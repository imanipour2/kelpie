#pragma once

#include <httplib.h>

#include <chrono>
#include <string>

// Owns one incomplete loopback request in the isolated child. HTTP 100 proves
// that a transport worker is reading its body, before route admission. This
// exercises activeRequests=0 with listenerFinished=false during shutdown.
class HeldHttpRequest {
 public:
  ~HeldHttpRequest() { Release(); }
  HeldHttpRequest() = default;
  HeldHttpRequest(const HeldHttpRequest&) = delete;
  HeldHttpRequest& operator=(const HeldHttpRequest&) = delete;

  bool Start(int port, const std::string& token) {
    socket_ = ::socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (socket_ == INVALID_SOCKET) return false;
    u_long nonblocking = 1;
    if (ioctlsocket(socket_, FIONBIO, &nonblocking) != 0) return false;
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_port = htons(static_cast<u_short>(port));
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (::connect(socket_, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0) {
      if (WSAGetLastError() != WSAEWOULDBLOCK) return false;
      fd_set writable;
      FD_ZERO(&writable); FD_SET(socket_, &writable);
      timeval timeout{2, 0};
      if (::select(0, nullptr, &writable, nullptr, &timeout) != 1) return false;
      int error = 0, length = sizeof(error);
      if (getsockopt(socket_, SOL_SOCKET, SO_ERROR, reinterpret_cast<char*>(&error), &length) != 0 || error != 0)
        return false;
    }
    nonblocking = 0;
    if (ioctlsocket(socket_, FIONBIO, &nonblocking) != 0) return false;
    const DWORD timeout = 2000;
    if (setsockopt(socket_, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout)) != 0 ||
        setsockopt(socket_, SOL_SOCKET, SO_SNDTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout)) != 0)
      return false;
    const std::string headers = "POST /v1/get-tabs HTTP/1.1\r\nHost: 127.0.0.1\r\n"
        "Content-Type: application/json\r\nContent-Length: 2\r\nExpect: 100-continue\r\n"
        "Authorization: Bearer " + token + "\r\n\r\n";
    std::size_t offset = 0;
    while (offset < headers.size()) {
      const int sent = ::send(socket_, headers.data() + offset, static_cast<int>(headers.size() - offset), 0);
      if (sent <= 0) return false;
      offset += sent;
    }
    std::string response;
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(2);
    while (response.find("\r\n\r\n") == std::string::npos && response.size() < 256 &&
           std::chrono::steady_clock::now() < deadline) {
      char data[256];
      const int received = ::recv(socket_, data, static_cast<int>(sizeof(data) - response.size()), 0);
      if (received <= 0) return false;
      response.append(data, received);
    }
    return response == "HTTP/1.1 100 Continue\r\n\r\n" && IsPending();
  }

  bool IsPending() const {
    if (socket_ == INVALID_SOCKET) return false;
    fd_set readable;
    FD_ZERO(&readable); FD_SET(socket_, &readable);
    timeval immediate{};
    return ::select(0, &readable, nullptr, nullptr, &immediate) == 0;
  }

  void Release() {
    if (socket_ == INVALID_SOCKET) return;
    ::shutdown(socket_, SD_BOTH);
    ::closesocket(socket_);
    socket_ = INVALID_SOCKET;
  }

 private:
  SOCKET socket_ = INVALID_SOCKET;
};

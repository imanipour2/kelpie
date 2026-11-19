#pragma once

#include <chrono>
#include <filesystem>
#include <fstream>
#include <string_view>

namespace kelpie::windows {

inline constexpr char kShutdownTraceFile[] = "shutdown-test-trace.log";

enum class ShutdownStage {
  kCloseRequested, kBeginDrain, kHttpDrain, kPreviewDrain, kPersist,
  kCookieSync, kConnection, kAccount, kLoginWindow, kPageAppearance,
  kChromium, kNativeControl, kReadiness, kCompleted,
};

// Explicitly enabled only by the isolated process fixture. This file contains
// fixed stage names and elapsed milliseconds; never request data or credentials.
class ShutdownTrace {
 public:
  void Enable(bool enabled) { enabled_ = enabled; }

  void Record(const std::filesystem::path& profile, ShutdownStage stage) {
    const auto label = Label(stage);
    if (!enabled_ || label == last_ || count_ >= 128) return;
    last_ = label;
    ++count_;
    try {
      std::ofstream output(profile / kShutdownTraceFile, std::ios::app);
      const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::steady_clock::now() - started_).count();
      output << elapsed << "ms " << label << '\n';
    } catch (...) {
      // Diagnostics never change the application's shutdown result.
    }
  }

 private:
  static std::string_view Label(ShutdownStage stage) {
    switch (stage) {
      case ShutdownStage::kCloseRequested: return "close-requested";
      case ShutdownStage::kBeginDrain: return "begin-drain";
      case ShutdownStage::kHttpDrain: return "waiting-http-drain";
      case ShutdownStage::kPreviewDrain: return "waiting-preview-drain";
      case ShutdownStage::kPersist: return "persist-state";
      case ShutdownStage::kCookieSync: return "stop-cookie-sync";
      case ShutdownStage::kConnection: return "drain-connection";
      case ShutdownStage::kAccount: return "drain-account";
      case ShutdownStage::kLoginWindow: return "close-login-window";
      case ShutdownStage::kPageAppearance: return "drain-page-appearance";
      case ShutdownStage::kChromium: return "stop-chromium";
      case ShutdownStage::kNativeControl: return "stop-native-control";
      case ShutdownStage::kReadiness: return "clear-readiness";
      case ShutdownStage::kCompleted: return "completed";
    }
    return "unknown-stage";
  }

  bool enabled_ = false;
  unsigned count_ = 0;
  std::string_view last_;
  std::chrono::steady_clock::time_point started_ = std::chrono::steady_clock::now();
};

}  // namespace kelpie::windows

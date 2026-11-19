#pragma once

#include <chrono>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <string_view>

namespace kelpie::windows {

inline constexpr char kShutdownTraceFile[] = "shutdown-test-trace.log";

enum class ShutdownStage {
  kCloseRequested, kBeginDrain, kHttpDrain, kPreviewDrain, kPersist,
  kCookieSync, kConnection, kAccount, kLoginWindow, kPageAppearance,
  kChromium, kNativeControl, kReadiness, kCompleted,
  kOwnerTick, kBeforeDirectPump, kAfterDirectPump, kAfterBrowserState, kClosePoll,
  kCefPumpDrain,
};

struct ShutdownProgress {
  std::uint64_t owner_ticks;
  std::uint64_t close_polls;
  unsigned direct_pump_depth;
  bool external_pump_active;
  std::size_t active_requests;
  bool accepting_requests;
  bool listener_thread_finished;
  std::string_view transport_version = "unknown";
};

// Explicitly enabled only by the isolated process fixture. This file contains
// fixed stage names and elapsed milliseconds; never request data or credentials.
class ShutdownTrace {
 public:
  void Enable(bool enabled) { enabled_ = enabled; }
  bool enabled() const { return enabled_; }

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

  void RecordProgress(const std::filesystem::path& profile, ShutdownStage stage,
                      const ShutdownProgress& progress) {
    const auto counter = stage == ShutdownStage::kClosePoll ? progress.close_polls : progress.owner_ticks;
    // The first few and then powers of two show whether the owner continues
    // polling without consuming the bounded trace on every 50ms retry.
    const bool sampled = counter <= 4 || (counter & (counter - 1)) == 0;
    if (!enabled_ || count_ >= 128 || (!sampled && stage != ShutdownStage::kCloseRequested)) return;
    ++count_;
    try {
      std::ofstream output(profile / kShutdownTraceFile, std::ios::app);
      const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::steady_clock::now() - started_).count();
      output << elapsed << "ms " << Label(stage)
             << " ticks=" << progress.owner_ticks << " polls=" << progress.close_polls
             << " directPumpDepth=" << progress.direct_pump_depth
             << " externalPump=" << progress.external_pump_active
             << " activeRequests=" << progress.active_requests
             << " accepting=" << progress.accepting_requests
             << " listenerFinished=" << progress.listener_thread_finished
             << " httpVersion=" << progress.transport_version << '\n';
    } catch (...) {}
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
      case ShutdownStage::kOwnerTick: return "owner-tick";
      case ShutdownStage::kBeforeDirectPump: return "before-direct-cef-pump";
      case ShutdownStage::kAfterDirectPump: return "after-direct-cef-pump";
      case ShutdownStage::kAfterBrowserState: return "after-browser-state";
      case ShutdownStage::kClosePoll: return "close-poll";
      case ShutdownStage::kCefPumpDrain: return "waiting-cef-pump";
    }
    return "unknown-stage";
  }

  bool enabled_ = false;
  unsigned count_ = 0;
  std::string_view last_;
  std::chrono::steady_clock::time_point started_ = std::chrono::steady_clock::now();
};

}  // namespace kelpie::windows

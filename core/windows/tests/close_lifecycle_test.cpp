#include "close_lifecycle.h"
#include "shutdown_trace.h"

#include <cassert>
#include <iostream>
#include <stdexcept>

namespace {

bool ReentrantCloseDoesNotRunTwice(bool during_persistence) {
  kelpie::windows::CloseLifecycle lifecycle;
  lifecycle.Request();
  int persisted = 0;
  int shutdowns = 0;
  bool nested_completed = false;
  auto nested = [&] {
    nested_completed = lifecycle.Advance(true, [&] { ++persisted; }, [&] {
      ++shutdowns;
      return true;
    });
  };
  const bool completed = lifecycle.Advance(true, [&] {
    ++persisted;
    if (during_persistence) nested();
  }, [&] {
    ++shutdowns;
    if (!during_persistence) nested();
    return true;
  });
  const bool passed = completed && !nested_completed && persisted == 1 && shutdowns == 1;
  std::cout << (passed ? "PASS" : "FAIL") << " nested close during "
            << (during_persistence ? "persistence" : "shutdown")
            << ": persisted=" << persisted << " shutdowns=" << shutdowns
            << " nested_completed=" << nested_completed << '\n';
  return passed;
}

void ExceptionsReleaseTheAttempt() {
  kelpie::windows::CloseLifecycle lifecycle;
  lifecycle.Request();
  int persisted = 0;
  try {
    lifecycle.Advance(true, [] { throw std::runtime_error("synthetic persistence failure"); },
                      [] { return true; });
    assert(false);
  } catch (const std::runtime_error&) {}
  assert(!lifecycle.persisted());
  try {
    lifecycle.Advance(true, [&] { ++persisted; },
                      []() -> bool { throw std::runtime_error("synthetic shutdown failure"); });
    assert(false);
  } catch (const std::runtime_error&) {}
  assert(lifecycle.persisted());
  assert(!lifecycle.completed());
  assert(lifecycle.Advance(true, [&] { ++persisted; }, [] { return true; }));
  assert(persisted == 1);
}

void TraceIsOptInAndBounded() {
  using kelpie::windows::ShutdownStage;
  kelpie::windows::ShutdownTrace trace;
  const auto profile = std::filesystem::temp_directory_path() /
      ("kelpie-shutdown-trace-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
  std::filesystem::create_directory(profile);
  const auto path = profile / kelpie::windows::kShutdownTraceFile;
  trace.Record(profile, ShutdownStage::kCloseRequested);
  assert(!std::filesystem::exists(path));
  trace.Enable(true);
  trace.Record(profile, ShutdownStage::kCloseRequested);
  trace.Record(profile, ShutdownStage::kCloseRequested);
  for (unsigned attempt = 0; attempt < 200; ++attempt)
    trace.Record(profile, attempt % 2 ? ShutdownStage::kHttpDrain : ShutdownStage::kPreviewDrain);
  std::ifstream stream(path);
  std::string line;
  unsigned count = 0;
  while (std::getline(stream, line)) {
    assert(line.find("ms ") != std::string::npos);
    if (count == 0) assert(line.ends_with(" close-requested"));
    else assert(line.ends_with(" waiting-http-drain") || line.ends_with(" waiting-preview-drain"));
    ++count;
  }
  assert(count == 128);
  stream.close();
  std::filesystem::remove_all(profile);
}

}  // namespace

int main() {
  kelpie::windows::CloseLifecycle lifecycle;
  int persisted = 0;
  lifecycle.Request();

  // The first close attempt must wait for handlers admitted before listener
  // shutdown. It cannot persist a state that an in-flight mutation can alter.
  assert(!lifecycle.Advance(false, [&] { ++persisted; }, [] { return true; }));
  assert(persisted == 0);

  // A bounded CEF close can be incomplete after persistence. Retrying must not
  // write a second snapshot, and the next successful close completes it.
  assert(!lifecycle.Advance(true, [&] { ++persisted; }, [] { return false; }));
  assert(persisted == 1);
  assert(lifecycle.persisted());
  assert(!lifecycle.completed());
  assert(lifecycle.Advance(true, [&] { ++persisted; }, [] { return true; }));
  assert(persisted == 1);
  assert(lifecycle.completed());
  const bool nested_persist = ReentrantCloseDoesNotRunTwice(true);
  const bool nested_shutdown = ReentrantCloseDoesNotRunTwice(false);
  ExceptionsReleaseTheAttempt();
  TraceIsOptInAndBounded();
  return nested_persist && nested_shutdown ? 0 : 1;
}

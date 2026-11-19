#pragma once

namespace kelpie::windows {

// Coordinates the two shutdown barriers without owning browser or HTTP objects:
// listener admission must drain before durable state is written, and CEF may
// require more than one bounded close attempt after that point.
class CloseLifecycle {
 public:
  void Request() { requested_ = true; }
  bool requested() const { return requested_; }
  bool completed() const { return completed_; }
  bool persisted() const { return persisted_; }

  template <typename Persist, typename Shutdown>
  bool Advance(bool admission_drained, Persist persist, Shutdown shutdown) {
    if (!requested_ || completed_ || !admission_drained || advancing_) return completed_;
    // CEF teardown can pump a nested native close notification. Keep that
    // notification from persisting or shutting the same runtime down again.
    // Release the attempt on every return and exception so an incomplete
    // bounded close can still retry from the outer owner loop.
    advancing_ = true;
    struct Attempt {
      bool& active;
      ~Attempt() { active = false; }
    } attempt{advancing_};
    if (!persisted_) {
      persist();
      persisted_ = true;
    }
    if (!shutdown()) return false;
    completed_ = true;
    return true;
  }

 private:
  bool requested_ = false;
  bool persisted_ = false;
  bool completed_ = false;
  bool advancing_ = false;
};

}  // namespace kelpie::windows

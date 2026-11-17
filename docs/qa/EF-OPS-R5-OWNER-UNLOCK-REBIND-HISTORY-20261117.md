# Kelpie R5 QA: owner-unlock WebView rebind and native history diagnostics

Date: 2026-11-17. Stacked draft atop PR46 -> PR45 -> PR44 -> PR42. Owner test device: Samsung SM-S928B / Android 17. Scope is the isolated R5 QA APK, **not** the public release or the ENERFOAM website.

## Verified physical reproduction and source diagnosis

The same native approval survived Android KEYCODE_SLEEP / genuine owner unlock: after status sync it was `ACTIVE_AGENT_SESSION` with advanced safetyEpoch 2 but `browserOperationsEnabled=false`; the Android keyguard was **gone**, screen awake, Kelpie MainActivity was the actual top RESUMED Activity with focused window. R5 native runtime continued `SUSPENDED_OR_TRANSFER / SUSPENDED / IDLE`; even sending `am start` to the already-top Activity did not recover it. Source inspection shows the R5 coordinator calls `ownership.lockDevice()` on screen-off and its active-state collector ignores an unlock transition when native generation is unchanged. `onResume()` likewise has no route for `renderer=IDLE` and `owner=SUSPENDED`. Android may keep the Activity resumed beneath the lock screen; an owner unlock does not imply another `onResume` callback.

**Fix candidate:** Recognize the existing *same-generation* active grant after lock/unlock (not a new grant) from the local native state flow; additionally receive Android protected `ACTION_USER_PRESENT` to refresh native lock state and handle `onResume/IDLE`. A security gate requires real Activity RESUMED and focused, visible decor, *same* attached and shown app-owned active WebView, unlocked Android keyguard and interactive display, notification STOP available, idle private display, original session generation and previous `SUSPENDED` owner state. Only then call `ownership.unlockNeedsReattach` and bind the exact native target, with immediate post-check of tab identity, consent state, lock and display before setting ready. If proof fails, remain suspended; if a rebinding race occurs, stop/fail closed. Never grant consent from an HTTP request, broadcast, remote action or ADB.

## Native BACK diagnostics — NOT a navigation policy bypass

On a harmless RTL fixture, clicking `#qa-next` correctly navigated to `next.html` and `READ_DOM` verified the page. Native R5 BACK returned `FAILED / ADAPTER_FAILURE`. Read-only CDP `Page.getNavigationHistory` for a test WebView reported two entries with currentIndex 1, but this does not prove the Java `view.canGoBack()` on the **owned** WebView was true. Add QA-only sanitized Android `KelpieR5History` categories when `view.canGoBack()/canGoForward()` rejects the operation, including only native history count/index (never URLs/title/content), and on unverified native history settlement. Do **not** silently navigate with JavaScript history or force `goBack()` when the native WebView reports no back item; examine exact device evidence first.

## Required test and approval gates

- Local Kotlin formatting + focused JVM gate tests (same grant, same generation, original suspended owner, exact visible WebView; deny after lock, stop, bad generation, hidden app, inaccessible notification), isolated signed Android APK build and SHA-pinned package preflight.
- Preserve exact currently installed PR46 QA APK and signer with `adb pull` before non-destructive `adb install -r`. Never clear owner or DualApp data. Read installed SHA on both profiles, verify native MCP security negative cases and real paired remote capabilities.
- Owner physically unlocks the real device after a real lock; observe session still approved but suspended, Android-native broadcast/flow causes authorized rebind and returns `READY / FOREGROUND / IDLE` without a second approval. Repeat with persistent keyguard (must remain denied), HOME/background transfer and STOP (must never rebind).
- Only on explicitly approved harmless RTL QA fixture test BACK once; inspect native history diagnostic categories and correlate with WebView DevTools read-only history. No speculative retry on ambiguous mutation outcomes.
- Full Android CI, real incoming call, Doze/24-hour recovery, multi-principal isolation, iOS parity decision, independent security review remain release-blocking. No merge/main/production website changes.

## Official platform references

- Android Developers [Intent.ACTION_USER_PRESENT](https://developer.android.com/reference/android/content/Intent#ACTION_USER_PRESENT): protected system broadcast emitted after the user's keyguard goes away; it is not an owner agent approval.
- Android Developers [context-registered receivers](https://developer.android.com/develop/background-work/background-tasks/broadcasts): flags and system broadcast sender restrictions.
- Android Developers [WebView.canGoBack and goBack](https://developer.android.com/reference/android/webkit/WebView): operate on the original WebView UI thread; a real back-history item is required.
- Project operational Issue #98: https://github.com/imanipour2/enerfoam-website/issues/98

#!/usr/bin/env bash
# Owner-approved freeze: protect installed code45 pairing/consent/connection surfaces.
# This is a source-only diff gate. It is NOT real-device, pixel-privacy or security acceptance.
set -euo pipefail

baseline="c7823444953db4e64999d45d4a95e53c17fc6d11"
git cat-file -e "${baseline}^{commit}" || {
  echo "FAIL: exact installed code45 commit is unavailable; refusing an ungrounded freeze check" >&2
  exit 2
}

# Keep this set small and precise: renderer and private-host source must remain editable.
# Changing these protected files requires a separate explicit owner decision and a
# deliberately revised guard rather than weakening this one during renderer fixes.
frozen=(
  apps/android/app/build.gradle.kts
  core/android/build.gradle.kts
  core/android/src/main/java/com/kelpie/browser/agent/session/AndroidAgentProcessSession.kt
  core/android/src/main/java/com/kelpie/browser/agent/session/SessionConsentCore.kt
  core/android/src/main/java/com/kelpie/browser/agent/session/AgentSessionNotification.kt
  core/android/src/main/java/com/kelpie/browser/agent/session/R5BackgroundOptInStore.kt
  core/android/src/main/java/com/kelpie/browser/network/R5RequestAuthorization.kt
  core/android/src/main/java/com/kelpie/browser/network/R5McpBootstrapGate.kt
  core/android/src/main/java/com/kelpie/browser/network/R5RemoteOperationPolicy.kt
  core/android/src/main/java/com/kelpie/browser/network/R5AgentControlPlaneRoutes.kt
  core/android/src/main/java/com/kelpie/browser/agent/execution/R5AgentAuthorization.kt
)
changed="$(git diff --name-only "$baseline" HEAD -- "${frozen[@]}")"
if [[ -n "$changed" ]]; then
  echo "FAIL: code45 owner-frozen connection/consent/build surface changed:" >&2
  printf '%s\n' "$changed" >&2
  echo "A new owner decision and separate security review are required." >&2
  exit 1
fi
echo "PASS: owner-frozen connection/consent/build files match installed code45."

# The legacy, single-tab private VirtualDisplay architecture and native R5 controls
# must not be silently removed. These are source checks, not proof of a live frame.
display=core/android/src/main/java/com/kelpie/browser/capture/PrivateWebViewDisplay.kt
native=core/android/src/main/java/com/kelpie/browser/agent/execution/R5NativeBrowserPort.kt
privacy=core/android/src/main/java/com/kelpie/browser/capture/WebViewCapturePrivacy.kt
for file in "$display" "$native" "$privacy"; do
  test -s "$file" || { echo "FAIL: required component missing: $file" >&2; exit 1; }
done
grep -Fq 'manager.createVirtualDisplay(' "$display" || { echo 'FAIL: native VirtualDisplay path missing' >&2; exit 1; }
grep -Fq 'ImageReader.newInstance(' "$display" || { echo 'FAIL: private ImageReader path missing' >&2; exit 1; }
grep -Fq 'val host = Presentation(' "$display" || { echo 'FAIL: private Presentation path missing' >&2; exit 1; }
grep -Fq 'view.loadUrl(requested)' "$native" || { echo 'FAIL: WebView navigate primitive missing' >&2; exit 1; }
grep -Fq 'Operation.SCROLL -> {' "$native" || { echo 'FAIL: WebView scroll primitive missing' >&2; exit 1; }
grep -Fq 'requireSyntheticScreenshotFixture' "$privacy" || {
  echo 'FAIL: existing privacy fail-closed gate removed without independently reviewed replacement' >&2
  exit 1
}
echo "PASS: private display, WebView actions and existing screenshot refusal remain present."
echo "NOTE: PASS here does not assert HOME render, screenshot safety, phone installation or main merge."

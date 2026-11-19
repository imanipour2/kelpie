# R5 Mother MCP: Private-tab ownership and viewport hardening

Date: 2026-11-19. Status: OWNER-DIRECTED DEVELOPMENT / REVIEW-GATED. This branch is **stacked on PR #70** at `fc25806dd9940ef13fc7a3b06b77c96a3977b6c9` (PR #66 + KAI integration), **not main**. Existing installed S24 app `0.2.20-r5-mother-viewport-sidecar-qa`, port 18463, owner tunnel and consent remain untouched.

## G0 — Evidence and authority before changes

- Owner live test: Alibaba `/domestic` loaded; `tabs.select` reported success, selected tab showed `window.innerWidth/innerHeight=0`, `tab.click` UNSUPPORTED, fill/screenshot/scroll failed; another popup/tab showed 384×682 and screenshot PNG succeeded. Human Alibaba screenshots confirm flight inventory; page-not-found inference was incorrect. Source report: [Issue #98](https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6999896880).
- Root-cause hypothesis at source boundary: `MobileTabHandler.switchTab` calls `TabStore.selectTab` without native rehost; R5 `R5AgentTabCommands` uses `PrivateWebViewDisplay.switchPrivateWebView` + identity proof *before* `TabStore.selectTabAsAgent`. Mother `R5MotherMcpCaptureAdapter` requires matching `currentTabId`; selecting without moving can leave an inactive 0×0 WebView selected. Exact command-to-handler route on the installed S24 still needs native trace.
- Baseline PR #70 exact GitHub Actions: Android and R5 QA jobs FAIL with one Kotlin JVM regression `R5WebViewActionScriptsTest.snapshotProvidesBoundedUniqueCssFallbackWhenIdIsMissing` at line 40 (overly broad assertion `!snapshot.contains("e.value")` matches sanitizer source), 419 tests/1 failed; lint, iOS/macOS/Windows and Linux pass. This is **pre-existing to this branch**, not new viewport-fix evidence.
- Sources read: repo `AGENTS.md`, `CLAUDE.md`, `core/README.md`, `docs/policies/development-and-release.md`, KAI-001 Drive draft, R5 source and G0 issue. The repo requires Android/iOS parity or independent exception before main merge; reviewed main merge never implies install or release.
- Selected scope: reuse existing authenticated mother MCP, same owner/native consent, R5 transfer mutex, native renderer, and shared KAI. No new port, privileged screen capture, device-wide ADB control, token handling, APK installation, or production release.

## Planned change / threat boundaries

1. Route mother `switch-tab` via a native-authorized R5 rehost callback while private; mutate active tab only after ownership and target identity verify; refuse mother new/close/delete tab while private instead of silently changing/destroying the active renderer.
2. Wait for native View measured dimensions AND positive CSS viewport after attaching new private WebView; rollback swap if not ready. No fallback to cached/blank image; request-level existing nonce/frame proof remains authoritative.
3. Fix existing false-positive KAI source test; add pure tests for foreground/private switch gate and fail-closed mutation; add browser fixture/QA matrix. No raw site DOM or secrets in logs.
4. Report exact CI and native S24 QA independently. No claim of hardware PASS until running new APK on the S24 and reproducing Alibaba/STOP/lock/HOME tests.

## Acceptance matrix and rollback

- **T01** Foreground `switch-tab` preserves standard tab behavior.
- **T02** Private switch with missing owner/principal/hook fails without active tab mutation.
- **T03** Authorized private switch rehosts same app-owned WebView, then selects tab and verifies ownership/epoch.
- **T04** Offscreen target zero native/CSS viewport fails and restores former tab/renderer.
- **T05** Private new/close/delete cannot strand/destroy current R5 WebView.
- **T06** 20 tab/modal switches on actual S24 (not source-level simulation).
- **T07** 25 screenshot cycles and new non-gray frame proof; no stale/wrong-tab/foreign-app pixels.
- **T08** Alibaba origin/destination/date, sorting and bottom-to-top list coverage, real postcondition.
- **T09** STOP/lock/session expiry while switching; no action/no frame after revocation.
- **T10** HOME screen-off/resume, app stays background and tunnel survives without auto-consent.
- **T11** KAI source/security tests and exact Android CI.
- **T12** iOS parity or separately approved exception, human owner acceptance and staged signed QA APK/rollback before any main merge/public release.

Rollback: retain existing S24 install and untouched tunnel; revert this child branch/PR if tests regress. This artifact is not authority to deploy.

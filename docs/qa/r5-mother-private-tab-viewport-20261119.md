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


## 2026-11-19 — S24 Termux startup SESSION_REVOKED root-cause confirmation (review-gated)

**Owner's exact phone diagnostic:** Under existing `proot-distro login ubuntu`, `ef-kelpie-mcp-readonly-20261119.py` returned:
- YAML regular and discovery Authorization are both present, identical, and equal to the new owner Authorization file.
- Existing and new owner Authorization files have equal content. The existing runit launcher still references the old filename; **this is not itself the cause** because the two files match.
- Local `POST http://127.0.0.1:18463/mcp` `initialize` with each of the three Authorization sources consistently returns **HTTP 403 SESSION_REVOKED**.
- When run outside PRoot in bare Termux, config missing/not-available is expected and does **not** contradict the Ubuntu test.
- Additional local ADB read-only comparison in this review session produced the same `HTTP 403 SESSION_REVOKED` for an anonymous and a deliberately invalid bearer `initialize`, so presence of an Authorization field alone does not prove the middleware resolved a **non-localhost verified principal**. A separate browser Origin test returned `FORBIDDEN` as expected.

**Source-proven location:** Exact installed S24 APK SHA256 `3ea605c53f6e696e465256880e602a199f01530d5121f0ad5444381d7afdaf34` matches recorded PR #66 build `68ade958af66b7f6de219abd2d9f9011478f5a40`. Its `HTTPServer.handleAuthenticatedPost` unconditionally returns `403 SESSION_REVOKED` when the private agent is active and owner grant is unavailable, even for `initialize`. Draft PR #71 introduces `R5McpBootstrapGate` to admit **only protocol bootstrap metadata** for a verified non-localhost principal independently of the owner-private WebView session. `tools/call` and all browser data/actions still require owner consent.

**Test hardening:** This branch now routes the post-authorization delivery check through `requiresNativeGrantAtDelivery` and tests the exact combination of private ingress/egress, paired client, localhost/unauthenticated callers, initialize/tools-list and tools-call. Source tests do not prove the existing tunnel static Bearer resolves to a paired **non-localhost** client. That remains the last key admission question before expecting a post-update `/readyz HTTP200`.

**Installation blocker:** The installed APK signer SHA256 `234c761b48afa2955787dbad8dbabd3fd9f667ef3ff35cd9ee7ed127246fe287` differs from PR #71 ephemeral CI signer. Do not attempt direct `adb install -r`, substitute another package identity, auto-uninstall, wipe user data, rotate secrets, or change fixed port 18463. Existing encrypted Windows private hot snapshot was integrity-checked, including six SQLite quick-check passes, but **cold consistent restore has not been validated**. The phone remains unchanged.

**Current state:** Source/QA fix in draft PR only, hardware `READINESS_503` unresolved, owner session may return `FORBIDDEN` when Termux is foreground, main not merged, beta not accepted. Publish/install gates remain pending.

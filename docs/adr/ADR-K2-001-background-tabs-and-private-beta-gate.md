# ADR-K2-001 — Android private R5 tab ownership and beta gate

Date: 2026-11-17 | Status: DRAFT / DESIGN ACCEPTED FOR QA, IMPLEMENTATION NOT COMPLETE

## Verified starting point

The owner reports a successful personal Google sign-in during the previous hardware run; this is first-hand user evidence, not an independent OAuth callback/cancellation/security test. The pinned installed Android QA source remains PR #50 (`ecac5d7f`), not `main`. Actual UI tests on Samsung SM-S928B / Android 17 verified three tabs, same-tab Back/Forward, real link click, scroll, reload, close, and process-restart tab restoration. The locally paired R5 MCP has three tools, but the installed ChatGPT connector exposes only the read-oriented browser-control tool and the native bridge has no create/close/select tab methods. Private beta must not be presented as public production.

## Why Browser Use behavior is not a direct transplant

Browser Use operates Chrome/Chromium CDP browser targets and explicitly distinguishes attachment/selection of a target from activation of a *visible* tab. Its browser session and tool implementations are references for separate tab identities, idempotent navigation, checkpoints and background control. Android WebView has no browser-wide CDP tab manager with those semantics: the app itself owns each WebView, an Activity lifecycle and (for R5) one private VirtualDisplay. Do not launch the Activity or use full-screen MediaProjection/Accessibility to move between app-owned tabs. Sources: https://github.com/browser-use/browser-use/blob/main/skills/browser-use/SKILL.md ; https://github.com/browser-use/browser-use/blob/main/browser_use/browser/session.py ; https://developer.android.com/develop/ui/views/layout/webapps/webview .

## Required invariant and proposed native API

1. Owner-consented native process session and paired principal are verified for **every** call, using a fresh consentGeneration, observed tabId/documentEpoch, unique commandId and a monotonic clientSequence. STOP, lock, process death or a different principal must invalidate in-flight actions and readback. No persistent auto-grant.
2. Split TabStore's person-oriented add/select/close methods from agent-oriented background methods. `new` must not select the tab automatically or launch an Activity. `close` must check the current tab's identity, not a stale index; closing the final/controlled tab needs an explicit safe successor and renderer handoff.
3. Preserve one WebView owner at a time. A background `select` requires a single serialized handoff of the actual WebView from the current private Presentation to the target WebView, with a **new** renderer identity/epoch, no old-frame reuse and postcondition readback from the same tab. If a handoff cannot be independently verified, **fail closed** rather than foreground Kelpie. Do not silently swap renderer pointers.
4. Add `tabs.new`, `tabs.close`, `tabs.select` to native write-tool discovery only after instrumented tests; avoid granting write privileges through a tool advertised as read-only. Reconcile installed ChatGPT connector tool registry separately. `tabs.list` remains read-only and never switches tabs.
5. Ordinary click/type/scroll/navigate must stay in Kelpie background under the active native grant; sensitive human-authenticated login, card payment, OTP and irreversibly consequential acts require local handoff/approval. A handoff is explicit and resumes only after authentic owner confirmation. Google OAuth in supported external Custom Tabs does not grant cookie-jar transfer to arbitrary WebView sites.

## Acceptance before beta

- CI for exact commit: lint, Android, native/unit tests, macOS/parity decision and artifact provenance.
- Real owner-device: open tab 2, navigate, click, back, forward, open tab 3, close tab 2, return to tab 1 **without bringing Kelpie foreground** while ChatGPT is visible; 25 loops; observe fresh page/renderer epoch after each switch and reject stale tab actions.
- Native STOP mid-transfer, lock/screen-off, incoming call, Doze/battery-saver, reboot and tunnel reconnection; no cross-app screenshot; 48dp tap targets and TalkBack; no credentials sent to agents.
- Close independent KA-P0-01..08 security findings or owner-signed *narrow* risk exception after review. Strict iOS/Android parity gate is still open. Debug QA builds are not production beta releases.

## UX micro-fix in this PR

Address editing now shows a 48dp accessible `Clear address` button only when there is editable text. This changes the buffer only, preserves keyboard focus, and does not navigate. While not editing, Reload/Stop remains visible. Back/Forward stay grouped and address takes remaining horizontal space, with 48dp touch controls. No agent or browser-session privileges are changed by this UX patch.

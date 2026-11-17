# ADR-R5-004 — Owner-consented background tabs and private beta

**Status:** DRAFT / ANDROID QA ONLY / MAIN MERGE BLOCKED / PUBLIC_BETA_NO_GO
**Date:** 2026-11-17. **Authority:** [CDG](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit) and [Issue #98](https://github.com/imanipour2/enerfoam-website/issues/98). **Base:** draft PR50 exact head `ecac5d7f06fd954c4a0b8b804f8fb76185ee97d0`.

## Problem and decision

Physical S24 QA proved manual tab creation/closure, scroll, click, same-tab Back/Forward and persistence. Native R5 MCP omitted lifecycle methods; blindly calling `TabStore.selectTab()` while another WebView is owned by the private Presentation would cause mismatched renderer identity, and `startActivity()` would steal focus from ChatGPT. Neither is allowed.

- Add focused nonempty URL editor clear ×, 48dp touch targets, and grouped Back/Forward; clearing must not navigate or erase the real page.
- `tabs.new` creates a real app-owned **unselected** background tab; count capped at 40; no Activity launch.
- `tabs.close` destroys an **inactive and unattached** tab only. The agent must select another tab before closing the active one.
- `tabs.select` swaps the exact target WebView within the existing private VirtualDisplay Presentation; native freshness markers stay outside the page; no MediaProjection, Accessibility, cross-app windows, or foreground Activity.
- Mutations share one R5 command mutex, consent-generation, verified paired principal, monotonic sequence, unique command IDs and fresh tab/document identity; independently verify tab counts, active ID, and renderer ownership afterward. STOP, lock and process death revoke the grant.
- The installed `kelpie_browser_control` facade also accepts owner-consented ordinary tab/action methods without granting remote arbitrary JS or filesystem access; separate `kelpie_browser_actions` and `kelpie_agent_session` remain native MCP tools. Explicit payment, publication, irreversible deletion and credential export need a separate approval.
- Build an isolated **owner-only private QA beta** `com.kelpie.browser.r5betaverify` on port 18461, v`0.2.14-r5-private-beta-qa`, rather than wiping the Google-signed-in `r5fullverify` QA on port 18460. Debug-only provenance and owner hardware acceptance, not a Play/public release.

## Research and limits

The [Browser Use documentation](https://docs.browser-use.com/) and [open-source implementation](https://github.com/browser-use/browser-use) inform tab-targeting without OS-task activation. Android [background-start rules](https://developer.android.com/guide/components/activities/background-starts) forbid treating `startActivity` as a safe automation mechanism. [WebView](https://developer.android.com/reference/android/webkit/WebView) and [Compose text fields](https://developer.android.com/develop/ui/compose/components/text-fields) supply the relevant host/UI constraints. These references guide implementation, but do not replace real-device QA. Owner-reported Google login success is evidence for that specific account flow, not a guarantee for third-party OAuth/cookie transfer.

## Mandatory QA and open gates

1. Exact-source Android build, ktlint, unit tests, Lint, signed debug APK provenance and installer integrity green.
2. Native user taps Allow; background `tabs.new(example.org)` does not bring Kelpie foreground; `tabs.select` rebinds same private presentation; screenshot/DOM/click/scroll work on target, switch-back preserves previous WebView, inactive `tabs.close` leaves other tabs.
3. Reject missing/stale grant, wrong principal, replayed IDs/sequences, stale document epoch, unknown/active tab close, lock and STOP during swap without leaking image/DOM. No screenshot of Android screen or other apps.
4. Known same-tab Back/Forward, `_blank` separation, process restart, lock/unlock, calls, Doze, network handoff, reboot and Termux recovery.
5. Independent closure of 8 P0 audit findings and associated P1, Android/iOS strict parity or separately approved scoped exception. **No public beta or main merge before these gates.**

**Unresolved:** 8 P0 security, published provider schema parity, arbitrary-site Google OAuth, semantics of sensitive button clicks, full hardware endurance. GitHub PR merge never automatically authorizes production, website or device migration.

# R5 Personal Background Browser: Device QA and Safe Operations

Date: 2026-11-17
Status: PRIVATE QA ONLY — not upstream, not a production release, and not a website publication authorization.
Source baseline: R5 draft PR #33, commit 553a7bc152c50a208b53d80b3b0b15cc1d8d0a2b
Scope: Android 17, Samsung Galaxy S24 Ultra test device; source-only nonce-epoch hardening stacked on R5.
Owner approval reference: ENERFOAM CDG, EF-OPS-KELPIE-R5-TRIAL-20261116.
Evidence register: https://github.com/imanipour2/enerfoam-website/issues/98

## Proven on the installed R5 baseline

The owner physically approved one process-scoped agent session in the native Android dialog. The authenticated local R5 API reported ACTIVE_AGENT_SESSION and READY. With ChatGPT foreground and Kelpie WebView in its app-owned private background display, the phone executed native READ_DOM, FILL, SCROLL, CLICK and NAVIGATE with independent target readback. Native PNG screenshot AUTO (720x1280 with exact source/output scale), HIGH (1080x1920) and legacy screenshot all returned VERIFIED_EFFECT. Re-capture on both public enerfoam.com and test.enerfoam.com was successful; the staging hostname returned a default WordPress welcome page, NOT evidence of a valid mirrored staging environment.

Forty sequential screenshots from public enerfoam.com: 40/40 executed, 40 distinct SHA-256 frame digests, median local response approximately 266 ms. Process PSS before/after approximately 208,969/209,294 KiB, without obvious short-run linear growth. No PNG was intentionally persisted to disk during this run. A 40-frame metadata-only JSON log exists on the QA laptop. This is not a 24-hour leak or thermal/power qualification.

Negative checks: wrong consent generation denies with HTTP403; malformed screenshot option rejects with HTTP400; payment, publish, and file:/// navigation reject; no website content or account state was changed.

## This PR's proposed native freshness extension

The installed baseline explicitly has MAX_FRAMES=512 per private display, unrelated to RAM capacity. The new source extends that to a bounded 512-frame nonce epoch, with fresh SecureRandom seed pair for two independent 24-bit app-owned native paint markers and a monotonic native callback ID. It does not persist or retain screenshots. The old document/WebView/session remains identical across epoch transitions. For every ImageReader frame, both markers must match the expected pair; old frames, canceled requests and STOP retain existing fail-closed image ownership.

New pure-JVM tests cover frame 1, frame 512, frame 513 rollover, 2,000 distinct expected token pairs, bounded seed history through 90 epochs, repeated entropy pairs and invalid random input. 2,000 tokens in JVM are not equivalent to 2,000 screenshots on Android; no real-device claim until an instrumented stress run succeeds.

The secondary marker adds a small 24x24 compositor-colored square below the original marker in the top-left corner of the delivered screenshot. Neither marker is written into the visited page's DOM or into any other app. UI/legibility checks should account for this region.

## Personal operating procedure

1. Confirm owner has unlocked Android, POST_NOTIFICATIONS granted, and only the intended QA package is installed on User 0. Never grant notifications, pairing, unlock, or approval via ADB.
2. Reconnect USB and run the fail-closed scripts/qa/r5-personal-readiness.py using explicitly selected ADB and serial. --connect restores only tcp:18460 forward and tcp:18080 reverse. --start-app is an optional package-scoped activity launch; there are no global ADB input commands.
3. If the native R5 session is OFF, issue agent-session-request and have the owner verify and physically approve the displayed client. Never tap approval through ADB or remotely.
4. Query authenticated agent-capture-capabilities and agent-tabs; use exact tab ID/document epoch and a fresh clientSequence/commandId for each action. Never replay UNCERTAIN mutating actions.
5. For read-only website QA, first READ_DOM, then SCREENSHOT auto; explicitly request a newly rendered HIGH frame for unreadable fine text. Each screenshot is decoded briefly and discarded. Record only metadata/aggregate metrics, never cookies, private DOM, tokens, screenshots, OTP or credentials.
6. Test navigation and basic interaction only on an offline local fixture or authorized staging content. Public ENERFOAM website work is read-only until G0 backup/restore, staging mapping, rollback, RTL/WCAG/Woo flow and G1–G4 gates are independently evidenced.
7. STOP from trusted notification or authenticated agent-session-stop; confirm status OFF and command denial. Lock and process death also revoke. Do not expose the local QA port on a public interface.
8. To update an APK, first STOP/verify OFF, independently check pinned source SHA, APK package identity, debug signature, supported ABIs, 16 KiB ELF alignment, and Android version; then update same package with ADB. Never uninstall official upstream com.kelpie.browser. After update repeat native consent.
9. Direct ChatGPT plugin use without USB requires a healthy authenticated Termux outbound tunnel (local ADB relay alone is insufficient). Currently remote plugin returns Session terminated and tunnel-client is not observed. Do not bypass Termux sandbox or install broad Accessibility services. Repair requires owner-mediated inspection of existing runit service and authenticated narrow route.
10. Do not claim public release, default browser role, Apple parity, or automatic Android unlock. This feature is Android QA scoped and must remain a Draft PR without iOS parity and independent security/release approval.

## Acceptance matrix

- [x] Existing baseline native S24 background READ_DOM, SCROLL, FILL, CLICK, NAVIGATE and screenshot
- [x] AI quality negotiation and native fresh HIGH capture on public and test ENERFOAM websites
- [x] 40 consecutive distinct PNG proof results and short memory sampling
- [x] Security negative cases: malformed option, wrong consent, file URL, high-impact operation
- [x] Local readiness script on USB-connected S24; notification granted and single QA package
- [ ] Native 2-marker epoch 512/513/1000/2000 frames on physical S24
- [ ] Native STOP notification tap and mid-encode cancellation
- [ ] Lock/Doze/slow consumer/network handoff/24-hour stress with process telemetry
- [ ] Android native build/signature/package/provenance on this new branch
- [ ] Valid trusted staging WordPress backup/restore and personal UX read/write tests
- [ ] Direct outbound tunnel/MCP without USB
- [ ] iOS parity or owner-approved scoped exception, independent code/security review
- [ ] Full GitHub CI and authorized release gate

References: Android ImageReader https://developer.android.com/reference/android/media/ImageReader ;
Android WebView https://developer.android.com/reference/android/webkit/WebView ;
Android memory https://developer.android.com/topic/performance/memory ;
Android 16KB page sizes https://developer.android.com/guide/practices/page-sizes ;
MCP tools specification https://modelcontextprotocol.io/specification/2025-06-18/server/tools ;
ENERFOAM approved document cycle https://docs.google.com/document/d/1ZwBQdFdMwYu6jOAcIZ32oVIedzy4W5BGaZEz91XbEi4/edit.

GATES: PRIVATE_ANDROID_QA / NO_GITHUB_MAIN_MERGE / NO_SITE_WRITE / RELEASE_NO_GO / SYNC_BLOCKED_MAIN.

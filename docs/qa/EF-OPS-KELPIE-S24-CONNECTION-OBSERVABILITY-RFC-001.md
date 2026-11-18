# EF-OPS-KELPIE-S24-CONNECTION-OBSERVABILITY-RFC-001
Status: **ENGINEERING PROPOSAL / NOT MERGED / NO SITE DEPLOYMENT / SYNC_BLOCKED**. Date: 2026-11-18. Owner directed capture and design, not consent bypass or release approval.

## References
Canonical incident and decision review: https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit
Implementation baseline: PR https://github.com/imanipour2/kelpie/pull/66, exact commit 68ade958af66b7f6de219abd2d9f9011478f5a40
Historical operational record: https://github.com/imanipour2/enerfoam-website/issues/98
Original native source: core/android/build.gradle.kts; network/{HTTPServer,AuthMiddleware,TunnelReadinessProbe}.kt; agent/session/{R5ObservedAgentClients,AgentSessionCoordinator}.kt; native core-state/peer_approval.cpp.
External references: OpenAI tunnel-client cmd/client/run_command.go, pkg/runtimehealth and pkg/mcpclient/internal/static_headers.go; Termux/PRoot-Distro/Android background-service references reviewed in incident RCA.

## Verified device facts
* Samsung SM-S928B, Android 17; current isolated APK com.kelpie.browser.r5mothernextverify v0.2.20/code40 built for local HTTP **18463**, NOT 18422 or 18423.
* Native /health earlier HTTP200. Owner bridge public key matched Trusted Computers entry; /v1/pair signed proof returned PAIR_PROOF_ACCEPTED, EXISTING_COMPUTER_KEY_TRUST_REUSED and PAIR_APPROVED. No plaintext password or blind consent was required.
* Termux Ubuntu /root/.local/share/kelpie-r5-owner/authorization-header.txt and /root/ef-tunnel/kelpie-auth.header initially differed. Verified backup and sync emitted RUNIT_REFERENCE=PASS, PREFLIGHT=PASS, BACKUP_CREATED=YES, HEADER_SYNC=PASS; config target = 127.0.0.1:18463/mcp, runit injects Authorization file into MCP + discovery.
* Before sync restart, tunnel /readyz=503 because initialize was Forbidden; target replied HTTP403 with SESSION_REVOKED when native private owner session was not active; foreground generic tools occasionally passed. Valid signer, trusted computer, bearer use, owner consent, local MCP and remote tools are DISTINCT GATES.
* After sv force-restart, runit status run PID 31422 for >352s, but 'ps -ef | grep tunnel-client' showed no executable and localhost:8080 /healthz and /readyz returned curl (7) HTTP000. ChatGPT connector returned Session terminated. Error-filtered log empty. Actual installed run script and binary have NOT been read directly, and post-restart child PIDs/stages remain UNVERIFIED.
* Three Termux:Boot entries exist: 10-kelpie, 20-enerfoam-s24, 30-ef-s24-autocare. Older packages/ports remain present; deleting them is NOT authorized.

## Root-cause tree (hypotheses requiring measured evidence)
P0: runit-managed launcher stuck before starting tunnel-client; candidate bottlenecks: proot-distro bootstrap, working directory or protected file check, find app lookup, external exec/fork, or supervisor lifecycle handling. Active runit PID does not prove the tunnel executable runs.
P1 AFTER CLIENT STARTS: wrong bearer application or incoming Authorization override, generic localhost fallback, native owner consent generation, private WebView lifecycle and initialize contract; do not fix P1 by bypassing trust. The codebase includes separate localhost and verified Ed25519 identities and native consent gating.

## Required architecture: connection diagnostic plane
Components with independently observed transitions:
BOOT -> RUNSVDIR (single instance) -> RUN SCRIPT -> PROOT LOGIN -> CONFIG PRESENCE -> BINARY LOCATION -> BINARY EXEC -> 8080 HEALTH -> 8080 READY -> MCP INITIALIZE -> VERIFIED NON-LOCALHOST PRINCIPAL -> OWNER-NATIVE CONSENT -> R5 PRIVATE WEBVIEW -> REMOTE TABS/DOM/SCREENSHOT -> BACKGROUND/STOP/LOCK -> REBOOT-RECOVERY.

Event schema v1 (small, append-only, versioned):
- schema_version; UTC event time + monotonic elapsed; ephemeral boot_id; supervisor generation; process generation; request correlation id (if available); app build SHA/package; component/stage; outcome UNKNOWN|PENDING|PASS|FAIL|BLOCKED|REVOKED|EXPIRED; enumerated reason_code; measured duration; PID/PPID (locally only); listener port; evidence source; last_observed_at.
- Never log tokens, bearer headers, private keys, account identifiers, full URLs, cookies, page content, screenshots, command-line args, raw environment, device serial, or owner identity. Keep only hashed ephemeral principal references and verified/nonlocal boolean when permitted. The event log NEVER grants authorization.
- Every displayed PASS carries timestamp/age; expired proof becomes UNKNOWN; never infer remote control from HTTP200 /health.
- Explicit gate ledger states for (A) Ed25519 key trust (B) actual Bearer recognized (C) MCP ready (D) owner session grant (E) fresh-render evidence (F) remote action test; separate native owner-approval and revoke timestamps/generation, with no automatic approval.
- Native Connection Status: compact layer cards, source, reason code and next safe step; owner-triggered Check again; local read-only redacted export; consent buttons separate and explicit; alert if supervisor running but listener missing.

Supervisor hardening (after actual script readback):
1. Instrument each stage with START/FINISH/FAIL events before a potentially blocking command; bounded startup timeout and process-alive check, controlled backoff/crash-loop breaker, unique supervisor lock (one service directory).
2. Track child process PID and exit code, TERM/CHLD graceful cleanup; use exec only if proven compatible with PRoot process tree and Termux service-daemon; do not change binary ABI or launcher blindly.
3. Local bounded rotating log suggestion <=5 x 1MiB files or 7d, mode0600, no remote automatic export; manual owner-export redact at producer AND exporter.
4. Durable boot and agent session records must be distinct: persistent preference must never persist approval bearer, process consent, STOP unblock, or active private WebView ownership.

QA gates:
- Unit/fixture tests: blocked proot, stale PID, missing config, find returns empty, executable not runnable, exit before health, listener binding conflict, redaction, native consent/revoke/STOP, 403/503/000 distinction.
- Integration: real S24 runit + tunnel-client process tree and 8080 HTTP; mcp initialize; verified principal reaches R5; owner approval; remote tabs/snapshot/click/screenshot fresh pixels in HOME; 25 cycles, lock/unlock, WiFi/5G, reboot first unlock; restore rollback.
- CI and signed APK provenance on exact commit; no main merge or publicly deployed beta absent owner approval and explicit gates. Website LIVE/STAGING not touched.

## Next single read-only device diagnostic
Read current 'sv status'; derive actual run PID; inspect /proc/PID/status and /proc/PID/task/PID/children (recursively by numeric PID); do not display cmdline or environment. Independently bounded probe: proot-distro bash -> cd -> file-presence -> find executable -> executable --version. Record first non-completing step with timeout and exact exit code; DO NOT issue repeated sv restart before these results.

Governance: proposed implementation only. Drive is canonical record; GitHub branch/PR holds technical design/QA, main remains untouched; proof of GitHub merge is not permission to install a beta or change WordPress. SYNC_BLOCKED until review+readback.
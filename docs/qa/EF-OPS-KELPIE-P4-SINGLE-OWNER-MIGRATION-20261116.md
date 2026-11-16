# EF-OPS-KELPIE-P4-SINGLE-OWNER-MIGRATION — proposed cutover and rollback

**Status:** PROPOSED / NO DEVICE CUTOVER / NO PACKAGE REMOVAL / NO RELEASE.  
**Source snapshot:** 2026-11-16; actual S24 Termux and Ubuntu configuration NOT presently accessible to this session.  
**Owner request:** converge the S24 onto one clean P4 browser, one tunnel, one reviewed auto-start/recovery owner and retire obsolete apps only after acceptance.  
**Canonical authorities:** [CDG](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit); [EF-OPS-TOOLS-001](https://docs.google.com/document/d/1DIhXXti6Md9MHMSmofsUFoLUajj-ISOB2vIANbIE7QM/edit); [issue #98](https://github.com/imanipour2/enerfoam-website/issues/98).

## Current independently verified facts (not all simultaneous)

- Live ChatGPT Kelpie MCP last reached `com.kelpie.browser.unifiedverify` 0.2.6-qa build26 on **18440**, SM-S928B Android17. Browser DOM/click/fill/scroll worked. Attempts to screenshot while background returned `FOREGROUND_REQUIRED` with `USER_TAP_REQUIRED`; **this app is not the P4 build**.
- An earlier on-phone probe gave P4 `18437/health = HTTP 200` while 18440 was temporarily unavailable. Actual active runit `run` script then still contained **18440** rather than **18437**; `proot` and `tunnel-client` were absent during that failed boot. Other phone sessions recovered the 18440 MCP route. Historical checks are not proof of a stable target.
- [Draft PR #23](https://github.com/imanipour2/kelpie/pull/23) builds `com.kelpie.browser.pcparallelverify` 0.2.10-parallel-qa code30 on **18447**. Its **six exact-head CI checks succeeded**. Debug-only artifact SHA256 `ffa576060c134e5bbefa718ccfadf6cb3104d1a6be0277763b25a24c59af3e3a` was verified against generated provenance. This is **P4 capture-only**, not the approved end-state one-consent Agent Runtime.
- The already tested `com.kelpie.browser.pcverify` 0.2.9-qa code29 uses 18437; the unpublished R5 Windows worktree also uses this package identity and must not overwrite it. Local latest R5 HEAD `0a49477b0bd584b7ae17474d0e0abf3913187f19` remains unpushed.
- Official device runbook records `~/.termux/boot/10-kelpie`, `20-enerfoam-s24`, `30-ef-s24-autocare`, `~/.local/bin/ef-kelpie-autostart`, `~/.bashrc`, runit service `$PREFIX/var/service/enerfoam-s24-tunnel/run` and PRIVATE Ubuntu config `/root/ef-tunnel/phone-direct.yaml`. None is a new source of truth; read the live device.
- Direct ENERFOAM S24 Kelpie MCP connector at this cutover planning check returned an internal error. Separate Windows Browser Use tunnel remained `Tunnel-client has not been seen for 300 seconds`; physical USB alone does not prove ADB control.

## Target model

**Exactly one authoritative QA/test browser** chosen only *after* code/approval/compatibility gates; one single tunnel-client on local 8080 managed by runit, one boot/dependency chain, one recoverable configuration. The old browser is kept until the new route passes acceptance. Port routing on the phone must match the verified new package and any protected `mcp` discovery/header settings. Browser-app and Windows browser-control tunnels are distinct: fixing one does not repair the other.

**Recommended staged P4 route:** `pcparallelverify` / 18447 as a temporary isolated cutover candidate, preserving `unifiedverify` / 18440 and `pcverify` / 18437 untouched. The true final package should be decided after bringing forward the latest local R5 source and approving any Android/iOS parity exception. Never call a debug QA app a production release.

## Migration sequence, strict go/no-go

| Gate | Action | Acceptance evidence | On failure |
|---|---|---|---|
| M0 authority and rollback | Read exact CDG scope, Github main parity policy, built artifact provenance; approve temporary cutover only; read current SHA and user consent. | Exact approved target and reversible test plan, no release implicitly. | STOP, no install. |
| M1 read-only local inventory | Run `scripts/qa/p4-phone-migration-inspect.sh` on Termux; inspect installed `dumpsys package`/ports via authorized ADB; check 10/20/30 Boot, helper, bashrc, runit and sanitized recent logs. Inspect Ubuntu config **locally**, with no raw secret printing. | Exact active package, launchers, all old port references, duplicate instance count and tunnel health. | Do not guess source configuration. |
| M2 snapshot and verify | Back up native Android APK paths/package metadata, Termux runit + boot + helper + bashrc and Ubuntu `phone-direct.yaml` (private local paths with restricted permissions), log versions/hashes **without credentials**. Confirm restore tested in safe space; preserve auth header material locally only. | Verifiable backups and simple rollback; no destructive package changes. | NO_GO. |
| M3 candidate install | Only with owner approval and verified ADB + package ID + signing + artifact SHA; install **`com.kelpie.browser.pcparallelverify`** side-by-side, no `-r` or uninstall, on the authorized S24. | Package visible with code30; no changes to original and pcverify data, candidate 18447 health=200. | Remove *only* the newly installed test candidate if safe, revert other changed files. |
| M4 one tunnel-owner switch | Terminate/restart **existing managed** runit instance once after backup and **minimum** target change, only after inspecting both run script readiness port and private Ubuntu `phone-direct.yaml` upstream; retain existing secrets/auth header semantics. Never create a second 8080 client. | `/readyz:8080=200`, exactly one tunnel-client, direct ChatGPT `tabs.list` + public snapshot, validated device-info target port18447. | Restore both original runit script **and** Ubuntu YAML atomically, restart only managed instance, confirm 18440 recovery. |
| M5 manual/native QA | Require real native user gesture, P4 P4-only example.com grant, 20 frames including freshness/pixel-based differentiation, STOP, cross-principal negative tests, HOME/foreground and SCREEN_OFF/LOCK rejection. | Measured with real S24 and no bypass of lock, Android secure content or other apps. | STOP/rollback; no app removals. |
| M6 reboot and recovery | Verify genuine shutdown/reboot after backups. No unlock bypass. After first manual unlock, require runit 8080 health, single client, correct P4 app alive, ChatGPT MCP, foreground screenshot after consent and no stale 18440/18437 target in active boot/recovery scripts. Repeat once and test network switch. | Reboot/Doze and auto-start behavior reported separately; Android 17 background activity launch restrictions acknowledged. | Restore backup and original working tunnel, leave rollback package installed. |
| M7 retire old packages | Only after repeatable M0–M6 pass, explicit confirmation of data/tabs/logins/bookmarks retention needs and verified rollback. Decide removal order based on actual dependency, never uninstall working parent tunnel before new is proven. | Old apps removed individually with `pm path` readback and unaffected working new MCP route. | Defer removal; data loss is irreversible. |
| M8 policy/release | If intended as production, independent owner CDG parity decision/exception, security+signing review, 50 device cycles, availability/rollback gate, explicit RELEASE_APPROVAL. | Reviewed main merge and verified published signed build only afterward. | `SYNC_BLOCKED_MAIN / RELEASE_NO_GO`. |

## Boot and recovery policy

One supervisor: `runit` owns exactly one tunnel client/8080; `Termux:Boot` only starts services/boot workflow, it does not create another manually started tunnel. Exactly one canonical browser launcher after unlock, with idempotence and bounded retry; remove redundant launcher invocations from 10/20/30/helper/bashrc only **after** mapping actual live invocation graph and storing backups. Avoid permission/lock bypass; Android background-start limits mean a guaranteed *unattended visual* Activity after reboot is not a valid acceptance assumption. Use user-visible foreground notification for visual capture after unlocking. Avoid forcing media projection or `FLAG_SECURE` bypass.

The staged native `P4` capture consent may be limited to 20 frames, 90 seconds, example.com and foreground-safe behavior. Owner's superseding product goal (one consent for active Kelpie session and arbitrary Kelpie-owned domains) is **not yet implemented**; it remains a separate release/architecture gate.

## Read-only operator command after verified acquisition

From Termux, only after downloading the reviewed script from a trusted exact GitHub commit/PR, run:

```sh
sh scripts/qa/p4-phone-migration-inspect.sh --self-test
sh scripts/qa/p4-phone-migration-inspect.sh
```

Do **not** paste the protected YAML, token, command-line flags, Environment or session cookies into chat. Report just the flag/health/status lines. The script performs no edits and explicitly reports `CUTOVER=NOT_PERFORMED`.

**Required latest status:** `MIGRATION_PLAN_READY / INSPECTOR_CI_PENDING / DEVICE_READBACK_BLOCKED / ZERO_CUTOVER_ACTIONS / ZERO_UNINSTALLS / RELEASE_NO_GO`.

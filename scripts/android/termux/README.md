# Kelpie Termux operational helpers — private S24 QA only

The current mother-compatible connection retains the approved computer's trust;
normal reconnects do not require the legacy per-session workflow below.
[The runit PRoot termination hook](runit-proot-terminate.md) fixes ordinary
restart of an existing PRoot-hosted tunnel without another tunnel, credential
or Android execution permission. It is a separate supervisor helper.

## Historical optional reconnect prototype

The original `ef-kelpie-request-consent` prototype is one fixed command, not a
second Android app or tunnel. The provisioning and installation restrictions
below describe that uninstalled optional prototype, not current device status.
NOT installed/enabled by the PR. Existing owner package:
com.kelpie.browser.r5mothernextverify, port 18463, Termux 8080.

## Flow

1. If Kelpie recently saw exactly one verified paired client, use the
   existing native one-tap action — Termux is not needed.
2. Otherwise tap "درخواست اتصال از Termux" in the Kelpie connection sheet.
3. Kelpie sends com.termux.RUN_COMMAND with one fixed SCRIPT_PATH and
   RUN_COMMAND_BACKGROUND=true (only on an explicit physical tap).
4. The already installed Termux uses the existing Ubuntu PRoot and reads
   its existing private owner Authorization header. It POSTs JSON {} to
   http://127.0.0.1:18463/v1/agent-session-request.
5. A verified, uniquely paired non-localhost principal may enter native
   PENDING approval. Only the owner can approve the real native dialog;
   STOP, lock and session revocation remain authoritative.
6. The real remote browser-control tabs/DOM/screenshot tests are the end
   acceptance. A queued command or HTTP error is not the session state.

## Optional one-time provisioning — not authorized for live device yet

- The existing R5 QA-only AndroidManifest adds the optional
  com.termux.permission.RUN_COMMAND permission and com.termux package query.
- Owner grants Kelpie "Run commands in Termux environment" from the Android
  App Info / Permissions / Additional permissions screen (wording may vary).
- Owner explicitly sets allow-external-apps=true in Termux
  ~/.termux/termux.properties and runs termux-reload-settings.
  CAUTION: this permits any app with that permission to execute commands in
  Termux; enable only in this owner-reviewed private QA.
- Install this reviewed script at exactly
  /data/data/com.termux/files/home/.local/bin/ef-kelpie-request-consent,
  compare source/destination SHA256, and set chmod 700. Do not pipe unknown
  scripts from the web or place secrets in command extras.
- proot-distro and Ubuntu must ALREADY exist. No parallel APK, Termux,
  tunnel, service, profile, daemon or change of port is permitted.
- Owner may revoke the Android permission and set allow-external-apps=false
  to rollback this optional interface; STOP revokes browser control.

IMPORTANT: The installed S24 APK signature differs from the GitHub CI debug
signer. No install-r, uninstall, profile wipe or side-by-side debug app is
allowed until exact-head CI, owner sign-off and recoverable single-package
upgrade plan pass. The prior encrypted hot snapshot is not proof that an
Android restore succeeds. No device configuration has been changed.

## Diagnostic outputs

NATIVE_APPROVAL_PENDING: server returned a structured pending native request,
but an additional physical owner tap is required.
REQUEST_HTTP_<status>_ERROR_<code>: a structured error, not re-pair permission.
REQUEST_RESULT_UNKNOWN_HTTP_<status>: response unrecognized. Do not infer that
native approval was lost; the owner's HTTP500/approval coexistence proves why.
REQUEST_RESULT_UNKNOWN_NETWORK_OR_TIMEOUT: delivery state unknown.
AUTH_* / TERMUX_PROOT_UNAVAILABLE: local prerequisites absent.

The pilot sends the Intent but does not yet receive an optional Termux
PendingIntent callback with script stdout. Thus dispatch success means QUEUED,
not CONNECTED. Future result plumbing requires native real-device QA.

Specification and test matrix:
https://github.com/imanipour2/kelpie/blob/fix/r5-mother-atomic-webview-switch-20261119/docs/specs/r5-owner-initiated-termux-reconnect.md

Official Termux reference:
https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent

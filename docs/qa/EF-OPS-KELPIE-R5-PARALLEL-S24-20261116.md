# R5 corrected-consent parallel S24 QA identity — 2026-11-16

Status: **DRAFT / NO RELEASE / NO MAIN MERGE / owner-authorized hardware QA only**. This stacked branch is based on R5 consent-only Draft PR27 at source commit `7a900a7303b3d9f8f2fafcd12a458cc5c52b4bf6`, not on a released application.

## Why a second identity is necessary
On the actual Samsung SM-S928B, the installed `com.kelpie.browser.r5sourceverify` v`0.2.11-r5-consent-qa` APK certificate fingerprint is `f07d685c34a7afd9bccce5c4d58e2066fd6a3e3776f9757e06500694c2bd1881`. A verified GitHub Actions replacement at the above SHA has a different debug-signer fingerprint `b0feaeef171f5e0ace7e49ae42e91e4d95ec38584fac8dd19b99cce3fc5cd287`. Android rejects cross-certificate updates. Never silently uninstall old app or clear its data to bypass this invariant.

This narrowly scoped QA packaging change uses the existing `kelpieR5SourceQaProfile=true` flag with a distinct package `com.kelpie.browser.r5fixedverify`, versionCode `32` / versionName `0.2.12-r5-consent-qa`, and localhost port **18458 in both app and core BuildConfig**. Legacy `r5sourceverify` on port18457 and `unifiedverify` stay untouched. A new package starts with fresh app-private state and needs its own genuine owner touch.

## Requirements
1. Build from exact branch HEAD with `qa-r5-source-slot.yml` and verify CI Kotlin formatting, JUnit, APK, dual-module ports, binary manifest, native alignment, SHA and signing certificate. Not a public release.
2. On real S24: check exact ADB device ID/model, package absent, old R5 retained, APK signature/digest, `POST_NOTIFICATIONS` initial state. Install only the new QA package using `adb install --user 0` (no `-r` against old package), then verify `pm path`, version, service `18458 /health`, and no false browser operations.
3. Owner must personally unlock and tap the native Approve dialog. Never synthesize approval via ADB/Accessibility or remote API. Observe `ACTIVE_CONSENT_ONLY`, visible STOP notification, owner STOP to OFF, deny and notification-permission-off fail-closed. Do not modify notification permissions while a grant is active without explicit device-owner direction.
4. Test STOP, ordinary HOME/resume, lock/Doze, process kill, two authenticated principals and 50 real granted cycles when proper test fixtures and owner input exist; carefully distinguish source/JVM tests from on-device results. R5 still does **not** implement agent navigation/DOM/PNG egress.
5. Keep both QA versions and backups until a reviewed cutover; public release remains `RELEASE_NO_GO`. Android/iOS parity unresolved. Preserve WordPress LIVE/STAGING and GitHub main.

Tracking: https://github.com/imanipour2/enerfoam-website/issues/98

Earlier CI-proven package remains recorded in Draft PR27; this branch is a **separate signing-isolation QA variant**, not a silent supersession of release authority.

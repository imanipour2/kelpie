# Kelpie Android 2026 compliance — API 36 migration (DRAFT)

**2026-11-15 | Source/test change, not Google Play authorization or production release**

## Verified baseline and mandatory migration

At base `main` commit `a884c5c6523fcfb95a5d74ea11aec3c054aec23f`:
- Android application `apps/android/app/build.gradle.kts` uses `compileSdk=34`, `targetSdk=34`, `minSdk=28`.
- Android library `core/android/build.gradle.kts` uses `compileSdk=34`.
- Verification helper `apps/updates/android-verification/build.gradle.kts` uses `compileSdk=34`, `targetSdk=34`.
- AGP `8.3.0` and Gradle Wrapper `8.6`; tested isolated P3 uses Android 17 on SM-S928B but **does not mean a production app meets 2026 Play requirements**.

Google Play **new apps and app updates after August 31, 2026** require **Android 16 (API 36)** target for ordinary mobile apps. An older existing app's listing policy is different. Our decision here is to prepare for a possible standard Play-distributed release rather than claim Kelpie is already listed. Source: [Google Play target API policy](https://support.google.com/googleplay/android-developer/answer/11926878) and [Android Developers guidance](https://developer.android.com/google/play/requirements/target-sdk).

Official [Android toolchain requirements](https://developer.android.com/build/releases/about-agp) say API 36 needs AGP **8.9.1+**. AGP **8.9** needs Gradle **8.11.1+**. Therefore this separate branch upgrades `AGP 8.3.0→8.9.1`, `Gradle 8.6→8.11.1`, `compileSdk/targetSdk→36` for the application, library and verification helper. [Official Gradle 8.11.1 ZIP checksum](https://gradle.org/release-checksums/) is pinned in the wrapper: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`. CI installs `platforms;android-36` and `build-tools;35.0.0`.

## Security and release gates (NOT passed by changing the build files)

1. **API 36 behavior review:** Android 16 and 15 edge-to-edge/insets, background activity starts, screen orientation, foreground services and notification permission, WebView lifecycle, and updated app compatibility. Test RTL, multi-window, pause/restore and native keyboard on S24 Android 17. Inspect manifest permissions; do not add `Accessibility`, `MediaProjection`, overlay or screen-recording permission merely for a private app-owned VirtualDisplay.
2. **Privacy:** Accurate, prominent localized consent for any real-tab capture, scope display, visible STOP and expiry; prevent credential/cookie/image logging. Complete Google Play Data Safety and Privacy Policy with what the **actual** integrated app transmits and stores; do not assert compliance without inventory and owner review.
3. **OWASP MASVS/MASTG:** map storage, cryptography, authentication/authorization, network, platform, code, resilience and privacy to executable tests and findings ([OWASP MASVS](https://mas.owasp.org/MASVS/), [MASTG](https://mas.owasp.org/MASTG/)). Specific concern: UI-issued local grant must not become a remote-authentication bypass; screenshot bytes may be sensitive.
4. **Signing:** release APK/AAB signing requires repository's four encrypted secret values `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` and exact `apksigner`/bundle verification. No keys in source/CI logs. Do not replace the existing installed browser using a differently signed debug APK, as previous P3 experiments required an **isolated** uninstall. Release key ownership and Android package identity must be reviewed.
5. **Testing:** CI with API 36 and Android Lint; installation of a **separate QA package** before proposing upgrade of `com.kelpie.browser`; physical S24 Android17 screenshot/OTP/connection/WebView/Doze/lock tests; iOS parity for user-visible features; rollback with user data retained. Verify actual `versionCode/versionName` and signed APK hash from installed package.
6. **Release:** Google Play developer account verification and store listing/data policy as applicable; explicit final owner release GO. A CI pass or GitHub merge is not enough.

## Change boundaries

- This is **separate from** Android/Apple consent-policy [Draft PR #7](https://github.com/imanipour2/kelpie/pull/7) and the bounded [P3 PoC Draft PR #6](https://github.com/imanipour2/kelpie/pull/6). No unreviewed merges or automated release.
- No modification to the installed phone app, GitHub main, Android signing identity, WordPress, Elementor or WooCommerce. No release claims without real hardware.
- Once CI passes, review dependencies, rebase carefully with consent/renderer work, and request independent review. Do not use a stale pre-API36 debug build as evidence for new Play compliance.

**State:** `API36_BUILD_MIGRATION_DRAFT / HARDWARE_NOT_TESTED / PLAY_COMPLIANCE_NOT_VERIFIED / RELEASE_NO_GO`.


### Current manifest security inventory (grounded in main, not assumed fixed)

`apps/android/app/src/main/AndroidManifest.xml` currently declares `INTERNET`, network/Wi-Fi discovery permissions, `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and `POST_NOTIFICATIONS`. It also sets `android:allowBackup="false"` and `android:usesCleartextTraffic="true"`, and exports an OAuth callback Activity using a custom URI scheme. These are **facts from source**, not findings that all permissions are unjustified:

- Trace microphone usage and runtime permission prompts; do not request RECORD_AUDIO unless a genuinely active user feature needs it.
- Review `usesCleartextTraffic=true` in the context of paired LAN HTTP/MCP. Authentication is not equivalent to encrypted transport: protect bearer tokens, avoid unsafe public Wi-Fi and evaluate TLS/network security configuration without breaking local paired access. Confirm whether the gateway is restricted to a trusted interface and how clients verify device identity.
- Audit the OAuth callback for exact redirect validation, state/nonce binding, replay protection, browser scheme hijacking risks and package signature identity.
- Review Android 16 foreground-service restrictions and ensure the CONNECTED_DEVICE service type and its permissions are justified and disclosed.
- Keep actual data extraction and Android Keystore protections under tests, including update/restore with matching signer, and verify that no credential backup path silently reappears.

OWASP mapping: [MASVS-NETWORK](https://mas.owasp.org/MASVS/), [MASVS-AUTH](https://mas.owasp.org/MASVS/), [MASVS-PRIVACY](https://mas.owasp.org/MASVS/). **No manifest or runtime behavior changes in this audit-only note**.

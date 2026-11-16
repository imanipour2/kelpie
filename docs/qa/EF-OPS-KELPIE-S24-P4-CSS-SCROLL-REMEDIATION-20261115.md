# P4 WebView private-display CSS scroll restoration — controlled Android hardware QA

Mission: EF-OPS-KELPIE-S24-REMEDIATION-002  
Device: Samsung Galaxy S24 Ultra (SM-S928B), Android 17, Windows USB/ADB  
Scope: isolated `com.kelpie.browser.pcverify` QA; public `https://example.com/` only. **Not a production approval.**

## Evidence before this change

- `7c7d746e938ad9fde7aa19997df11630722c8e45`: real local native consent followed by 20/20 distinct PNGs (1080x1920), `matchedNonceFrames=20`. Budget exhaustion revealed a private-display cleanup defect.
- `dbf90c999cfda3521caa851c186844adb9169ef6`: exact-head six CI workflows succeeded. APK pulled back from the installed QA profile matched SHA256 `24f97cfb660011bfec36e05c6df5e28a34c1b25309a6fdc768fa3e27f09bfa40`.
- On `dbf90c9`, after a **fresh actual on-device affirmative consent**, 20/20 independent nonce-matched PNGs succeeded, frame 21 was HTTP 403, and after frame 20 the renderer was `RESTORE_PENDING` rather than `PRIVATE_DISPLAY`. Same Activity (user 0, task 628), same tab UUID, JS window document nonce, synthetic input, history state/length, and URL survived. The QA pixel marker was removed.
- **Remaining failure:** `window.scrollY` was **419.9111022949219 CSS px before** and **843.3778076171875 CSS px after** the same-WebView restore. All other nine checks passed. The test artifact metadata JSON SHA256 is `c1532f518c8c73cb6b5d9d0c196719710ad9f0d61122fbcdbd36feeda4397891`. No screenshot bytes retained.

## Root cause and narrowly scoped remediation

`WebView.scrollY` is an Android-native scroll offset; copying this value into a differently sized `Presentation` and restoring the native offset does not guarantee the same document `window.scrollY` in CSS pixels. The previously verified offline P3 QA used JavaScript CSS scroll restoration, see `apps/android/app/src/main/java/com/kelpie/browser/P3ScrollRestore.kt` in branch `experiment/s24-p3-compose-owner-qa-20261115`.

In `PrivateWebViewDisplay.kt`, read nonnegative finite `window.scrollX/Y` via Android WebView's own `evaluateJavascript` **while the exact original view is still mounted**; abort starting capture if either snapshot cannot be obtained in bounded time. After releasing the private display and reattaching that **same** WebView to its Compose owner, restore with `window.scrollTo(initialCssX, initialCssY)` and verify from the actual document within 1.5 CSS px, with up to three bounded attempts. Never navigate or re-create the WebView. This operation is restricted by the existing public-origin, local-consent and identity gates.

## Physical acceptance still required for current CSS fix

1. Verify exact new GitHub branch SHA, all applicable CI workflows, the QA ZIP digest, installed APK digest, unique QA app ID, and absence of sensitive permissions. Do not install in the original `unifiedverify` app.
2. On real owner Android user `u0`, keep exactly the intended QA Task/Activity in foreground; Dual App `u95` may have a separate historical ActivityRecord and must not be mistaken for an additional owner Task.
3. On only public `https://example.com/`, inject a synthetic non-sensitive document nonce, form value, `history.state` and scroll position. Verify before snapshot. Request native consent, then wait for the person's affirmative local gesture; no ADB consent simulation.
4. HOME, capture 20/20 nonce-matched genuinely fresh PNGs on the hidden Activity, record only SHA256 and dimensions, and verify budget exhaustion moves renderer out of `PRIVATE_DISPLAY`. Frame 21 must be denied.
5. Resume **the original Task/Activity**, prove same tab/document nonce, form, history and URL, absent QA marker, and exact CSS `window.scrollY` within 1.5 CSS px. If any check fails, remain RELEASE_NO_GO.
6. Independently test native STOP, 90-second expiry, keyguard, client/document mismatch and cancellation; require separate release/security and rollback approval before any non-QA installation or merge.

Canonical operational readback: https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6895149364 . The source PRs remain Draft; do not merge aggregate PR #12. WordPress/WooCommerce and the original Kelpie are unchanged.

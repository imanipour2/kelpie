# S24 P4 physical evidence — no new device run

Device planned: Samsung Galaxy S24 Ultra SM-S928B, Android 17. This document deliberately does **not** certify a new build.

Historical hardware record: https://github.com/imanipour2/enerfoam-website/issues/98
- `84ed68206e72cf4f99849e2c4c4d3261674913cc`: 20 distinct nonce-proven public `https://example.com/` screenshots; 11/11 state + settled CSS scroll assertions. QA artifact and APK hashes are in Issue #98.
- `b8c3a351661b58578620e49860514c2571ebda2f`: genuine on-device 90s no-frame expiry; OS private display and capture notification absent before API polling; post-expiry frame denied. Source-specific installed APK SHA256 `ff7fd24c39a69567b844b3269821e2638ee59223d00d179a713ac2e2d3253357`.
- A manual notification STOP was **not** tapped and cannot be classified PASS.
- Physical SCREEN_OFF, 2-principal isolation and FAILED recovery were **not** confirmed on this remediation branch.

**New branch physical QA:** NOT EXECUTED. There is no new installed APK SHA/signature to report; no hardware PASS claim.

**Before any authorized future QA installation:** inspect ADB serial/user/API, all installed packages/versionCodes, artifact/source commit, APK/SHA256/manifest/signer, distinct package/port and no RECORD_AUDIO; require operational authorization; verify local real consent; use public example.com only; capture only synthetic metadata and nonce proof, never real credentials or WordPress.

**Acceptance steps:** real approval → HOME → 20 changing pixel nonces → frame21 403 → same Task/Activity/tab/document/form/history and CSS scroll ±1.5px after delayed readback → native display/notification release. New separate approvals for real STOP, 90s zero-frame expiry, screen off, two principals, simulated faults and 50 cycles. Never use ADB tap to fake consent.

Flags: `HARDWARE_TEST_BLOCKED_OR_PENDING / RELEASE_NO_GO / ORIGINAL_APP_UNCHANGED`.

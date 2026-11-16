# R5 AI Vision Quality Negotiation and Memory Safety

Status: DRAFT / Android QA branch only / not merged / not installed
Date: 2026-11-16
Stacked on draft PR 32. Owner evidence is tracked in ENERFOAM issue 98.

## Research and current source

The native Android R5 uses an app-owned VirtualDisplay, Presentation and ImageReader(maxImages=2), and verifies compositor nonce freshness before encoding. OneShotFrameBuffer closes late/cancelled Bitmap ownership; Image.close and Bitmap.recycle are in finally blocks. Those protections cannot cap ByteArrayOutputStream growth, Base64 duplication, JSON serialization or slow-client response retention.

Upstream ConnectScreenshotBudget retries large account screenshots as increasingly compressed JPEG; that strategy must NOT automatically loop for R5 native agent screenshots. Playwright supports in-memory screenshots and targeted element crops. MCP tools/list provides schema-based discovery; R5 native status/HTTP routes do not automatically imply a dedicated named MCP tool has been published in CLI tools/list.

## Implemented source-only API, subject to tests

After native owner consent, authenticated agent-runtime-status returns captureCapabilities. A separate authenticated agent-capture-capabilities route returns the same versioned capability contract and is protected by last-mile grant checks.

The existing agent-command SCREENSHOT accepts an optional capture object with quality auto, high, or original; purpose overview, layout, fine_text, coordinate_action, visual_evidence, or photo_scene; optional maxLongEdge 1024..4096, and receiverSupportsScaleMetadata boolean. Unrecognized or malformed capture options are rejected. A request without capture preserves the original PNG format, original dimensions and original JSON fields.

Auto selects after-proof image delivery resolution using the pure adaptive policy. Downsampling requires explicit receiver coordinate metadata support. High selects a new native-size lossless capture (never interpolates the old image); original retains the original size and PNG format. The current first integration supports PNG only; it does not advertise WebP, JPEG, region crops or a published dedicated CLI MCP tool.

Scaled results include sourcePixelWidth and Height, output width and height, scaleX/Y and imageToSourceScaleX/Y. These factors map between output pixels and native bitmap pixels, NOT inherently to CSS viewport coordinates. The model should request auto for initial overview, request high when it cannot read fine UI text, then prefer DOM or owner clarification; Kelpie does not automatically replay screenshot commands.

## Memory safeguard

R5BoundedImageOutputStream fails before compressed output exceeds 8 MiB; it uses 4 MiB on low-RAM devices, low-memory signals or Android memoryClass below 192 MiB. The stream does not buffer any overflow bytes. The encoder recycles its additional scaled Bitmap on every return path. The outer capture finally recycles the original Bitmap and closes the pending one-shot holder. An oversized image yields failure instead of partial content or a silent quality downgrade. Explicit catch of Exception does not suppress OutOfMemoryError.

This bounded PNG output still requires original native allocation before scaling. PNG/Base64 serialization creates additional copies; no claim is made that native GPU peak or low-end energy use is already optimized. Do not store screenshot files on-device by default; remote AI-side copies cannot be recalled by deleting local bytes.

## Required acceptance gates

1. All Android Gradle compile, ktlint, scoped JUnit, lint, debug APK and artifact signing checks pass on exact commit.
2. Real S24 Ultra owner-native approval; ChatGPT is top resumed, Kelpie remains background; verify initial auto and explicit high output, native nonce freshness, tab and document identity, correct image metadata.
3. Trigger 4/8 MiB compressed ceiling, cancellation, STOP and lock during encoding, two concurrent calls, slow HTTP receiver and disconnect. Capture process memory and fd usage; verify no unbounded retained bytes.
4. Test readability and click targeting on RTL/Persian, English small text, numerical tables, dense components and low-RAM phone. Fail closed rather than silently downgrade critical fine text.
5. Resolve fixed 512-frame nonce cap independently with secure epoch rotation, then test captures 512, 513, 1000 and 2000 without retaining historical images.
6. Eliminate unsafe global ADB input, which previously typed fixture URL into ChatGPT foreground composer. An initial focus check alone is not a race-safe defense.
7. Provide true MCP tools/list advertisement and equivalent iOS behavior in a separate reviewed integration before main/release. Do not claim current QA branch is production ready.

References:
- https://modelcontextprotocol.io/specification/2025-06-18/server/tools
- https://playwright.dev/docs/screenshots
- https://developer.android.com/reference/android/media/ImageReader
- https://developer.android.com/reference/android/app/ActivityManager
- https://developer.android.com/reference/android/os/PowerManager
- https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat
- https://github.com/UnlikeOtherAI/kelpie/blob/main/core/android/src/main/java/com/kelpie/browser/handlers/ScreenshotHandler.kt

GATE: SOURCE_QA / NO_MAIN_MERGE / NO_APK_INSTALL / DEVICE_NOT_RETESTED / RELEASE_NO_GO / SYNC_BLOCKED_MAIN.

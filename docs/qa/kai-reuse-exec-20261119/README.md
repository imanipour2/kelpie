# KAI reuse-first source remediation

Mission: **EF-OPS-KELPIE-KAI-REUSE-EXEC-001**. Source work authorized by the
owner on 2026-11-19 (Asia/Tehran), also recorded as Annex B of
[KAI-001](https://docs.google.com/document/d/1AJ8Yfr9tDXg4VRy8aepkpuWdMQEWSAn5ObrImMg-zm0/edit).
This inventory and change design were written **before implementation**.

## G0 source and authority readback

- Base: `91dadda8caebe69394e4a6b4589892ae4a495e54`, freshly read from GitHub
  `main` and matched against `git ls-remote` and the local checkout.
- Branch: `feat/kai-reuse-security-observation-20261119`, directly from that base.
- Read: `AGENTS.md`, `CLAUDE.md`, `core/README.md`,
  `docs/policies/development-and-release.md`, the handlers and complete gateway
  registration/dispatch chain listed below. Maximum 500 lines per source file.
- Authorization: source, isolated branch, tests, permanent ADR/QA documentation,
  and review PR. No merge, release, APK installation/removal, signing change,
  connection/tunnel/port/VPN/Termux change, device automation, or website change.
- KAI architecture and Annex A are proposals. Annex B authorizes this scoped
  implementation; it does not approve a second runtime or waive platform parity.
- Existing CDG/main/Live Master Log lag the newer KAI/RCA documents.
  `SYNC_BLOCKED_MAIN` is a record/integration state, not a reason to invent approval.

## Reusable Components Inventory

Paths below are repository-relative. `A` abbreviates
`core/android/src/main/java/com/kelpie/browser/`; `S` abbreviates
`packages/shared/src/browser-control/`; `N` abbreviates `core/native/core-mcp/`.

| Existing capability | Code path, class/function | Actual entry point | Source | Defect / limit | Minimum proposed repair | Verification | New code justification |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Browser ownership and registration | `A/KelpieBrowserSession.kt`, `init/registerHandlers` | Existing host creates one session; registers handlers then BrowserGatewayBinding | main | Native R5 owner-consent runtime absent from main | Preserve ownership; no extra WebView/host | embedding check, registration review | None |
| HTTP/MCP admission and serialized execution | `A/network/Router.kt`, `BrowserGatewayBinding.kt`, `BrowserGatewaySession.kt`; `N/src/browser_gateway.cpp` | HTTP `/v1/*` and `/mcp` -> registered router -> persistent gateway -> existing handler/fixed reader | main | Pairing is not R5 consent; direct request loses scoped principal; page realm cannot grant consent | Keep native authorization boundary; do not advertise new owner-authorized actions | native gateway unit tests; explicit blocked R5 cases | No server or authorization system |
| Canonical snapshot | `S/snapshot.ts`, `collectBrowserSnapshot`; `N/src/browser_gateway.cpp`, `CollectBrowserSnapshotExpression` | `kelpie_browser_control` -> `tab.snapshot` -> generated fixed reader -> `browser-control-evaluate` -> HandlerContext | main | Raw query URL and sensitive ARIA/placeholder; incomplete coverage metadata | Extend this reader with redaction, geometry, sensitivity and honest coverage | browser fixtures T01/T03/T06/T07 | No second scraper |
| Canonical element refs | `S/native-reference.ts`, `rememberNativeSnapshot`, `resolveNativeReference`, `actOnNativeReference`; C++ `refs` registry | `tab.snapshot` retains actual Elements; `tab.click/type` validates native ref membership then fixed action | main | No TTL; page-owned mutable record; insufficient role/geometry/hit testing and mutation invalidation | Harden existing registry, bounded lifetime, same-element validation and single-use action; page data never authority | stale/reorder/replacement/occlusion fixtures T02/T04/T10/T12 | No second registry |
| Selector projection | `BrowserGatewayNative.selectorSnapshotExpression`; `N/src/browser_gateway.cpp`, `CollectBrowserSelectorSnapshotExpression` | Existing JNI -> same canonical reader -> value-free selectors | main | Discards geometry/coverage needed for bitmap correlation | Extend projection only as needed; preserve fields | JNI/build, canonical fixture tests | Small additions to existing API |
| Legacy annotation map | `A/handlers/InteractionScripts.kt`, `annotationElementsScript` | `A/llm/LLMHandler.kt` registers `screenshot-annotated`; native extension discovery schema exists | main | 50-element cap silent; `el.value` used as name; no Canvas labels | Reuse canonical projection; redacted names and explicit coverage; existing image path | T01/T03/T07 | No parallel observation engine |
| Legacy annotation mutation | `annotationActivationScript`, `fillAnnotationScript`; LLMHandler `clickAnnotation/fillAnnotation` | `kelpie_click_annotation/fill_annotation` -> router | main | Blind re-enumeration by index; URL-only expiry; no previous observation required | Fail closed unsafe index path; direct callers toward canonical refs until native KAI consent integration | unchanged DOM/reorder/no-session refusal T02/T12 | No legacy index registry |
| Legacy selector interaction | `A/handlers/InteractionHandler.kt` and `InteractionScripts.kt` | click/tap/fill/type/select-option/check/uncheck routes | main | Value/diagnostic echo; DPR-only screenshot tap; synthetic events are not proof of effect | Remove confidential output; reject ambiguous/unbound screenshot coordinate input; preserve safe existing selector commands | fixture T03/T05/T08/T12 | Small policy helper only if needed |
| DOM and page context | `A/handlers/DOMHandler.kt`, `A/llm/PageTextSnapshot.kt`, LLMHandler | get-dom/query-selector/get-attributes/page-text/form-state/accessibility extensions | main | Raw HTML/form values/metadata and URLs escape | Sanitize existing read/serialization paths; sensitive fields null/redacted | synthetic secret fixtures and no-log checks T03 | Shared sanitizer, not scraper |
| Hit testing and temporary highlights | `A/handlers/CoordinateDiagnosticsHandler.kt`, `HighlightHandler.kt` | registered diagnostic/highlight routes | main | Diagnostic value fallback; no proof geometry belongs to image | Sanitize summaries; keep QA highlights transient | T04/T05 | No permanent overlay |
| Bitmap capture and scale | `A/handlers/HandlerContext.kt`, `captureScreenshotPayload`, `viewportMetrics`; ScreenshotHandler | screenshot and annotated screenshot reuse same WebView/Bitmap encoder | main | No visual redaction; no frame freshness proof for background; capture/map race | Protect output before encoding, refuse unproven capture, Canvas on copy, real imageScaleX/Y | unit geometry + Android fixture T03/T05/T09/T13 | Small bitmap drawing helper only at responsibility boundary |
| Full-page capture | ScreenshotHandler `captureFullPagePayload/drawFullContent` | screenshot fullPage parameter | main | Scroll-stitch lacks atomic map/frame and sensitive coverage guarantee | Fail closed annotated/unverified sensitive full-page requests; no false full coverage | T07/T09 | None |
| Native owner consent/action receipts | `A/agent/AgentOperationRuntime.kt`, `VerifiedNativeBrowserAdapter.kt`, R5 coordinator | PR66 coordinator registered conditionally; existing consent runtime, commandId, sequence and before/after receipts | PR66 only | Not in main; click fingerprint and window scroll insufficient for effect/coverage | Reuse after explicit dependency reconciliation; do not clone into main | native/runtime tests then S24 T08/T10/T11 | New runtime prohibited |
| Fresh background frame provider | `A/capture/PrivateWebViewDisplay*`, `R5VerifiedFrameEncoder.kt`, `R5MotherMcpCaptureAdapter.kt` | PR66 `attachMotherMcpScreenshot()` -> HandlerContext hook -> existing ScreenshotHandler | PR66 only | No sensitive-image masking; map/frame atomicity missing | Extend accepted provider later; no Canvas/cache substitute for R5 fresh frame | S24 HOME/STOP/lock/25 lifecycle cycles | Second capture service prohibited |

## Dependency map and decisions

| PR | Base SHA | Head SHA | State and dependency |
| --- | --- | --- | --- |
| [66](https://github.com/imanipour2/kelpie/pull/66) | `e9ee1617d7760e4f3032e008be0aad19489af7ad` on `qa/r5-owner-friendly-background-optin-20261118` | `68ade958af66b7f6de219abd2d9f9011478f5a40` | Draft, unmerged; main comparison diverged: ahead 518 / behind 1, 217 files |
| [67](https://github.com/imanipour2/kelpie/pull/67) | `68ade958af66b7f6de219abd2d9f9011478f5a40` | `0d33eb894383f8654d4feffe4fa22c4bbb64b802` | Draft, unmerged, one documentation file; depends on 66 |
| [68](https://github.com/imanipour2/kelpie/pull/68) | `91dadda8caebe69394e4a6b4589892ae4a495e54` | `07b49a14bf1e0ccf8c631147606ffb27225316c4` | Draft, unmerged, two documentation files; mission/design, not runtime |

No cherry-pick or merge is part of this work. Existing canonical fixed-reader
code is shared with iOS; Android-specific capture changes still need iOS parity
or an independently approved exception before integration.

### ADR: smallest safe implementation boundary

1. Fix P0 exposure and P1 stale targeting in existing shared/Android paths first.
2. Keep legacy schemas where safe. A blind index action is a security bug, so a
   clear refusal is preferable to preserving its wrong-target behavior.
3. Extend existing screenshot/annotation paths; draw only on the output Bitmap.
   Unknown sensitive coverage or unsupported geometry must refuse export.
4. Native R5 consent, STOP, lock and principal guarantees do not exist on main.
   New KAI owner-authorized actions/background success remain blocked until
   that dependency is reviewed. No new permission flags supplied by callers.
5. No native touch experiment without evidence that fixed DOM actions fail on
   a specific supported control. No runtime dependency or persistent script.

## Threat model

- Web text, labels, ARIA, placeholders, URLs and selectors are untrusted data;
  they cannot grant native permission, extend consent or disable STOP.
- Form values, hidden credentials, password/OTP/payment data, URL credentials,
  query and fragments must not become names, JSON, image output or permanent logs.
- Renderer/DOM races, stale numbers, replacement nodes, overlays and repeated
  commands may redirect or duplicate mutations. Refuse uncertain targets.
- The existing page-world JS registry is not a security boundary against a
  hostile renderer. Random keys do not fix that. AndroidX isolated-world APIs
  require supported provider features and bootstrap/listener changes; there is
  no assumed direct `WebViewCompat.executeJavaScript(world, ...)` API.
- Gray/cached/absent frames, discarded bitmap buffers and incomplete page
  coverage must not turn into success. No HTTP 200/build-as-device-QA claims.
- Do not save actual account pages, values or screenshots in source/CI/receipts.

## Gate ledger before implementation

G0 inventory/design recorded. Source edits and fixture checks next. G4 physical
S24 tests are **BLOCKED**: no device was accessed by this task. Historical version
0.2.20/code40 and successful remote-read/reboot reports in the
[RCA log](https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit)
are not fresh APK/signature/consent/device readback. Twenty-five captures are not
twenty-five complete lifecycle cycles. G5 merge/install/release are not authorized.

Detailed tests, contracts, actual results and final source/CI SHA follow in the
same folder. No feature, build, device or acceptance status is asserted here.

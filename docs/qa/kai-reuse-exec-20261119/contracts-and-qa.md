# KAI reuse remediation: contracts, dependencies and QA

Mission: **EF-OPS-KELPIE-KAI-REUSE-EXEC-001**. This is a source-review record,
not device acceptance or release approval. Read it with the pre-implementation
[Reusable Components Inventory](README.md).

## Baseline and authority

| Item | Evidence / scope |
| --- | --- |
| Freshly read base `main` | [`91dadda8caebe69394e4a6b4589892ae4a495e54`](https://github.com/imanipour2/kelpie/commit/91dadda8caebe69394e4a6b4589892ae4a495e54) |
| Independent source branch | `feat/kai-reuse-security-observation-20261119`, directly from that base |
| G0 inventory before source changes | [`fe5eab3ce6c8395304b372d088d7d0c5b8af921c`](https://github.com/imanipour2/kelpie/commit/fe5eab3ce6c8395304b372d088d7d0c5b8af921c) |
| Source-work authorization | Current owner mission and Annex B of [KAI-001](https://docs.google.com/document/d/1AJ8Yfr9tDXg4VRy8aepkpuWdMQEWSAn5ObrImMg-zm0/edit) |
| Architecture references | KAI-001 / KAI-AO-001 remain proposals; [P5 research](https://docs.google.com/document/d/1oM-IQArjUKb0biVodw87F0KJGPh74XAwg01cADPgtDM/edit) is research, not device evidence |
| Governance | [EF-GOV-DOCOPS-001](https://docs.google.com/document/d/1ZwBQdFdMwYu6jOAcIZ32oVIedzy4W5BGaZEz91XbEi4/edit); reviewed main/CDG/Live Log lag newer KAI records: `SYNC_BLOCKED_MAIN` |
| Allowed work | Source branch, fixtures, tests, documentation and development PR |
| Gates not granted | Main merge, install/remove APK, public release, connection/port/tunnel/VPN/Termux/signing changes, other-app control, consent/STOP/lock bypass, website changes |

Results below describe the implementation working tree and locally executed
checks. The G0 commit is not the tested implementation commit. The development
PR must bind final source SHA and CI runs before asserting `SOURCE_COMMITTED` or
`CI_PASS` for that implementation. No new S24 session, installed APK version,
package inventory or signing certificate was read in this mission.

## ADR: minimum change inside the existing paths

**Decision under review:** repair the fixed snapshot reader, ephemeral reference
registry, native gateway and existing Android handlers. Keep the existing
endpoint names and request schemas where safe. Explicit refusal replaces unsafe
blind-index and unbound-image actions. No new WebView, scraper, image service,
KAI server, permission authority, dependency or persistent content script is added.

| Existing component | Source change and reason |
| --- | --- |
| Shared fixed reader | [`snapshot.ts`](../../../packages/shared/src/browser-control/snapshot.ts): bounded private-value reflection removal, sanitized names/URL, sensitivity, geometry and partial coverage; does not export entered values |
| Existing reference registry | [`native-reference.ts`](../../../packages/shared/src/browser-control/native-reference.ts): 15-second expiry, original-object checks, mutation/geometry/hit-test validation, bounded storage and one-use admission |
| Native gateway | [`browser_gateway.cpp`](../../../core/native/core-mcp/src/browser_gateway.cpp), existing header and selector builder: native lease bounds, cancellation/tab invalidation, atomic validated publication, safe metadata/error preservation and structural selector projection |
| Android DOM/LLM reads | `DOMHandler`, `LLMHandler`, `PageTextSnapshot`, `CoordinateDiagnosticsHandler` and `ElementSelectorScript`: reuse current read paths, sanitize summaries and diagnostics; `ObservationPrivacyScripts` is a small shared policy helper, not a second reader |
| Android interactions | `InteractionHandler` / `InteractionScripts`: no value echo, safe single-target fill/type, ambiguity/focus/hit checks, sensitive-action refusals, blind annotation indices disabled, screenshot-space taps disabled |
| Existing capture path | `ScreenshotHandler` / `HandlerContext`: one native fixture gate shared by plain, diagnostic and annotated exports; experimental Canvas labels on a copied Bitmap |
| Pure capture calculations | `ScreenshotGeometry.kt`: finite geometry, independent scales, native fixture predicate and peak allocation accounting; extracted to make boundary checks testable without a second capture engine |
| Durable feedback | `FeedbackStore`: fixed allowlist of categories/status/counters; arbitrary body, values, URL, account metadata and image payloads omitted |
| Documentation/tool descriptions | Existing API docs and discovery descriptions state current refusals; no new KAI route or owner-action schema |

The existing ephemeral registry now releases its observer/listeners on action,
expiry or invalidation. It is not a permanently injected page bootstrap. No
AndroidX isolated-world or native-touch implementation has been added. P4 is
deferred: no real control failure has established a need for it.

## Implemented canonical observation contract

The callable route remains `kelpie_browser_control` → `tab.snapshot` → the
existing native gateway → generated fixed reader → existing renderer binding.
The same package reader also supplies the existing JNI selector projection.
No schema accepts page-provided native permission or a new authorization flag.

The existing `{url,title,text,truncated,elements}` shape is extended additively:

| Field | Current meaning / limit |
| --- | --- |
| `observationId` | Native gateway session plus observation generation; not a compositor nonce or native consent token |
| `tabId` | The native request's tab identity; does not certify the renderer's document or origin |
| `ttlMs` | Remaining native monotonic lease, at most 15,000 ms including collection time |
| `elements[].ref` | Opaque reference to the captured object in the existing renderer registry; never an `E01` display number |
| `role`, `name` | Bounded role and sanitized accessible name. Ordinary `PRIVATE` controls can retain their accessible label; `SENSITIVE` names are redacted |
| `value` | Always `null` in the element map; input/editor values never become exported names |
| `rect` | Finite CSS `{x,y,width,height}`; negative positions can be legitimate, negative sizes are refused |
| `visible`, `enabled` | Observed page state, rechecked before an action; not native authorization |
| `sensitivity` | `PUBLIC`, `PRIVATE` or `SENSITIVE`; a page-derived classification with known limits |
| `allowedOperations` | Reader currently advertises `click`/`type` only where suitable; hints do not grant permission or add canonical select/fill support |
| `viewport`, `visualViewport`, `scroll`, `devicePixelRatio` | Page-reported bounded geometry; `visualViewport` can be `null`; DPR is descriptive, not the image transform |
| `coverage` | `INCOMPLETE` or `UNKNOWN`, observed/scanned counts, nullable `totalCandidates`/`unobserved`, `truncated`, fixed limitation identifiers |
| `privacyRects`, `screenshotBlocked`, `screenshotBlockReasons` | Conservative page-derived hints. **Never proof that exporting real-page pixels is safe** |

The reader defaults to 200 elements and 16,000 text characters, caps requests at
1,000 elements / 100,000 characters and bounds traversal. The annotation
projection requests 50 entries. When traversal or output bounds prevent a count,
`totalCandidates`/`unobserved` are `null`; the result must not imply full page,
infinite-list or task coverage. Native copies only known metadata fields, finite
geometry and fixed operation/reason values. Duplicate or malformed refs reject
the whole result before native reference publication. Native `text` is rebuilt
from sanitized URL/title/elements rather than forwarding renderer text verbatim.

The fixed reader strips URL credentials, query and fragment. It collects bounded
known private values locally to remove their reflected appearances in names and
text, then exports no such value. This is targeted disclosure reduction, not a
proof against a hostile page inventing/encoding secrets elsewhere. Separate
legacy `tab.url`/`tab.title` methods keep their existing binding behavior; this
work does not certify every generic evaluator or data-export extension as safe.

### Reference lifecycle and action checks

Native retains at most 16 observed tabs. Collection and action use a native
monotonic deadline; clock injection is a C++ test facility, not a JSON parameter.
Cancellation, native invalidation, authorization refusal and explicit tab changes
retire refs. An in-flight observation invalidated before publication returns
`OBSERVATION_RACE`. An action noticing a changed selected tab refuses instead of
silently switching back to an old reference.

Within the renderer, the registry stores original objects, role/name-related
signatures and geometry. It expires at 15 seconds, caps entries at 1,000 and
bounds retained signatures. Observed DOM mutations, navigation/pagehide,
history/hash changes, resize/scroll and input/change invalidate it. Pending
MutationObserver records are checked synchronously, so an action cannot rely
only on whether the observer callback already ran. Ref dispatch checks
connectivity, identity/signature, geometry tolerance, enabled/visible state,
viewport center and `elementFromPoint`. It repeats checks after synchronous
focus handlers and consumes the observation before dispatch to prevent reentry.
Action admission also reuses the reader's sensitivity and allowed-operation
policy, including related labels and sensitive form/ancestor context. Missing
metadata grants no operation, and noninteractive snapshot nodes cannot be clicked
merely because they have a reference. These are page-policy checks, not consent.

| Error | Meaning in this branch |
| --- | --- |
| `STALE_REFERENCE` | Legacy native membership/cross-observation rejection |
| `STALE_OBSERVATION` | Expired, invalidated, already consumed or wrong current-tab observation; also the intentional legacy index/image-coordinate refusal |
| `STALE_ELEMENT` | Disconnected, replaced, moved or otherwise changed captured target |
| `AMBIGUOUS_TARGET` | Duplicate/ambiguous target resolution; never choose a convenient match |
| `OCCLUDED` | Hit testing cannot establish that the target owns its center |
| `NEEDS_HUMAN` | Sensitive target/operation or missing safe image-export provider |
| `UNSUPPORTED` | Unsupported target type/root/state; native shadow actions are not claimed |
| `OBSERVATION_RACE` | Native invalidation or capture/document/geometry race |

These checks address stale targeting in cooperative/ordinary pages. Android's
page-world JavaScript registry is **not isolation from hostile scripts**. A page
can affect page-world objects and APIs. No native principal/consent generation,
renderer attestation or complete STOP/keyguard action runtime has been fabricated.
Existing transport pairing and gateway authorization remain in force; they do
not satisfy the missing R5 owner-session contract.

### Legacy compatibility and deliberate refusals

HTTP/MCP endpoint names and discovery remain registered. Android
`click-annotation`/`fill-annotation` always return `STALE_OBSERVATION` with
`OBSERVATION_REQUIRED`, including when the URL is unchanged. A fresh screenshot
does not re-enable index actions. `tap` with `coordinateSpace:"screenshot"`
also refuses because it lacks a bound image transform; callers must not evade
that refusal by relabeling the same coordinates as viewport coordinates.

Existing selector click/fill/type/select/check paths are repaired in place.
Fill replaces and type appends on one captured target; no delayed per-character
clear/re-query sequence remains on Android. Sensitive controls, ambiguous matches,
inert/disabled targets, focus-time replacement/repurposing and obscured hit
targets are rejected. Fixed errors do not echo input text or unsafe selectors.
The events are synthetic JavaScript, not `MotionEvent` or proof of a business effect.

Android `get-dom` returns sanitized detached HTML with `redacted:true` rather than
an unmodified document. Form values are `null`; metadata and attributes are
filtered. Existing explicit diagnostic evaluation/actions refuse on the protected
diagnostic path. This is not a claim that the product's general script/evaluation
extensions were removed or that prompt injection has been solved globally.

## Experimental image contract: P2 not accepted

**All ordinary Android screenshot exports are blocked with `NEEDS_HUMAN`.**
The common existing handler checks a native build flag and `WebView.url`; page
JSON cannot enable capture. The only exception is synthetic fixture development:
debug `-PkelpieKaiAnnotationQa=true` and exact native URL
`https://kai-fixture.invalid/`. Default/release builds keep the flag false.
The reserved URL is not a deployment endpoint or account site; tests must load
synthetic content locally. No DNS/port/tunnel/device setup is changed by this work.
Malformed requests or absent renderers can still receive their earlier parameter
or `NO_FRESH_FRAME` errors. `fullPage:true` always returns `NEEDS_HUMAN`.

The fixture path reuses the owned WebView, `postVisualStateCallback`, Bitmap,
Canvas and the shared selector snapshot. It requires foreground visibility/focus,
an unlocked device, unchanged native tab/document and existing abort/remote
document guards. It checks the snapshot/map before and after draw, rejects uniform
sampled frames and times out after three seconds without the callback. These are
conservative experiment checks, **not compositor freshness/privacy attestation**.
Background, HOME/private-display, real-account and accepted P2 behavior remain blocked.

| Fixture output | Meaning |
| --- | --- |
| `observationId` / `annotationSessionId` | Same newly generated ID for this image/map; not the canonical actionable-ref observation |
| `tabId`, `rendererIdentity`, `documentEpoch`, `capturedAt` | Native in-process capture identity and timestamp; not durable renderer authentication |
| `consentGeneration:null`, `frameNonce:null` | R5 authority and compositor frame evidence are absent |
| `readOnly:true`, `actionByRefAvailable:false` | Image map cannot authorize an action |
| `freshness:"foreground-callback-unattested"` | Callback/draw experiment, no background fresh-frame claim |
| `privacy:"synthetic-fixture-only-no-privacy-attestation"` | No safe real-page redaction proof |
| `annotations` | At most 50 snapshot entries, `displayId:E01…E50`, CSS rect and sanitized name/role; `elementRef:null`, `allowedOperations:[]` |
| `labelDrawn`, `omissionReason`, `annotationCoverage` | A label can be omitted for clipping/collision; counts must not imply all labels are visible |
| `validUntil:"display-only"` | Neither index nor label is callable |

Legacy `interactableOnly`/`labelStyle` parameters do not change this experiment:
it uses numbered labels on the first bounded canonical entries, which can include
noninteractive semantic nodes. Label layout has not been visually accepted on
RTL, price/time text or the S24. Canvas draws on a copy, without a permanent page
overlay. Label collision avoidance does not guarantee preservation of all page text.

The geometry helper uses independent measured scales:

```text
imageX = (cssX - visualOffsetX) * imageScaleX - cropX
imageY = (cssY - visualOffsetY) * imageScaleY - cropY
```

The current fixture provider accepts only unit VisualViewport scale, zero offset
and matching viewport dimensions. Other zoom/crop/keyboard configurations return
`INVALID_CAPTURE_GEOMETRY`; the helper's general arithmetic is not evidence of
provider support. Bitmap allocation accounts for all concurrently retained
images, capped at the smaller of 32 MiB and one eighth of heap; dimensions are
bounded to 8,192 per side and encoded output to 8 MiB. Bitmaps are recycled in
`finally`, including cancellation/refusal. Device memory behavior is unmeasured.

## Proposed owner action / receipt contract: P3 blocked

No `tab.act`, KAI server, `expectedEffect` request field or new ActionReceipt
runtime is implemented. Canonical `tab.click`/`tab.type` keep their current
request schemas. `{version:1,ok:true,value:null}` acknowledges their legacy fixed
operation; it does not mean `EFFECT_VERIFIED`. `mayHaveCompleted:true` is an
uncertainty fence: observe and reconcile, never automatically repeat a mutation.

The mission's future `commandId`, operation, observation/element reference,
expected effect, native principal/consent generation, before/after observation
and `ADMITTED`/`DISPATCHED`/`EXECUTED`/`EFFECT_VERIFIED`/`UNCERTAIN`/`STALE`/
`BLOCKED` receipts remain a proposal to extend the existing R5 runtime after
dependency review. An enum in feedback sanitization does not implement that runtime.
Sort/Filter requires actual selected/chip/filter/order evidence. No generic DOM
fingerprint proves it. Unchanged window `scrollY` does not prove the end of a
modal, lazy/infinite/virtualized list or pagination. Current coverage stays partial.

## PR dependency and conflict review

These are exact G0 readbacks, not promises about later PR state. All three were
open drafts and unmerged. Their CI results do not validate this new branch.

| PR | Base branch / SHA | Head branch / SHA | Scope / dependency |
| --- | --- | --- | --- |
| [66](https://github.com/imanipour2/kelpie/pull/66) | `qa/r5-owner-friendly-background-optin-20261118` / `e9ee1617d7760e4f3032e008be0aad19489af7ad` | `feat/r5-mother-native-background-and-status-20261118` / `68ade958af66b7f6de219abd2d9f9011478f5a40` | 25 files against its QA base; main comparison diverged, ahead 518 / behind 1, 217 files. No blind cherry-pick |
| [67](https://github.com/imanipour2/kelpie/pull/67) | PR66 head / `68ade958af66b7f6de219abd2d9f9011478f5a40` | `docs/s24-tunnel-observability-rfc-20261118` / `0d33eb894383f8654d4feffe4fa22c4bbb64b802` | One RFC file; depends on 66, not a connection or runtime change |
| [68](https://github.com/imanipour2/kelpie/pull/68) | `main` / `91dadda8caebe69394e4a6b4589892ae4a495e54` | `docs/kai-agent-intelligence-reliability-20261118` / `07b49a14bf1e0ccf8c631147606ffb27225316c4` | Two proposal/mission files; documentation, not KAI implementation |

PR66 exact-head [Android run 44307266238](https://github.com/imanipour2/kelpie/actions/runs/44307266238)
was successful at readback; its other non-skipped checks were green. PR67's Lint
was successful but [macOS run 44352061174](https://github.com/imanipour2/kelpie/actions/runs/44352061174)
failed with its job canceled before steps. PR68's Lint was successful and
[macOS run 44404636621](https://github.com/imanipour2/kelpie/actions/runs/44404636621)
was queued. None is new-source CI or physical QA.

PR66 contains reachable conditional integration: `KelpieBrowserSession` builds
the R5 coordinator, and `R5MotherMcpCaptureAdapter.attachMotherMcpScreenshot`
attaches the existing HandlerContext path. It checks a verified native principal,
consent generation and private-display/tab ownership. `PrivateWebViewDisplay`
reuses the same WebView and requires compositor nonce evidence, one pending
frame and no stale cached fallback. `R5VerifiedFrameEncoder` bounds encoding and
recycles images, but does not provide sensitive-pixel redaction.

`R5NativeBrowserPort` and `R5WebViewActionScripts` need repair before reuse:
snapshot URL/body/metadata can expose private content, click's generic fingerprint
is not Sort/Filter verification, and window scrolling is not container/result
coverage. `AgentOperationRuntime` already has serialized execution, command IDs,
sequence handling, consent generation and receipts; extending it after review is
preferable to a second runtime. Those files are absent from this main baseline.
This branch does not import them or make the background provider reachable.

## Threat model and remaining boundaries

| Threat | Implemented mitigation | Residual / gate |
| --- | --- | --- |
| Values or URL secrets become observation names/text | Shared bounded redaction, structural selectors, native allowlists and rebuilt text | Known disclosure paths only; hostile arbitrary text/encoding remains untrusted |
| Secrets leave through PNG | All ordinary Android exports refuse; native debug fixture gate | Trusted privacy/frame provider required before real capture; no accepted masking |
| Stale index activates another element | Blind index actions refuse; original-object refs, TTL, mutation/signature/geometry checks | Renderer world is not isolated against a hostile page |
| Focus reentry, DOM replacement, duplicate action | Consume before focus/dispatch; recheck captured object and hit target; no automatic retry | No R5 command receipt/idempotency admission on this baseline |
| Page prompt injects native permission or disables STOP | No new page-defined authority or server; existing native authorization retained | Full principal/consent/STOP/keyguard action guarantees await R5 integration |
| False fresh-frame or result-completeness claim | Fixture-only unattested labels, explicit errors and `INCOMPLETE`/`UNKNOWN` coverage | No HOME/private-display/device freshness or task-completion acceptance |
| Durable QA/log leaks | Synthetic fixtures; no image artifacts; feedback allowlist; fixed failure messages | Do not run fixture evidence collection on real accounts or log request bodies |

Same-origin frames and open Shadow DOM are not claimed as supported native action
scopes. Cross-origin frames, canvas/custom/shadow surfaces are conservatively
opaque/unknown for image safety. No AccessibilityService, UIAutomator, persistent
ADB, public CDP or MediaProjection path is introduced.

## Local evidence and reproducibility

| ID | Executed check / evidence | Actual result and scope |
| --- | --- | --- |
| E1 | Root `pnpm build` and `pnpm lint` | **PASS** for package build/lint; not Android build, Android KtLint or device execution |
| E2 | Existing native `test_browser_gateway`, `test_mcp_registry`, `test_native_extensions`, compiled with g++ 14.2.0 / C++17 / assertions / `-Wall -Wextra` | **PASS**, no compiler warnings. Covers mocked native contracts, bounds, races and safe refusals; no WebView/native consent |
| E3 | Generated selector reader from `browser_snapshot_fixture --snapshot`, evaluated in JSDOM with explicit deterministic geometry | **PASS** for structural selector/metadata/redaction cases. DOM-only test; no browser layout, PNG or device proof |
| E4 | [`android-observation-privacy.mjs`](../../../tests/native/android-observation-privacy.mjs), extracting actual Kotlin-embedded scripts; JSDOM adapter | **PASS**, 22 fixture groups: privacy, blind-index refusal, diagnostic refusals, focus replacement/repurposing, one activation, sensitive/disabled/inert parent/child targets. Geometry is mocked |
| E5 | Shared unit tests in root `pnpm test` | **PASS**, 7 suites / 94 tests |
| E6 | Full root `pnpm test` | **FAIL** at this readback: CLI reports 50 suites, 661 passed tests and 2 failures (stream UTF-8 chunk handling, Windows stdio connection-closed), plus `networkInterfaces` EPERM. No baseline or regression cause is inferred |
| E7 | Real Chromium/Playwright launch | **BLOCKED**, process launch fails `socket() Operation not permitted`; browser fixtures collected but not executed locally |
| E8 | Android Gradle wrapper / native Android toolchain | **BLOCKED**, wrapper download fails `java.net.SocketException: Network is unreachable`; no full JDK, Gradle cache, Android SDK or local Android build evidence |
| E9 | `node --check` fixture scripts and `git diff --check` | **PASS** at source handoff; syntax/whitespace only |
| E10 | Physical S24 / Android 17 / installed package and signature | **BLOCKED**, no device access or current owner-consent readback |

Native compilation reused the repository's existing `nlohmann/json` dependency,
tag `v3.11.3`, verified SHA `9cca280a4d0ccf0c08f47a99aa71d1b0e52f8d03`; no new
runtime library was added. The permanent
[KAI reuse QA workflow](../../../.github/workflows/kai-reuse-qa.yml) regenerates the
canonical contract, runs package and real-browser fixtures and compiles the
existing gateway fixture with that pinned dependency. Its presence is not a
successful run. Exact new-source CI links must be read after PR checks execute.
Local C++ tests were not the complete CMake/Android/Apple integration build.

### T01–T14 acceptance matrix

`PASS` below is restricted to the named source check. Every physical S24 result
is **BLOCKED**; no source/mock result closes G4. The source evidence uses the
working tree described above, not the historical base or G0 documentation SHA.
No real account values or screenshots are stored as fixture evidence.

| Test | Expected result | Actual source result / fixture | Source status | S24 status |
| --- | --- | --- | --- | --- |
| T01 RTL controls | At least 12 Persian controls have distinct refs, correct roles/names/rects and readable labels | [`kai-observation.ts`](../../../packages/shared/tests/fixtures/kai-observation.ts) has 12 RTL controls; browser test prepared. E3 proves only smaller DOM structural/redaction checks; no rendered RTL/Canvas proof | **BLOCKED** for T01; E3 subset PASS | **BLOCKED** |
| T02 old identity | Reorder/banner/SPA/navigation/reload/tab change rejects old identity without wrong click | Native lease/tab/invalidation/race tests E2 PASS; E4 blind-index refusal PASS. Browser stale-document fixtures prepared but E7 blocked | **PASS** native/DOM subsets; integration **BLOCKED** | **BLOCKED** |
| T03 secrets | No synthetic password/OTP/card/file/hidden/ARIA/placeholder/URL secret in observation, image or log | E2/E3/E4 targeted JSON/text/selector redaction PASS. Default image refusal implemented; native gate/feedback JVM tests unrun; no PNG privacy acceptance | **PASS** targeted DOM/native subsets; image/JVM **BLOCKED** | **BLOCKED** |
| T04 ambiguity/occlusion | Duplicate, covered and off-viewport targets refuse explicitly | E2 explicit-error tests and E4 strict/disabled/inert target checks PASS. Real elementFromPoint/layout browser cases prepared | **PASS** mock/script subsets; layout **BLOCKED** | **BLOCKED** |
| T05 image coordinates | Native/viewport scale, zoom/rotation/keyboard/scroll/visual viewport map correctly or refuse | `ScreenshotGeometryTest` covers independent scales, inverse crop/offset, invalid values; five JVM tests including gate/budget prepared, unrun. Unsupported actual geometry refuses | **BLOCKED** | **BLOCKED** |
| T06 complex surfaces | Support only proven frame/shadow scopes; opaque cases report unknown | Canonical fixtures mark frames/custom/open/closed shadow/canvas opaque; no browser/device execution, no same-origin iframe/open-shadow action support claimed | **BLOCKED** | **BLOCKED** |
| T07 long/dynamic pages | More than 50 entries shows truncation; modal/infinite/virtual lists do not imply false end | Browser coverage fixture prepared; bounded metadata validation E2 PASS. No scroll-container/end-of-results implementation or real list test | **PASS** metadata subset; coverage integration **BLOCKED** | **BLOCKED** |
| T08 Sort/Filter | Actual selected/chip/filter/result-order effect in Persian and English | Native operation still dispatch-only; R5 effect-verification work absent; label/counter fixtures are not Sort/Filter proof | **BLOCKED** | **BLOCKED** |
| T09 unusable frames | Gray/old/missing renderer returns `NO_FRESH_FRAME`; no false success | Frame refusal codes preserved E2 PASS; uniform sampling/callback/race checks in fixture source, no Android pixel execution. Default real-page export denied | **PASS** error-forwarding subset; frame **BLOCKED** | **BLOCKED** |
| T10 revocation | Principal mismatch/revoke/STOP/keyguard stops capture/action before further mutation | E2 authorization/cancel/invalidation/uncertainty tests PASS; E4 sensitive-action refusal PASS. Real native owner runtime absent; no lock/STOP/device proof | **PASS** limited subsets; native lifecycle **BLOCKED** | **BLOCKED** |
| T11 background | Fresh same-WebView image after HOME with ChatGPT foreground, no focus theft | Current fixture requires foreground; returns `UNSUPPORTED_IN_BACKGROUND` after fixture admission. R5 provider is not integrated | **BLOCKED** | **BLOCKED** |
| T12 regression | Safe click/fill/select/scroll/tabs/capture, no double action, old/new MCP behavior explicit | E2 gateway/catalog compatibility PASS; E4 existing scripts/single action PASS. Intentional legacy image/index refusals documented; browser/native Android integration unrun | **PASS** native/DOM subsets; full regression **BLOCKED** | **BLOCKED** |
| T13 resources | TTL/cap/cancel/timeout/recycle; no leaks over at least 25 real lifecycle cycles | E2 TTL/cap/cancel PASS; source has finally recycling and aggregate bitmap budget. Timer/browser and JVM tests prepared; no heap profiling or 25 device cycles | **PASS** lease subset; device resources **BLOCKED** | **BLOCKED** |
| T14 final quality | Android build/KtLint/tests/security review/parity/rollback/flag/CI accepted | E1/E2/E5 PASS, E6 FAIL, E7/E8 BLOCKED. Independent source review and CI/device/parity still gated; default fixture flag off; rollback below | **FAIL / BLOCKED** at this readback | **BLOCKED** |

## Gate ledger, parity and handoff

| Gate | Actual state / remaining work |
| --- | --- |
| G0 | Inventory, threat/dependency map and minimum design recorded before source edits; fixture preparation exists |
| G1 | P0/P1 source repairs and bounded native/script checks implemented; real WebView, hostile-page isolation and native owner-session requirements remain unaccepted |
| G2 | Canvas source is an experimental synthetic-fixture path only; **not accepted**. Real image export remains blocked |
| G3 | **BLOCKED** on reviewed integration of the existing R5 owner/action runtime and semantic postconditions |
| G4 | **BLOCKED**: no present device identity/APK/signature/consent, HOME/STOP/lock/network/rotation or 25-cycle evidence |
| G5 | **BLOCKED**: full CI, code review, parity/exception and owner acceptance remain; merge/install/release independently gated |

The shared reader and native gateway affect common platforms, but that is not
Android/iOS feature parity. Android-specific refusal, privacy, capture and
feedback behavior has no reviewed matching iOS implementation in this branch;
Apple builds were not run here. Merge requires a corresponding implementation
and verification or a separate formal owner-approved architecture exception.
No such exception is asserted.

Historical [Issue 98](https://github.com/imanipour2/enerfoam-website/issues/98) and
the [connection RCA](https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit)
contain prior version-020 connection/image/owner-consent observations. They do
not attest the current installed state or this source branch. Earlier 25 captures
are not 25 complete HOME/resume/lock/network lifecycle cycles. No `DEVICE_QA_PASS`,
`OWNER_ACCEPTED`, `MAIN_MERGED`, `APK_INSTALLED` or `REMOTE_E2E_VERIFIED` is claimed.

Rollback is a source revert of the reviewed implementation commit on a development
branch, with the fixture flag kept false. No installed package or connection
setting was changed, so there is no device rollback action in this mission.
Re-enabling unsafe legacy behavior is not an acceptance shortcut. A future release
must separately decide how privacy-safe capture and compatible client handling
will replace these deliberate refusals.

## Technical references used for the boundary

- [Android WebView](https://developer.android.com/reference/android/webkit/WebView): existing renderer, visual-state callback and lifecycle API; callback availability does not supply privacy attestation.
- [Android View](https://developer.android.com/reference/android/view/View): owned-view geometry/drawing; no native touch prototype was justified by this mission's executed tests.
- [Android ImageReader](https://developer.android.com/reference/android/media/ImageReader): relevant to the existing R5 fresh-frame dependency, not a new capture service here.
- [AndroidX WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat) and [WebViewFeature](https://developer.android.com/reference/androidx/webkit/WebViewFeature): any future optional provider facility requires actual runtime support checks; no feature support is assumed for the S24.
- [KMPwright pinned input reference](https://github.com/bernaferrari/kmpwright/blob/3cf13b9eb83d75753fc06f8f6f368ae2dbf74492/kmpwright-core/src/androidMain/kotlin/com/bernaferrari/kmpwright/core/Input.kt): reviewed MIT reference, not copied or integrated; P4 remains deferred.
- [Browser Use](https://github.com/browser-use/browser-use/tree/ccbdb8095fbaf903c748880a600054a34c7b6191): reviewed MIT reference for observation/action concepts; no external engine, dependency or code imported.

Repository [AGENTS.md](../../../AGENTS.md), [CLAUDE.md](../../../CLAUDE.md),
[core README](../../../core/README.md) and
[development/release policy](../../policies/development-and-release.md) govern
source quality and later integration gates. Newness or a proposed title does not
supersede a recorded owner approval or demonstrate a running implementation.

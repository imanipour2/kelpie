# R5 native agent-control remediation — private QA only (2026-11-17)

## Basis / evidence
- Base: draft PR #34, exact SHA `2f5c1701e8f7d651fdcd8189eaeec02c5b2190a2`.
- Device QA reference: https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6935540494
- 48 commands: 26 verified effects, 15 adapter failures, 6 admission rejections, 1 invalid screenshot payload HTTP400.
- Full 2000 genuine native frame test and native STOP readback recorded in issue #98.
- This branch has NOT been rebuilt or retested on Samsung; it is not an APK or public beta.

## Changes
1. Allow normal agent-directed buttons and HTML forms instead of blanket rejecting every button. Native script still rejects password, OTP and payment/card/bank-related click targets. Real user-supplied JavaScript is never evaluated.
2. A click is **not** reported verified because JavaScript returned DISPATCHED: require either two stable changed native URL/document observations, or stable DOM/control-state fingerprint changes followed by fresh independent confirmation. A click with no observable effect is **UNCERTAIN**, never automatically retried.
3. Add HTML SELECT by value/visible text and independently verify chosen option. Add bounded WAIT for document completion or visible CSS selector.
4. Routine PUBLISH and IRREVERSIBLE_DELETE operation enums are no longer denied at admission solely due to their names. They remain unimplemented native commands; HTTP200 is not success. Agent controls whether to request ordinary actions. PAYMENT and EXPORT_CREDENTIALS remain denied.

## Security and behavior
- Owner native session, authenticated principal, consent generation, STOP, lock, native renderer/tab identity and document epoch remain enforced.
- Password, credit-card autofill, OTP entry remain denied in FILL/TYPE, regardless of agent instruction.
- Sensitive form and payment detectors are a defense in depth; semantic detection is **not** a comprehensive financial safety guarantee. A future dedicated typed payment barrier at native and network action boundaries is needed before beta.
- DOM fingerprint hashes text and non-secret UI state in process; it is not persisted or presented as proof of a particular causal target and is not cryptographically collision-proof. SPA false-positive risk remains.
- Website text and prompt injection are untrusted; connected agent must apply its own policy.
- No new USB ADB input, no bypass of consent, and no WordPress changes.

## Remaining blockers and release gate
P0: OPEN_TAB, CLOSE_TAB, SWITCH_TAB, LIST_TABS command enum, BACK, FORWARD remain native-port incomplete; safe private-display ownership transition must be designed and tested instead of switching WebViews while a previous one is attached to the Presentation. Dedicated `agent-tabs` can list tabs.
P0: Direct ChatGPT Kelpie connector returns FORBIDDEN despite healthy local tunnel. Need provider contract / connector mapping access and a versioned authenticated adapter. Do not enable legacy endpoints.
P0: Android compile/ktlint, exact-head CI, hardware regression (buttons/SELECT/WAIT, ordinary form, STOP during click, same-document mutation, actual payment block), 24h Doze/battery and signing remain pending.
P1: Shadow DOM, cross-origin iframe, true native/IME input, file input/upload, downloads, network request observation, accessibility tree, coordinate-based click, full-page visual capture, job orchestration need scoped designs.
P1: Cloud-browser/TinyFish anti-bot infrastructure, hosted session profiles, parallel scaling and managed CAPTCHA cannot be honestly considered implemented by a private on-device WebView.

Status: SOURCE_PATCH_ONLY / CI_PENDING / DEVICE_TEST_PENDING / DIRECT_MCP_FORBIDDEN / BETA_NO_GO. No merge to main.
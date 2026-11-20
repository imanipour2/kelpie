# Mother computer approval: connection-policy candidate

Status: source candidate prepared for CI; no installed-device acceptance is claimed.

## Scope and defect

The mother Android profile combines upstream durable computer pairing with a
private-renderer execution adapter. The previous adapter also required a second
process-local native grant. Activity teardown, recent-client observation expiry,
and renderer failure could make a still-paired computer appear unauthorized.

This candidate restores durable computer approval as the source of connection
authority. Runtime epochs, rendering readiness, explicit STOP, and lock state
continue to protect execution. The WebView rendering implementation, visual
proof requirements, capture timeout, installed application, ports, and pairing
keys are outside this source-only change. A draft or a passing build does not
authorize a merge, release, or installation.

The source baseline is
[`f798d3699db7ffa4228e6f722ab332c73c8d90ee`](https://github.com/imanipour2/kelpie/commit/f798d3699db7ffa4228e6f722ab332c73c8d90ee),
from [Draft PR 72](https://github.com/imanipour2/kelpie/pull/72).

## Upstream contract consulted

The official
[computer-pairing contract](https://github.com/UnlikeOtherAI/kelpie/blob/main/docs/api/computer-pairing.md)
was read on 20 November 2026, blob
`f33e36727d86da8a11220b187ed4c534cdeb5154`.
The corresponding
[Android middleware](https://github.com/UnlikeOtherAI/kelpie/blob/main/core/android/src/main/java/com/kelpie/browser/network/AuthMiddleware.kt)
uses durable computer permission. A proven Ed25519 key receives one native
approval. Its trust and valid bearers survive app restarts and address changes;
the same key can obtain a replacement bearer without another approval.
Revocation removes that key and its bearer hashes. Legacy bearers and temporary
account grants retain their separate scope.

The implementation uses the actual native `computerKey` discriminator plus
current trusted-computer membership. It does not infer durable permission from
a client ID, a recent observation, or generic localhost admission. Existing R5
bearer safeguards are retained.

Each restored execution epoch also retains its native computer-proof origin in
memory. A same-named legacy/account bearer cannot inherit that epoch, read its
generation, stop it, or deliver its results. Upgrading an explicitly approved
legacy epoch with verified computer authority rotates the generation and retires
in-flight legacy work. This introduces no additional owner prompt.

## Connection contract

| Event | Candidate behavior |
| --- | --- |
| First proven computer key | Existing upstream native approval |
| Same approved computer reconnects | Reuse durable trust; no second approval |
| App or Activity recreation | Rebuild execution state from current durable authority |
| Old recent-agent observation | No expiry of established computer permission |
| Renderer unavailable before execution | Report readiness separately; keep computer approval |
| Authenticated static metadata | Serve bounded compiled metadata without a renderer or new session approval |
| Phone locks | Suspend protected execution and invalidate admitted old work |
| Explicit STOP | Remain paused across reconnect/recreation until native Resume |
| Key revoked or bearer invalid | Reject execution and stale response delivery |
| Temporary account or legacy grant | Preserve its original scope |
| Legacy session request from a durable computer | Report already approved, or native Resume required after STOP; create no duplicate dialog |

The execution generation and safety epoch protect in-flight work. They are not
another user-facing approval. A request cannot change principals, revive a
revoked key, clear STOP, or reuse work admitted before a lock transition.

An epoch change during an already admitted request can still trigger the
existing `SESSION_REVOKED` stale-delivery fence. That fence does not delete the
durable computer key or require pairing again. Another trusted computer may
receive `CONTROLLER_BUSY` while a different computer owns the runtime. This
candidate does not introduce a multi-controller mechanism.

STOP first pauses the current process, then commits the saved pause. If the
storage write fails, the native UI reports the failure and the process remains
paused. Persistence across process death cannot be guaranteed after a failed
write; native feedback asks the owner to retry STOP before restarting the app.

## Verification

The candidate adds 30 JUnit cases:

- 12 trusted-session continuity cases.
- 5 bounded static-metadata admission cases.
- 12 authenticated dispatcher and compatibility-route cases.
- 1 actual middleware discriminator case.

One native gateway regression covers metadata without browser dispatch,
readiness mapping for reads and mutations, mutation uncertainty, and diagnostic
redaction. Independent source review and `git diff --check` are clean. Tests
have not been executed locally; CI must validate the exact candidate commit.

The existing Mother Sidecar CI uses JDK 17, Android SDK 34, build tools 35,
ktlint, Kotlin unit tests, Android test compilation, and APK assembly:

```bash
cd apps/android
./gradlew --no-daemon -PkelpieR5MotherNextQaProfile=true \
  :kelpie-browser:ktlintCheck \
  :kelpie-browser:testDebugUnitTest \
  :kelpie-browser:compileDebugAndroidTestKotlin \
  :app:assembleDebug
```

Existing host-native CI validates the browser gateway. No test gate is waived
or replaced by source review. The analysis workspace lacks a complete JDK,
Android SDK, and native CMake/CTest toolchain.

Hardware acceptance remains separate: pairing, foreground/background actions,
reconnect/recreation, lock/unlock, STOP/Resume/revocation, and reboot require
actual observation. Source tests do not establish successful private rendering
or fresh screenshot delivery. The underlying rendering defect remains open.

The coordinator already exceeded the repository's 500-line guideline at the
baseline (567 lines); it is now 587 lines. A broader renderer refactor is outside
this connection-policy candidate. This existing size debt remains explicit.

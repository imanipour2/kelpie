# Consented capture request registry — pending authorization and local approval (DRAFT)

**Status:** source policy under test only; not connected to screenshots, not exposed as an HTTP endpoint, not shipped.

This stacked branch builds on [PR #7](https://github.com/imanipour2/kelpie/pull/7) and formalizes the missing boundary between an authenticated incoming request and a *fresh local user gesture*. It addresses the high-risk failure mode in which a client could forge a JSON body clientId or reuse a prior capture consent.

**Flow (future integration):**

1. `AuthMiddleware` verifies pairing/bearer and places immutable `AuthenticatedClientContext` on the request coroutine. The native gateway calls `verifiedClientId()`, not a JSON body field.
2. A client requests a capture attempt for a concrete, **existing** active `TabStore` tab/document/WebView. The `ConsentedCaptureRequestRegistry` holds a single pending request with random 192-bit ID for **30 seconds**; it refuses unauthenticated or malformed client/attempt/tab/document IDs, and rejects concurrent request overwrites.
3. The on-device Kelpie native modal (not yet implemented) displays the authenticated client, exact tab and request purpose. Only clicking its affirmative action calls `approveOnDevice` with the current exact tab/document/Java WebView identity and device unlocked state; any mismatch clears all pending authority.
4. `approveOnDevice` creates a separate local-only `ConsentedCaptureLease` with the existing 90-second and frame-budget limits. Its token never appears in remote HTTP/MCP responses or logs.
5. Every future background screenshot handler will validate actual authenticated client, request ID, agent attempt, tab ID/document, identical WebView, unlocked state and STOP via `claimFrame` before any rendering/encoding. On a mismatch it revokes all authority. The renderer itself still requires explicit privacy review and valid Android lifecycle/Compose safeguards.

This source change carries **no screenshots, browser network routes, UI button, manifest permissions or real private-session access**; it is deliberately designed to be integrated only after review and real-device negative tests. The user may revoke from a future persistent STOP UI at any time.

**New JVM tests:** no authenticated principal, invalid identifiers, pending overwrite, wrong tab/document/view, lock, STOP, wrong request ID, wrong client or attempt, pending TTL and lease expiry. Independent Apple platform parity assessment is required; this registry uses Android's StateFlow for prompt presentation while the core consent lease already has an Apple counterpart.

**Release gates:** integrated authenticated transport and native user UI, same-WebView private surface renderer, negative hardware tests, privacy/legal disclosure, Android 16/17 compatibility, stable release signature and owner CDG approval. No real-page capture or release while Draft.

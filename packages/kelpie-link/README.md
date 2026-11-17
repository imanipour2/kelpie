# Kelpie Link private rendezvous primitive

**Development-only security primitive; not a server, plugin, QR UI, OAuth provider, Android app update or authorization grant.**

The owner-approved architecture is [ADR-R5-006](../../docs/adr/ADR-R5-006-kelpie-link-onboarding-remote-mcp.md) and the implementation is tracked by [draft PR57](https://github.com/imanipour2/kelpie/pull/57).

This package is intentionally a small, dependency-free **fail-closed** building block for eventual secure one-use QR/App Link pairing. It creates 256-bit random challenges, binds them to an owner/device/provider/client tuple, expires them in at most three minutes (90 seconds default), rejects cross-principal claims, prevents replay, allows owner-wide revocation and never issues browser authorization. Claims return **PENDING_NATIVE_CONFIRMATION**, not `APPROVED`.

`ownerId`, `deviceId`, `provider`, and `clientId` are **untrusted inputs unless already authenticated by the future gateway**. This component does not itself prove their identity, does not authenticate a request and must NEVER be exposed directly as an anonymous public HTTP endpoint. The eventual approved gateway must enforce OAuth 2.1 client identity and audience, replay rate limits, device-key proof, challenge signing/routing, native owner pairing, separate process-session consent, STOP and auditable revocation. QR challenge is a rendezvous locator only, not a long-lived pairing bearer.

Use `node --test packages/kelpie-link/test/*.test.mjs` (Node20+) for deterministic expiration/replay/identity tests. No secrets or external network needed.

**Do not deploy or wire this into production** until the authorized backend, account model, mobile UI, transport, signatures and S24 physical test gates have passed. No paid hosting was provisioned.

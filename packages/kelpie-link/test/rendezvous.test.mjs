import test from "node:test";
import assert from "node:assert/strict";
import { RendezvousRegistry } from "../src/rendezvous.mjs";

const alice = {
  ownerId: "verified-owner",
  deviceId: "phone-s24",
  provider: "chatgpt",
  clientId: "verified-chatgpt-client",
};

test("a challenge is high entropy, short lived, and never grants a session", () => {
  let now = 100_000;
  const registry = new RendezvousRegistry({ now: () => now });
  const issued = registry.issue(alice);
  assert.equal(issued.status, "ISSUED");
  assert.match(issued.challenge, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(issued.expiresAt, now + 90_000);
  assert.equal(issued.token, undefined);
  assert.equal(issued.grant, undefined);
  assert.equal(registry.claim(issued.challenge, alice).status, "PENDING_NATIVE_CONFIRMATION");
  assert.equal(registry.claim(issued.challenge, alice).status, "REJECTED");
  now += 90_000;
  assert.equal(registry.pendingCount, 0);
});

test("cross-owner, cross-device, cross-provider and cross-client claims fail closed", () => {
  const registry = new RendezvousRegistry();
  const issued = registry.issue(alice);
  for (const field of ["ownerId", "deviceId", "provider", "clientId"]) {
    const other = { ...alice, [field]: alice[field] + "-attacker" };
    assert.equal(registry.claim(issued.challenge, other).status, "REJECTED");
  }
  assert.equal(registry.claim(issued.challenge, alice).status, "PENDING_NATIVE_CONFIRMATION");
});

test("an expired or time-reversed challenge cannot be claimed", () => {
  let now = 200_000;
  const registry = new RendezvousRegistry({ now: () => now });
  const issued = registry.issue(alice);
  now += 90_000;
  assert.equal(registry.claim(issued.challenge, alice).status, "REJECTED");
  now = 400_000;
  const issued2 = registry.issue(alice);
  now = 399_999;
  assert.equal(registry.claim(issued2.challenge, alice).status, "REJECTED");
});

test("owner revocation clears pending QR claims before they can be consumed", () => {
  const registry = new RendezvousRegistry();
  const issued = registry.issue(alice);
  registry.revokeOwner("verified-owner");
  assert.equal(registry.pendingCount, 0);
  assert.equal(registry.claim(issued.challenge, alice).status, "REJECTED");
});

test("bounded capacity fails closed and invalid challenges never consume", () => {
  const registry = new RendezvousRegistry({ capacity: 1 });
  const issued = registry.issue(alice);
  assert.equal(registry.issue(alice).status, "CAPACITY");
  assert.equal(registry.claim("../malformed", alice).status, "REJECTED");
  assert.equal(registry.claim(issued.challenge, alice).status, "PENDING_NATIVE_CONFIRMATION");
});

test("weak randomness is not accepted and identity inputs are validated", () => {
  const weak = new RendezvousRegistry({ entropy: () => Buffer.alloc(8) });
  assert.throws(() => weak.issue(alice), /secure 256-bit/);
  const registry = new RendezvousRegistry();
  assert.throws(() => registry.issue({ ...alice, ownerId: "unauthorized\nowner" }));
  assert.throws(() => registry.issue({ ...alice, provider: "" }));
  assert.throws(() => registry.issue(null));
});

test("nonce collisions never overwrite an existing pending rendezvous", () => {
  const registry = new RendezvousRegistry({ entropy: () => Buffer.alloc(32, 7) });
  assert.equal(registry.issue(alice).status, "ISSUED");
  assert.equal(registry.issue(alice).status, "COLLISION");
  assert.equal(registry.pendingCount, 1);
});

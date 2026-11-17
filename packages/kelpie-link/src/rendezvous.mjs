import { createHash, randomBytes } from "node:crypto";

/**
 * Ephemeral QR/rendezvous challenge storage — no network endpoint, bearer
 * grant, pairing approval or browser-control authority is implemented here.
 *
 * ALL caller-supplied identities must already be authenticated and attested
 * by an independent trusted gateway. A successful claim only means that the
 * same identity tuple has seen a fresh one-time challenge; the phone must
 * still perform its separate native owner pairing + per-process consent.
 */
export class RendezvousRegistry {
  #pending = new Map();
  #now;
  #entropy;
  #ttlMs;
  #capacity;

  constructor({
    now = Date.now,
    entropy = randomBytes,
    ttlMs = 90_000,
    capacity = 512,
  } = {}) {
    if (
      typeof now !== "function" ||
      typeof entropy !== "function" ||
      !Number.isSafeInteger(ttlMs) ||
      ttlMs < 1_000 ||
      ttlMs > 180_000 ||
      !Number.isSafeInteger(capacity) ||
      capacity < 1 ||
      capacity > 16_384
    ) {
      throw new TypeError("Invalid rendezvous policy");
    }
    this.#now = now;
    this.#entropy = entropy;
    this.#ttlMs = ttlMs;
    this.#capacity = capacity;
  }

  issue(identity) {
    const parties = validatedIdentity(identity);
    const now = this.#now();
    this.#prune(now);
    if (!Number.isSafeInteger(now) || now < 0) {
      return Object.freeze({ status: "UNAVAILABLE" });
    }
    if (this.#pending.size >= this.#capacity) {
      return Object.freeze({ status: "CAPACITY" });
    }
    const bytes = this.#entropy(32);
    if (!Buffer.isBuffer(bytes) || bytes.length !== 32) {
      throw new TypeError("A cryptographically secure 256-bit random source is required");
    }
    const challenge = bytes.toString("base64url");
    const key = digest(challenge);
    if (this.#pending.has(key)) {
      return Object.freeze({ status: "COLLISION" });
    }
    const expiresAt = now + this.#ttlMs;
    if (!Number.isSafeInteger(expiresAt)) {
      return Object.freeze({ status: "UNAVAILABLE" });
    }
    this.#pending.set(key, { parties, createdAt: now, expiresAt, claimed: false });
    // Challenge is a rendezvous locator, not an auth token or a consent grant.
    return Object.freeze({ status: "ISSUED", challenge, expiresAt });
  }

  claim(challenge, identity) {
    const parties = validatedIdentity(identity);
    if (!isChallenge(challenge)) return Object.freeze({ status: "REJECTED" });
    const now = this.#now();
    this.#prune(now);
    const slot = this.#pending.get(digest(challenge));
    if (
      !slot ||
      !Number.isSafeInteger(now) ||
      now < slot.createdAt ||
      now >= slot.expiresAt ||
      slot.claimed ||
      !sameIdentity(slot.parties, parties)
    ) {
      return Object.freeze({ status: "REJECTED" });
    }
    // Prevent reuse immediately, even if the external pairing flow fails.
    slot.claimed = true;
    return Object.freeze({ status: "PENDING_NATIVE_CONFIRMATION" });
  }

  revokeOwner(ownerId) {
    const owner = validatedField(ownerId);
    for (const [key, slot] of this.#pending) {
      if (slot.parties.ownerId === owner) this.#pending.delete(key);
    }
  }

  get pendingCount() {
    this.#prune(this.#now());
    return this.#pending.size;
  }

  #prune(now) {
    for (const [key, slot] of this.#pending) {
      if (!Number.isSafeInteger(now) || now < slot.createdAt || now >= slot.expiresAt) {
        this.#pending.delete(key);
      }
    }
  }
}

function isChallenge(challenge) {
  return (
    typeof challenge === "string" &&
    /^[A-Za-z0-9_-]{43}$/.test(challenge) &&
    Buffer.from(challenge, "base64url").length === 32
  );
}

function validatedField(value) {
  if (
    typeof value !== "string" ||
    value.length < 1 ||
    value.length > 128 ||
    /[\u0000-\u001f\u007f]/.test(value) ||
    value.trim() !== value
  ) {
    throw new TypeError("Authenticated identity field required");
  }
  return value;
}

function validatedIdentity(value) {
  if (value == null || typeof value !== "object") {
    throw new TypeError("Authenticated tuple required");
  }
  return Object.freeze({
    ownerId: validatedField(value.ownerId),
    deviceId: validatedField(value.deviceId),
    provider: validatedField(value.provider),
    clientId: validatedField(value.clientId),
  });
}

function sameIdentity(a, b) {
  return (
    a.ownerId === b.ownerId &&
    a.deviceId === b.deviceId &&
    a.provider === b.provider &&
    a.clientId === b.clientId
  );
}

function digest(challenge) {
  return createHash("sha256").update(challenge, "utf8").digest("hex");
}

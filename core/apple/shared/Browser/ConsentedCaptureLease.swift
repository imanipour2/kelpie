import Foundation
import Security

/// Identical consent-scoped capture authority on Apple platforms.
/// Only an explicit native UI tap may approve a grant; remote requests must not.
@MainActor
final class ConsentedCaptureLease {
    struct Binding: Equatable {
        let tabId: String
        let documentVersion: String
        let clientId: String
        let agentAttemptId: String

        var isWellFormed: Bool {
            [tabId, documentVersion, clientId, agentAttemptId].allSatisfy {
                !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
        }
    }

    private struct Grant {
        let token: String
        let binding: Binding
        let view: AnyObject
        let expiresAtMs: Int64
        let maxFrames: Int
        var used: Int = 0
    }

    static let maxLeaseMs: Int64 = 90_000
    static let defaultFrameLimit = 20
    static let maxFrameLimit = 240

    private let uptimeMs: () -> Int64
    private var grant: Grant?

    init(uptimeMs: @escaping () -> Int64 = {
        Int64(ProcessInfo.processInfo.systemUptime * 1_000)
    }) {
        self.uptimeMs = uptimeMs
    }

    /// UI-only entry point. Never automatically renew from an HTTP/MCP call.
    @discardableResult
    func approveLocally(
        binding: Binding,
        view: AnyObject,
        durationMs: Int64 = maxLeaseMs,
        maxFrames: Int = defaultFrameLimit
    ) -> String? {
        revoke()
        guard binding.isWellFormed,
              (1...Self.maxLeaseMs).contains(durationMs),
              (1...Self.maxFrameLimit).contains(maxFrames),
              let token = Self.randomToken() else { return nil }
        let now = uptimeMs()
        guard now >= 0, now <= Int64.max - durationMs else { return nil }
        grant = Grant(
            token: token, binding: binding, view: view,
            expiresAtMs: now + durationMs, maxFrames: maxFrames
        )
        return token
    }

    /// Fail closed and revoke on any invalid token, identity, lock, STOP or expiry.
    func claimFrame(
        token: String?,
        current: Binding,
        view: AnyObject,
        authenticatedClient: Bool,
        deviceUnlocked: Bool,
        userStopped: Bool
    ) -> Bool {
        guard var currentGrant = grant else { return false }
        let approved =
            token.map { Self.constantTimeEquals(currentGrant.token, $0) } == true &&
            currentGrant.binding == current &&
            currentGrant.view === view &&
            authenticatedClient && deviceUnlocked && !userStopped &&
            uptimeMs() < currentGrant.expiresAtMs &&
            currentGrant.used < currentGrant.maxFrames
        guard approved else {
            revoke()
            return false
        }
        currentGrant.used += 1
        grant = currentGrant
        return true
    }

    func revoke() {
        grant = nil
    }

    private static func randomToken() -> String? {
        var bytes = [UInt8](repeating: 0, count: 24)
        let status = bytes.withUnsafeMutableBytes { ptr in
            SecRandomCopyBytes(kSecRandomDefault, ptr.count, ptr.baseAddress!)
        }
        guard status == errSecSuccess else { return nil }
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    private static func constantTimeEquals(_ a: String, _ b: String) -> Bool {
        let x = Array(a.utf8), y = Array(b.utf8)
        guard x.count == y.count else { return false }
        var difference: UInt8 = 0
        for i in x.indices {
            difference |= x[i] ^ y[i]
        }
        return difference == 0
    }
}

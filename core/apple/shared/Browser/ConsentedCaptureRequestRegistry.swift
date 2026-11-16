import Foundation
import Security

/// UI-owned inbox for requests admitted by the actual native client middleware.
/// No screenshot bytes, remote authority bypass, or persistent grants.
@MainActor
final class ConsentedCaptureRequestRegistry {
    struct Prompt: Equatable {
        let requestId: String
        let clientId: String
        let attemptId: String
        let tabId: String
        let documentVersion: String
        let expiresAtMs: Int64
    }

    private struct Pending {
        let prompt: Prompt
        let view: AnyObject
    }

    private struct Approved {
        let requestId: String
        let binding: ConsentedCaptureLease.Binding
        let view: AnyObject
        let localToken: String
    }

    static let pendingTtlMs: Int64 = 30_000
    static let maxIdLength = 128

    private let nowMs: () -> Int64
    private let lease: ConsentedCaptureLease
    private var pending: Pending?
    private var approved: Approved?
    private(set) var prompt: Prompt?

    init(nowMs: @escaping () -> Int64 = {
        Int64(ProcessInfo.processInfo.systemUptime * 1_000)
    }) {
        self.nowMs = nowMs
        self.lease = ConsentedCaptureLease(uptimeMs: nowMs)
    }

    /// Call only with the client ID returned by verified bearer/account auth.
    func submitAuthenticated(
        verifiedClientId: String?,
        attemptId: String,
        tabId: String,
        documentVersion: String,
        view: AnyObject
    ) -> Prompt? {
        if let previous = pending, nowMs() >= previous.prompt.expiresAtMs {
            revoke()
        }
        guard pending == nil, approved == nil, let client = verifiedClientId,
              Self.validId(client), Self.validId(attemptId),
              Self.validId(tabId), Self.validId(documentVersion) else { return nil }
        let now = nowMs()
        guard now >= 0, now <= Int64.max - Self.pendingTtlMs,
              let requestId = Self.newRequestId() else { return nil }
        let next = Prompt(
            requestId: requestId,
            clientId: client,
            attemptId: attemptId,
            tabId: tabId,
            documentVersion: documentVersion,
            expiresAtMs: now + Self.pendingTtlMs
        )
        pending = Pending(prompt: next, view: view)
        prompt = next
        return next
    }

    /// Must only be called from the person's affirmative native UI gesture.
    func approveOnDevice(
        requestId: String,
        currentTabId: String,
        currentDocumentVersion: String,
        currentView: AnyObject,
        unlocked: Bool
    ) -> Bool {
        guard let next = pending, next.prompt.requestId == requestId,
              nowMs() < next.prompt.expiresAtMs, unlocked,
              next.prompt.tabId == currentTabId,
              next.prompt.documentVersion == currentDocumentVersion,
              next.view === currentView else {
            revoke()
            return false
        }
        let binding = ConsentedCaptureLease.Binding(
            tabId: next.prompt.tabId,
            documentVersion: next.prompt.documentVersion,
            clientId: next.prompt.clientId,
            agentAttemptId: next.prompt.attemptId
        )
        guard let token = lease.approveLocally(binding: binding, view: currentView) else {
            revoke()
            return false
        }
        approved = Approved(
            requestId: requestId, binding: binding, view: currentView, localToken: token
        )
        pending = nil
        prompt = nil
        return true
    }

    func claimFrame(
        requestId: String,
        verifiedClientId: String?,
        attemptId: String,
        currentTabId: String,
        currentDocumentVersion: String,
        currentView: AnyObject,
        unlocked: Bool,
        userStopped: Bool
    ) -> Bool {
        guard let current = approved, current.requestId == requestId,
              let client = verifiedClientId, client == current.binding.clientId,
              attemptId == current.binding.agentAttemptId else {
            revoke()
            return false
        }
        let scope = ConsentedCaptureLease.Binding(
            tabId: currentTabId,
            documentVersion: currentDocumentVersion,
            clientId: client,
            agentAttemptId: attemptId
        )
        let accepted = lease.claimFrame(
            token: current.localToken,
            current: scope,
            view: currentView,
            authenticatedClient: true,
            deviceUnlocked: unlocked,
            userStopped: userStopped
        )
        if !accepted { revoke() }
        return accepted
    }

    func revoke() {
        pending = nil
        approved = nil
        prompt = nil
        lease.revoke()
    }

    private static func validId(_ text: String) -> Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            text.count <= maxIdLength &&
            text.unicodeScalars.allSatisfy { !CharacterSet.controlCharacters.contains($0) }
    }

    private static func newRequestId() -> String? {
        var bytes = [UInt8](repeating: 0, count: 24)
        let status = bytes.withUnsafeMutableBytes { pointer in
            guard let address = pointer.baseAddress else { return errSecParam }
            return SecRandomCopyBytes(kSecRandomDefault, pointer.count, address)
        }
        guard status == errSecSuccess else { return nil }
        return bytes.map { String(format: "%02x", $0) }.joined()
    }
}

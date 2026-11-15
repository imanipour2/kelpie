import XCTest
@testable import KelpieBrowser

@MainActor
final class ConsentedCaptureLeaseTests: XCTestCase {
    private final class WebViewIdentity {}

    private func binding() -> ConsentedCaptureLease.Binding {
        .init(tabId: "tab", documentVersion: "doc", clientId: "client", agentAttemptId: "attempt")
    }

    func testNoConsentAndFrameBudget() {
        var now: Int64 = 1_000
        let lease = ConsentedCaptureLease(uptimeMs: { now })
        let view = WebViewIdentity()
        let binding = binding()
        XCTAssertFalse(lease.claimFrame(token: "made-up", current: binding, view: view,
                                        authenticatedClient: true, deviceUnlocked: true, userStopped: false))
        let token = lease.approveLocally(binding: binding, view: view, durationMs: 10, maxFrames: 2)
        XCTAssertEqual(token?.count, 48)
        XCTAssertTrue(lease.claimFrame(token: token, current: binding, view: view,
                                       authenticatedClient: true, deviceUnlocked: true, userStopped: false))
        XCTAssertTrue(lease.claimFrame(token: token, current: binding, view: view,
                                       authenticatedClient: true, deviceUnlocked: true, userStopped: false))
        XCTAssertFalse(lease.claimFrame(token: token, current: binding, view: view,
                                        authenticatedClient: true, deviceUnlocked: true, userStopped: false))
        now += 10
        XCTAssertFalse(lease.claimFrame(token: token, current: binding, view: view,
                                        authenticatedClient: true, deviceUnlocked: true, userStopped: false))
    }

    func testWrongClientDocumentAndWebViewFailClosed() {
        let lease = ConsentedCaptureLease(uptimeMs: { 123 })
        let view = WebViewIdentity()
        let secondView = WebViewIdentity()
        let b = binding()
        var token = lease.approveLocally(binding: b, view: view)
        let other = ConsentedCaptureLease.Binding(
            tabId: b.tabId, documentVersion: b.documentVersion,
            clientId: "replacement", agentAttemptId: b.agentAttemptId
        )
        XCTAssertFalse(lease.claimFrame(token: token, current: other, view: view,
                                        authenticatedClient: true, deviceUnlocked: true, userStopped: false))
        token = lease.approveLocally(binding: b, view: view)
        XCTAssertFalse(lease.claimFrame(token: token, current: b, view: secondView,
                                        authenticatedClient: true, deviceUnlocked: true, userStopped: false))
        token = lease.approveLocally(binding: b, view: view)
        let changed = ConsentedCaptureLease.Binding(
            tabId: b.tabId, documentVersion: "changed",
            clientId: b.clientId, agentAttemptId: b.agentAttemptId
        )
        XCTAssertFalse(lease.claimFrame(token: token, current: changed, view: view,
                                        authenticatedClient: true, deviceUnlocked: true, userStopped: false))
    }

    func testLockStopAndUnauthenticatedAccess() {
        let lease = ConsentedCaptureLease(uptimeMs: { 500 })
        let view = WebViewIdentity()
        let b = binding()
        var token = lease.approveLocally(binding: b, view: view)
        XCTAssertFalse(lease.claimFrame(token: token, current: b, view: view,
                                        authenticatedClient: false, deviceUnlocked: true, userStopped: false))
        token = lease.approveLocally(binding: b, view: view)
        XCTAssertFalse(lease.claimFrame(token: token, current: b, view: view,
                                        authenticatedClient: true, deviceUnlocked: false, userStopped: false))
        token = lease.approveLocally(binding: b, view: view)
        XCTAssertFalse(lease.claimFrame(token: token, current: b, view: view,
                                        authenticatedClient: true, deviceUnlocked: true, userStopped: true))
    }

    func testInvalidGrantsAndRevocation() {
        let lease = ConsentedCaptureLease(uptimeMs: { 100 })
        let view = WebViewIdentity()
        let b = binding()
        XCTAssertNil(lease.approveLocally(binding: b, view: view, durationMs: 90_001))
        XCTAssertNil(lease.approveLocally(binding: b, view: view, maxFrames: 241))
        let token = lease.approveLocally(binding: b, view: view)
        lease.revoke()
        XCTAssertFalse(lease.claimFrame(token: token, current: b, view: view,
                                        authenticatedClient: true, deviceUnlocked: true, userStopped: false))
    }
}

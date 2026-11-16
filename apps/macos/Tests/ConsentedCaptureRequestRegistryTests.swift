import XCTest
@testable import KelpieBrowser

@MainActor
final class ConsentedCaptureRequestRegistryTests: XCTestCase {
    private final class WebViewIdentity {}

    func testRequiresVerifiedClientAndFreshLocalApproval() {
        let view = WebViewIdentity()
        let inbox = ConsentedCaptureRequestRegistry(nowMs: { 100 })
        XCTAssertNil(
            inbox.submitAuthenticated(
                verifiedClientId: nil,
                attemptId: "attempt",
                tabId: "tab",
                documentVersion: "doc",
                view: view
            )
        )
        let pending = inbox.submitAuthenticated(
            verifiedClientId: "client",
            attemptId: "attempt",
            tabId: "tab",
            documentVersion: "doc",
            view: view
        )
        guard let requestId = pending?.requestId else {
            XCTFail("Pending request missing")
            return
        }
        XCTAssertFalse(
            inbox.claimFrame(
                requestId: requestId,
                verifiedClientId: "client",
                attemptId: "attempt",
                currentTabId: "tab",
                currentDocumentVersion: "doc",
                currentView: view,
                unlocked: true,
                userStopped: false
            )
        )
        // A remote frame claim before approval must not dismiss the person's prompt.
        XCTAssertEqual(inbox.prompt?.requestId, requestId)
        XCTAssertTrue(inbox.approveOnDevice(
            requestId: requestId,
            currentTabId: "tab", currentDocumentVersion: "doc",
            currentView: view, unlocked: true
        ))
    }

    func testApprovalBindsExactWebViewAndClient() {
        let view = WebViewIdentity()
        let inbox = ConsentedCaptureRequestRegistry(nowMs: { 100 })
        let pending = inbox.submitAuthenticated(
            verifiedClientId: "client",
            attemptId: "attempt",
            tabId: "tab",
            documentVersion: "doc",
            view: view
        )
        guard let requestId = pending?.requestId else {
            XCTFail("Missing request")
            return
        }
        XCTAssertTrue(
            inbox.approveOnDevice(
                requestId: requestId,
                currentTabId: "tab",
                currentDocumentVersion: "doc",
                currentView: view,
                unlocked: true
            )
        )
        XCTAssertTrue(
            inbox.claimFrame(
                requestId: requestId,
                verifiedClientId: "client",
                attemptId: "attempt",
                currentTabId: "tab",
                currentDocumentVersion: "doc",
                currentView: view,
                unlocked: true,
                userStopped: false
            )
        )
        XCTAssertFalse(
            inbox.claimFrame(
                requestId: requestId,
                verifiedClientId: "wrong",
                attemptId: "attempt",
                currentTabId: "tab",
                currentDocumentVersion: "doc",
                currentView: view,
                unlocked: true,
                userStopped: false
            )
        )
    }

    func testPendingExpiryAndLocalStop() {
        var now: Int64 = 200
        let view = WebViewIdentity()
        let inbox = ConsentedCaptureRequestRegistry(nowMs: { now })
        let first = inbox.submitAuthenticated(
            verifiedClientId: "client",
            attemptId: "attempt",
            tabId: "tab",
            documentVersion: "doc",
            view: view
        )
        now += ConsentedCaptureRequestRegistry.pendingTtlMs
        XCTAssertFalse(
            inbox.approveOnDevice(
                requestId: first?.requestId ?? "",
                currentTabId: "tab",
                currentDocumentVersion: "doc",
                currentView: view,
                unlocked: true
            )
        )
        let second = inbox.submitAuthenticated(
            verifiedClientId: "client",
            attemptId: "attempt2",
            tabId: "tab",
            documentVersion: "doc",
            view: view
        )
        XCTAssertNotNil(second)
        inbox.revoke()
        XCTAssertNil(inbox.prompt)
    }

    func testForeignAndStaleRequestsCannotRevokeOwnerApproval() {
        let view = WebViewIdentity()
        let inbox = ConsentedCaptureRequestRegistry(nowMs: { 1_000 })
        guard let req = inbox.submitAuthenticated(
            verifiedClientId: "owner", attemptId: "attempt-1",
            tabId: "tab-1", documentVersion: "doc-1", view: view
        ) else {
            XCTFail("Expected pending local consent")
            return
        }
        XCTAssertFalse(inbox.approveOnDevice(
            requestId: "stale-dialog", currentTabId: "tab-1",
            currentDocumentVersion: "doc-1", currentView: view, unlocked: true
        ))
        XCTAssertEqual(inbox.prompt?.requestId, req.requestId)
        XCTAssertTrue(inbox.approveOnDevice(
            requestId: req.requestId, currentTabId: "tab-1",
            currentDocumentVersion: "doc-1", currentView: view, unlocked: true
        ))
        for (id, client, attempt) in [
            ("foreign-request", "owner", "attempt-1"),
            (req.requestId, "attacker", "attempt-1"),
            (req.requestId, "owner", "stale-attempt"),
        ] {
            XCTAssertFalse(inbox.claimFrame(
                requestId: id, verifiedClientId: client, attemptId: attempt,
                currentTabId: "tab-1", currentDocumentVersion: "doc-1",
                currentView: view, unlocked: true, userStopped: false
            ))
        }
        XCTAssertTrue(inbox.claimFrame(
            requestId: req.requestId, verifiedClientId: "owner", attemptId: "attempt-1",
            currentTabId: "tab-1", currentDocumentVersion: "doc-1",
            currentView: view, unlocked: true, userStopped: false
        ))
    }

    func testMissingPrincipalCannotRevokeOwnerAndOwnerMismatchStillRevokes() {
        let view = WebViewIdentity()
        let inbox = ConsentedCaptureRequestRegistry(nowMs: { 2_000 })
        guard let req = inbox.submitAuthenticated(
            verifiedClientId: "owner", attemptId: "attempt-1",
            tabId: "tab-1", documentVersion: "doc-1", view: view
        ) else {
            XCTFail("Expected pending local consent")
            return
        }
        XCTAssertTrue(inbox.approveOnDevice(
            requestId: req.requestId, currentTabId: "tab-1",
            currentDocumentVersion: "doc-1", currentView: view, unlocked: true
        ))
        XCTAssertFalse(inbox.claimFrame(
            requestId: req.requestId, verifiedClientId: nil, attemptId: "attempt-1",
            currentTabId: "tab-1", currentDocumentVersion: "doc-1",
            currentView: view, unlocked: true, userStopped: false
        ))
        XCTAssertFalse(inbox.claimFrame(
            requestId: req.requestId, verifiedClientId: "owner", attemptId: "attempt-1",
            currentTabId: "tab-1", currentDocumentVersion: "new-document",
            currentView: view, unlocked: true, userStopped: false
        ))
        XCTAssertFalse(inbox.claimFrame(
            requestId: req.requestId, verifiedClientId: "owner", attemptId: "attempt-1",
            currentTabId: "tab-1", currentDocumentVersion: "doc-1",
            currentView: view, unlocked: true, userStopped: false
        ))
    }
}

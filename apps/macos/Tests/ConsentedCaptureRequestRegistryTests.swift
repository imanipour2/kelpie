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
        XCTAssertNil(inbox.prompt)
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
}

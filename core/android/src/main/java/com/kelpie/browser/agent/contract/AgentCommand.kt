package com.kelpie.browser.agent.contract

/** Only the authenticated gateway may populate [principalId]. Never trust a JSON field. */
data class AgentCommand(
    val commandId: String,
    val principalId: String,
    val consentGeneration: String,
    val tabId: String,
    val observedDocumentEpoch: String,
    val operation: Operation,
    val targetUrl: String? = null,
    /** Preserve the old positional ABI for existing R5 source and JVM fixtures. */
    val clientSequence: Long = 0,
    /** CSS selector is data, never interpolated as executable JavaScript. */
    val selector: String? = null,
    /** User-provided input payload is never logged or echoed in receipts. */
    val inputText: String? = null,
    val scrollDeltaX: Double = 0.0,
    val scrollDeltaY: Double = 0.0,
    val captureRequest: R5VisionCaptureRequest = R5VisionCaptureRequest.LEGACY,
    /** An observed inactive tab may be closed; never reinterpret this as the active renderer ID. */
    val targetTabId: String? = null,
    val targetDocumentEpoch: String? = null,
)

enum class Operation {
    LIST_TABS,
    OPEN_TAB,
    CLOSE_TAB,
    SWITCH_TAB,
    NAVIGATE,
    BACK,
    FORWARD,
    READ_DOM,
    SCREENSHOT,
    SCROLL,
    CLICK,
    TYPE,
    FILL,
    SELECT,
    WAIT,
    PAYMENT,
    PUBLISH,
    IRREVERSIBLE_DELETE,
    EXPORT_CREDENTIALS,
}

enum class AdmissionCode {
    ALLOWED,
    NO_ACTIVE_SESSION,
    WRONG_PRINCIPAL,
    WRONG_GENERATION,
    LOCKED,
    INVALID_TARGET,
    STALE_DOCUMENT,
    UNSAFE_URL,
    SENSITIVE_ACTION_REQUIRES_APPROVAL,
}

data class AdmissionResult(
    val code: AdmissionCode,
) {
    val allowed: Boolean get() = code == AdmissionCode.ALLOWED
}

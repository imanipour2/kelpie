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
    /** Positive, strictly increasing for a long-lived paired client stream; 0 is rejected. */
    val clientSequence: Long = 0,
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

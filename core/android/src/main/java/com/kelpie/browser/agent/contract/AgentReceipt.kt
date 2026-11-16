package com.kelpie.browser.agent.contract

/** Immutable, non-sensitive metadata. Never serialize a DOM, password or screenshot into a receipt. */
data class ObservedTarget(
    val tabId: String,
    val documentEpoch: String,
)

enum class ExecutionStatus {
    EXECUTED,
    REJECTED,
    FAILED,
    UNCERTAIN,
    CANCELLED,
}

enum class ExecutionCode {
    VERIFIED_EFFECT,
    UNVERIFIED_EFFECT,
    ADAPTER_FAILURE,
    NO_TARGET,
    DUPLICATE_COMMAND,
    SESSION_REVOKED,
    OPERATION_TIMEOUT,
    ADAPTER_CANCELLED,
    INTERNAL_FAILURE,
}

data class AgentReceipt(
    val commandId: String,
    val status: ExecutionStatus,
    val code: ExecutionCode? = null,
    val admission: AdmissionCode? = null,
    val before: ObservedTarget? = null,
    val after: ObservedTarget? = null,
    val evidenceId: String? = null,
)

sealed interface AdapterOutcome {
    /** Only emit when a *fresh observation* proves the requested effect. */
    data class Verified(
        val evidenceId: String,
    ) : AdapterOutcome

    /** A click/submit might have taken effect; retrying automatically may double-submit. */
    data object Uncertain : AdapterOutcome

    data object Failed : AdapterOutcome
}

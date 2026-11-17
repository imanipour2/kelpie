package com.kelpie.browser.agent.admission

import com.kelpie.browser.agent.contract.AdmissionCode
import com.kelpie.browser.agent.contract.AdmissionResult
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.agent.session.SessionConsentCore
import java.net.URI

/** Pure authorization check; execution must revalidate immediately before acting. */
object AgentAdmissionPolicy {
    private val sensitive =
        setOf(
            Operation.PAYMENT,
            Operation.PUBLISH,
            Operation.IRREVERSIBLE_DELETE,
            Operation.EXPORT_CREDENTIALS,
        )
    private val tabNeutral = setOf(Operation.LIST_TABS, Operation.OPEN_TAB)
    private val documentNeutral =
        setOf(
            Operation.LIST_TABS,
            Operation.OPEN_TAB,
            Operation.CLOSE_TAB,
            Operation.SWITCH_TAB,
            Operation.NAVIGATE,
            Operation.BACK,
            Operation.FORWARD,
        )

    fun check(
        state: SessionConsentCore.State,
        command: AgentCommand,
        actualTabId: String?,
        actualDocumentEpoch: String?,
    ): AdmissionResult {
        val active =
            state as? SessionConsentCore.State.Active
                ?: return AdmissionResult(AdmissionCode.NO_ACTIVE_SESSION)
        if (active.principalId != command.principalId) return AdmissionResult(AdmissionCode.WRONG_PRINCIPAL)
        if (active.generation != command.consentGeneration) return AdmissionResult(AdmissionCode.WRONG_GENERATION)
        if (active.locked) return AdmissionResult(AdmissionCode.LOCKED)
        if (command.commandId.isBlank() || command.commandId.length > 128) {
            return AdmissionResult(AdmissionCode.INVALID_TARGET)
        }
        if (command.operation !in tabNeutral && (actualTabId.isNullOrBlank() || actualTabId != command.tabId)) {
            return AdmissionResult(AdmissionCode.INVALID_TARGET)
        }
        if (command.operation !in documentNeutral &&
            (actualDocumentEpoch.isNullOrBlank() || actualDocumentEpoch != command.observedDocumentEpoch)
        ) {
            return AdmissionResult(AdmissionCode.STALE_DOCUMENT)
        }
        if (command.operation == Operation.NAVIGATE || command.operation == Operation.OPEN_TAB) {
            if (!safeWebDestination(command.targetUrl)) return AdmissionResult(AdmissionCode.UNSAFE_URL)
        }
        if (command.operation in setOf(Operation.CLICK, Operation.TYPE, Operation.FILL, Operation.SELECT)) {
            if (command.selector.isNullOrBlank() ||
                command.selector.length > 500 ||
                command.selector.any(Char::isISOControl)
            ) {
                return AdmissionResult(AdmissionCode.INVALID_TARGET)
            }
        }
        if (command.operation in setOf(Operation.TYPE, Operation.FILL, Operation.SELECT)) {
            if (command.inputText == null || command.inputText.length > 4_096) {
                return AdmissionResult(AdmissionCode.INVALID_TARGET)
            }
        }
        if (command.operation == Operation.SCROLL &&
            (
                !command.scrollDeltaX.isFinite() ||
                    !command.scrollDeltaY.isFinite() ||
                    kotlin.math.abs(command.scrollDeltaX) > 3_000.0 ||
                    kotlin.math.abs(command.scrollDeltaY) > 3_000.0
            )
        ) {
            return AdmissionResult(AdmissionCode.INVALID_TARGET)
        }
        // Publishing, irreversible deletion, payment and credential export require
        // separately verified owner approval, not a generic browsing grant.
        if (command.operation in sensitive) {
            return AdmissionResult(AdmissionCode.SENSITIVE_ACTION_REQUIRES_APPROVAL)
        }
        return AdmissionResult(AdmissionCode.ALLOWED)
    }

    /** No static domain whitelist; only web schemes are admitted at this first boundary. */
    private fun safeWebDestination(raw: String?): Boolean {
        if (raw == null || raw.length !in 1..2048 || raw.any(Char::isISOControl)) return false
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("https", "http")) return false
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return false
        return true
    }
}

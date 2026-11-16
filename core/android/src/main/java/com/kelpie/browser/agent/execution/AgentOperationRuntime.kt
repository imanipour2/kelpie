package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.admission.AgentAdmissionPolicy
import com.kelpie.browser.agent.contract.AdapterOutcome
import com.kelpie.browser.agent.contract.AdmissionCode
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.AgentReceipt
import com.kelpie.browser.agent.contract.ExecutionCode
import com.kelpie.browser.agent.contract.ExecutionStatus
import com.kelpie.browser.agent.contract.ObservedTarget
import com.kelpie.browser.agent.session.SessionConsentCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Pure orchestration. Existing TabStore/HandlerContext must implement [BrowserAdapter]
 * and resolve the actual WebView on the UI thread. A client-supplied principal,
 * tab epoch or success flag is NEVER evidence of authentication or execution.
 *
 * A single global lock is deliberately conservative until the real TabStore
 * lifecycle is covered by instrumentation; no split-brain tab switching.
 */
class AgentOperationRuntime(
    private val consent: SessionConsentCore,
    private val adapter: BrowserAdapter,
    private val maxTrackedCommands: Int = 4096,
) {
    interface BrowserAdapter {
        /** Resolve the *real* tab, not a request JSON snapshot. */
        suspend fun observe(command: AgentCommand): ObservedTarget?

        /** Called only after admission; must verify actual effect before returning Verified. */
        suspend fun perform(
            command: AgentCommand,
            before: ObservedTarget?,
        ): AdapterOutcome

        /** Must independently confirm the claimed effect using fresh observed page state. */
        suspend fun verifyEffect(
            command: AgentCommand,
            before: ObservedTarget?,
            after: ObservedTarget?,
            evidenceId: String,
        ): Boolean
    }

    private val commandMutex = Mutex()
    private val jobsLock = Any()
    private val running = mutableSetOf<Job>()
    // Bounded recent-ID cache plus a monotonic sequence: replays fail without
    // retaining all command IDs for a long-running process session.
    private val usedIds = LinkedHashSet<String>()
    private var commandGeneration: String? = null
    private var lastSequencedCommand = 0L

    init {
        require(maxTrackedCommands > 0) { "Command history capacity must be positive" }
    }

    /** Cancels work immediately; effects already sent to a website are not reversible. */
    fun stopFromTrustedNativeUi() {
        consent.stop()
        synchronized(jobsLock) { running.toList() }.forEach { it.cancel(CancellationException("LOCAL_STOP")) }
    }

    /** Verified principal comes from authenticated gateway metadata, NEVER remote JSON. */
    suspend fun dispatch(
        command: AgentCommand,
        verifiedPrincipal: String?,
    ): AgentReceipt {
        if (verifiedPrincipal.isNullOrBlank() || verifiedPrincipal != command.principalId) {
            return rejected(command, AdmissionCode.WRONG_PRINCIPAL)
        }
        // Avoid even observing a tab before confirming the verified session identity.
        val initial =
            consent.snapshot() as? SessionConsentCore.State.Active
                ?: return rejected(command, AdmissionCode.NO_ACTIVE_SESSION)
        if (initial.principalId != verifiedPrincipal) return rejected(command, AdmissionCode.WRONG_PRINCIPAL)
        if (initial.generation != command.consentGeneration) return rejected(command, AdmissionCode.WRONG_GENERATION)
        if (initial.locked) return rejected(command, AdmissionCode.LOCKED)
        var effectStarted = false
        val job = currentCoroutineContext()[Job]
        if (job != null) synchronized(jobsLock) { running += job }
        return try {
            commandMutex.withLock {
                val start = consent.snapshot()
                val active =
                    start as? SessionConsentCore.State.Active
                        ?: return@withLock rejected(command, AdmissionCode.NO_ACTIVE_SESSION)
                // No cached id or token is allowed to survive a new real consent generation.
                if (active.generation != command.consentGeneration) {
                    return@withLock rejected(command, AdmissionCode.WRONG_GENERATION)
                }
                if (commandGeneration != active.generation) {
                    usedIds.clear()
                    lastSequencedCommand = 0L
                    commandGeneration = active.generation
                }
                if (command.clientSequence <= 0 ||
                    command.clientSequence <= lastSequencedCommand ||
                    command.commandId in usedIds
                ) {
                    // No legacy sequence=0 bypass in product code. A repeated ID with a
                    // fresh sequence is still a duplicate, not a new authorized action.
                    return@withLock AgentReceipt(command.commandId, ExecutionStatus.REJECTED, ExecutionCode.DUPLICATE_COMMAND)
                }
                val before =
                    try {
                        adapter.observe(command)
                    } catch (_: Exception) {
                        return@withLock AgentReceipt(command.commandId, ExecutionStatus.FAILED, ExecutionCode.ADAPTER_FAILURE)
                    }
                val decision = AgentAdmissionPolicy.check(start, command, before?.tabId, before?.documentEpoch)
                if (!decision.allowed) return@withLock rejected(command, decision.code, before)
                // Reserve BEFORE entering untrusted WebView. Replayed writes never run twice.
                lastSequencedCommand = command.clientSequence
                if (usedIds.size >= maxTrackedCommands) {
                    usedIds.remove(usedIds.first())
                }
                usedIds.add(command.commandId)
                val outcome =
                    try {
                        withTimeout(MAX_OPERATION_MS) {
                            val preflight = adapter.observe(command)
                            if (preflight != before || consent.snapshot() != start) {
                                return@withTimeout AdapterOutcome.Uncertain
                            }
                            effectStarted = true
                            adapter.perform(command, before)
                        }
                    } catch (timeout: TimeoutCancellationException) {
                        return@withLock AgentReceipt(command.commandId, ExecutionStatus.UNCERTAIN, ExecutionCode.OPERATION_TIMEOUT, before = before)
                    } catch (_: Exception) {
                        return@withLock AgentReceipt(command.commandId, ExecutionStatus.UNCERTAIN, ExecutionCode.INTERNAL_FAILURE, before = before)
                    }
                val end = consent.snapshot()
                val after =
                    try {
                        adapter.observe(command)
                    } catch (_: Exception) {
                        return@withLock AgentReceipt(command.commandId, ExecutionStatus.UNCERTAIN, ExecutionCode.INTERNAL_FAILURE, before = before)
                    }
                if (end != start) {
                    return@withLock AgentReceipt(command.commandId, ExecutionStatus.UNCERTAIN, ExecutionCode.SESSION_REVOKED, before = before, after = after)
                }
                when (outcome) {
                    is AdapterOutcome.Verified -> {
                        val independentlyVerified =
                            try {
                                // Verification itself may involve DOM observation; keep under the
                                // same serialized command lease and validate consent again after it.
                                withTimeout(MAX_OPERATION_MS) {
                                    adapter.verifyEffect(command, before, after, outcome.evidenceId)
                                }
                            } catch (_: Exception) {
                                false
                            }
                        if (consent.snapshot() != start || !independentlyVerified) {
                            AgentReceipt(command.commandId, ExecutionStatus.UNCERTAIN, ExecutionCode.UNVERIFIED_EFFECT, before = before, after = after)
                        } else {
                            AgentReceipt(
                                command.commandId,
                                ExecutionStatus.EXECUTED,
                                ExecutionCode.VERIFIED_EFFECT,
                                before = before,
                                after = after,
                                evidenceId = outcome.evidenceId,
                            )
                        }
                    }
                    AdapterOutcome.Uncertain ->
                        AgentReceipt(
                            command.commandId,
                            ExecutionStatus.UNCERTAIN,
                            ExecutionCode.UNVERIFIED_EFFECT,
                            before = before,
                            after = after,
                        )
                    AdapterOutcome.Failed ->
                        AgentReceipt(
                            command.commandId,
                            ExecutionStatus.FAILED,
                            ExecutionCode.ADAPTER_FAILURE,
                            before = before,
                            after = after,
                        )
                }
            }
        } catch (cancel: CancellationException) {
            // If perform() started, STOP cannot undo an already-dispatched click or submit.
            AgentReceipt(
                command.commandId,
                if (effectStarted) ExecutionStatus.UNCERTAIN else ExecutionStatus.CANCELLED,
                ExecutionCode.ADAPTER_CANCELLED,
            )
        } finally {
            if (job != null) synchronized(jobsLock) { running.remove(job) }
        }
    }

    private fun rejected(
        command: AgentCommand,
        code: AdmissionCode,
        target: ObservedTarget? = null,
    ) = AgentReceipt(command.commandId, ExecutionStatus.REJECTED, admission = code, before = target)

    companion object {
        const val MAX_OPERATION_MS = 30_000L
    }
}

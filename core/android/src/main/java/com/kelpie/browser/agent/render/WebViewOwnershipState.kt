package com.kelpie.browser.agent.render

/**
 * Pure lifecycle reducer for ONE Android WebView. The native adapter must perform
 * actual detach/attach on the main thread and call finish* only after completion.
 * No WebView, Activity, Surface or Android context is retained here.
 */
class WebViewOwnershipState {
    enum class Host { ABSENT, FOREGROUND, MOVING_PRIVATE, PRIVATE, RESTORING, SUSPENDED, FAILED, CLOSED }

    data class Ticket(
        val sessionGeneration: String,
        val webViewIdentity: String,
        val sequence: Long,
        val target: Host,
    )

    private var generation: String? = null
    private var identity: String? = null
    private var nextSequence = 0L
    private var ownershipEpoch = 0L
    private var currentTicket: Ticket? = null
    private var host = Host.ABSENT

    @Synchronized
    fun status(): Host = host

    /** Incremented on every native-host lifecycle transition, including round trips. */
    @Synchronized
    fun transitionEpoch(): Long = ownershipEpoch

    /** Only bind after the real WebView identity and active consent were verified. */
    @Synchronized
    fun bindForeground(
        sessionGeneration: String,
        webViewIdentity: String,
    ): Boolean {
        // An absent renderer after lock or crash still belongs to the original
        // consent generation. Never permit a different generation to take over
        // that host state without creating a brand-new session owner.
        if (host != Host.ABSENT ||
            sessionGeneration.isBlank() ||
            webViewIdentity.isBlank() ||
            (generation != null && generation != sessionGeneration)
        ) {
            return false
        }
        generation = sessionGeneration
        identity = webViewIdentity
        host = Host.FOREGROUND
        ownershipEpoch++
        return true
    }

    @Synchronized
    fun moveToPrivate(sessionGeneration: String): Ticket? {
        if (host != Host.FOREGROUND || generation != sessionGeneration) return null
        return ticket(Host.MOVING_PRIVATE, Host.PRIVATE)
    }

    @Synchronized
    fun completePrivate(ticket: Ticket): Boolean {
        if (host != Host.MOVING_PRIVATE || ticket != currentTicket || ticket.target != Host.PRIVATE) return false
        currentTicket = null
        host = Host.PRIVATE
        ownershipEpoch++
        return true
    }

    @Synchronized
    fun restoreForeground(sessionGeneration: String): Ticket? {
        if (host != Host.PRIVATE || generation != sessionGeneration) return null
        return ticket(Host.RESTORING, Host.FOREGROUND)
    }

    @Synchronized
    fun completeRestore(ticket: Ticket): Boolean {
        if (host != Host.RESTORING || ticket != currentTicket || ticket.target != Host.FOREGROUND) return false
        currentTicket = null
        host = Host.FOREGROUND
        ownershipEpoch++
        return true
    }

    /** A lock demands that the native adapter release its private Surface immediately. */
    @Synchronized
    fun lockDevice() {
        if (host == Host.CLOSED || (host == Host.ABSENT && generation == null)) return
        currentTicket = null
        nextSequence++
        host = Host.SUSPENDED
        ownershipEpoch++
    }

    /** Unlock never claims a WebView is reattached; real adapter must rebind it. */
    @Synchronized
    fun unlockNeedsReattach(sessionGeneration: String): Boolean {
        if (host != Host.SUSPENDED || generation != sessionGeneration) return false
        host = Host.ABSENT
        ownershipEpoch++
        identity = null
        return true
    }

    @Synchronized
    fun failRenderer() {
        if (host == Host.CLOSED) return
        currentTicket = null
        nextSequence++
        host = Host.FAILED
        ownershipEpoch++
    }

    /** Fail closed: native renderer resources must be disposed before next bind. */
    @Synchronized
    fun detachAfterFailure() {
        if (host != Host.FAILED) return
        host = Host.ABSENT
        ownershipEpoch++
        identity = null
    }

    @Synchronized
    fun stop() {
        currentTicket = null
        generation = null
        identity = null
        nextSequence++
        host = Host.CLOSED
        ownershipEpoch++
    }

    @Synchronized
    fun mayExecuteWebOperation(
        sessionGeneration: String,
        webViewIdentity: String,
    ): Boolean =
        generation == sessionGeneration &&
            identity == webViewIdentity &&
            (host == Host.FOREGROUND || host == Host.PRIVATE)

    private fun ticket(
        inProgress: Host,
        target: Host,
    ): Ticket {
        host = inProgress
        nextSequence++
        ownershipEpoch++
        return Ticket(generation!!, identity!!, nextSequence, target).also { currentTicket = it }
    }
}

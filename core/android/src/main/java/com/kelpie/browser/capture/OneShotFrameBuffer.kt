package com.kelpie.browser.capture

import kotlinx.coroutines.CompletableDeferred

/**
 * Exactly one owner may take a captured frame. Late callbacks after STOP,
 * timeout or cancelled requests dispose their native Bitmap instead of
 * retaining it in a completed Deferred that nobody can receive.
 *
 * The disposal callback must be safe to call on the producing thread.
 */
internal class OneShotFrameBuffer<T : Any>(
    private val dispose: (T) -> Unit,
) {
    private val readiness = CompletableDeferred<Boolean>()
    private val mutex = Any()
    private var buffered: T? = null
    private var closed = false

    val isOpen: Boolean
        get() = synchronized(mutex) { !closed && !readiness.isCompleted }

    fun publish(item: T): Boolean {
        val accepted =
            synchronized(mutex) {
                if (closed || readiness.isCompleted) {
                    false
                } else {
                    buffered = item
                    readiness.complete(true)
                    true
                }
            }
        if (!accepted) dispose(item)
        return accepted
    }

    fun fail() {
        synchronized(mutex) {
            readiness.complete(false)
        }
    }

    suspend fun awaitTake(): T? {
        if (!readiness.await()) return null
        return synchronized(mutex) {
            if (closed) {
                null
            } else {
                val result = buffered
                buffered = null
                result
            }
        }
    }

    fun close() {
        val orphan =
            synchronized(mutex) {
                if (!closed) {
                    closed = true
                    readiness.complete(false)
                }
                val item = buffered
                buffered = null
                item
            }
        if (orphan != null) dispose(orphan)
    }
}

package com.kelpie.browser.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** A probe cannot prove remote ChatGPT identity, a live tunnel session or browser tool permission. */
internal data class TunnelReadinessSnapshot(
    val healthStatus: Int?,
    val readyStatus: Int?,
) {
    val healthOk: Boolean get() = healthStatus == 200
    val readyOk: Boolean get() = healthOk && readyStatus == 200
}

/**
 * Read-only, loopback-only health check for the existing tunnel-client diagnostics service.
 * Never reads Termux files, sends credentials, follows redirects, or starts/stops a service.
 * 8080 is a probe target, not a claim that the owner has configured this exact port.
 */
internal object TunnelReadinessProbe {
    private const val DIAGNOSTIC_PORT = 8080
    private const val TIMEOUT_MS = 900

    suspend fun check(): TunnelReadinessSnapshot =
        withContext(Dispatchers.IO) {
            TunnelReadinessSnapshot(
                healthStatus = getStatus("/healthz"),
                readyStatus = getStatus("/readyz"),
            )
        }

    private fun getStatus(path: String): Int? {
        val url = URL("http://127.0.0.1:$DIAGNOSTIC_PORT$path")
        val connection =
            (url.openConnection(Proxy(Proxy.Type.DIRECT, InetSocketAddress("127.0.0.1", DIAGNOSTIC_PORT))) as HttpURLConnection)
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            // Status only; do not retrieve or log response bodies.
            connection.responseCode
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

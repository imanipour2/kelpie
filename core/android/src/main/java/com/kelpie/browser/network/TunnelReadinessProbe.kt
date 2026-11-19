package com.kelpie.browser.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/** A probe cannot prove remote ChatGPT identity, a live tunnel session or browser tool permission. */
internal data class TunnelControlPlaneStatus(
    val status: String,
    val reasonCode: String?,
    val consecutiveFailures: Int?,
)

/**
 * A 200 /readyz means that startup completed, NOT that the cloud command poller
 * received commands. This intentionally contains only bounded categorical data.
 */
internal fun parseControlPlaneStatus(json: String): TunnelControlPlaneStatus? {
    val data = HttpJson.parseJsonBody(json) ?: return null
    val status = (data["status"] as? String)?.takeIf { it in setOf("ok", "degraded", "unknown") } ?: return null
    val reason = (data["reason_code"] as? String)?.takeIf {
        it in setOf("network_error", "auth_error", "unauthorized", "forbidden", "timeout", "unavailable")
    }
    val failures = ((data["details"] as? Map<*, *>)?.get("consecutive_failures") as? Number)
        ?.toInt()?.coerceIn(0, 9999)
    return TunnelControlPlaneStatus(status, reason, failures)
}

internal data class TunnelReadinessSnapshot(
    val healthStatus: Int?,
    val readyStatus: Int?,
    val controlPlane: TunnelControlPlaneStatus? = null,
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
                controlPlane = getControlPlaneStatus(),
            )
        }


    /**
     * A local, unauthenticated, read-only administrative health snapshot.
     * Limits the body to 4 KiB and never exposes arbitrary error messages,
     * addresses, credentials, keys or remote tunnel IDs to the UI/logs.
     */
    private fun getControlPlaneStatus(): TunnelControlPlaneStatus? {
        val connection =
            URL("http://127.0.0.1:$DIAGNOSTIC_PORT/health/control-plane")
                .openConnection(Proxy.NO_PROXY) as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) return null
            val bytes = ByteArray(4097)
            var count = 0
            connection.inputStream.use { input ->
                while (count < bytes.size) {
                    val received = input.read(bytes, count, bytes.size - count)
                    if (received < 0) break
                    if (received == 0) break
                    count += received
                }
            }
            if (count <= 0 || count >= bytes.size) return null
            parseControlPlaneStatus(String(bytes, 0, count, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun getStatus(path: String): Int? {
        val url = URL("http://127.0.0.1:$DIAGNOSTIC_PORT$path")
        val connection =
            (url.openConnection(Proxy.NO_PROXY) as HttpURLConnection)
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

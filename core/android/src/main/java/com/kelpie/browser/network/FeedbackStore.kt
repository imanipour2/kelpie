package com.kelpie.browser.network

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

data class FeedbackRecord(
    val reportId: String,
    val storedAt: String,
    val payload: Map<String, Any?>,
)

object FeedbackStore {
    private val categories = setOf("interaction", "coordinates", "screenshot", "observation", "security", "network", "other")
    private val commands = setOf("click", "tap", "fill", "type", "select-option", "scroll", "screenshot", "snapshot", "coordinate-diagnostics")
    private val errors =
        setOf(
            "STALE_ELEMENT",
            "STALE_OBSERVATION",
            "AMBIGUOUS_TARGET",
            "OCCLUDED",
            "OBSERVATION_RACE",
            "NO_FRESH_FRAME",
            "NEEDS_HUMAN",
            "BLOCKED",
            "SESSION_REVOKED",
            "DEVICE_LOCKED",
            "TIMEOUT",
            "ELEMENT_NOT_FOUND",
            "ELEMENT_NOT_VISIBLE",
            "EVAL_ERROR",
            "INVALID_PARAMS",
            "SCREENSHOT_FAILED",
        )
    private val statuses = setOf("ADMITTED", "DISPATCHED", "EXECUTED", "EFFECT_VERIFIED", "UNCERTAIN", "STALE", "BLOCKED")
    private val counters = setOf("count", "seenCount", "observedCount", "attemptCount", "errorCount", "durationMs", "width", "height")

    /** Only typed, bounded diagnostic metadata is eligible for durable storage. */
    internal fun safeMetadata(body: Map<String, Any?>): Map<String, Any?> {
        val safe = mutableMapOf<String, Any?>("payloadRedacted" to true)
        safe["category"] = (body["category"] as? String)?.takeIf { it in categories } ?: "other"
        safe["command"] = (body["command"] as? String)?.takeIf { it in commands } ?: "unknown"
        (body["error"] as? String)?.takeIf { it in errors }?.let { safe["error"] = it }
        (body["status"] as? String)?.takeIf { it in statuses }?.let { safe["status"] = it }
        for (key in listOf("success", "truncated", "redacted")) {
            (body[key] as? Boolean)?.let { safe[key] = it }
        }
        for (key in counters) {
            val value = (body[key] as? Number)?.toDouble() ?: continue
            if (value.isFinite() && value >= 0 && value <= 100_000 && value == value.toLong().toDouble()) {
                safe[key] = value.toLong()
            }
        }
        return safe
    }

    @Suppress("UNUSED_PARAMETER")
    fun save(
        context: Context,
        body: Map<String, Any?>,
        platform: String,
        deviceId: String,
        deviceName: String,
    ): FeedbackRecord {
        val reportId = UUID.randomUUID().toString().lowercase()
        val storedAt = Instant.now().toString()
        val payload =
            safeMetadata(body) +
                mapOf(
                    "reportId" to reportId,
                    "storedAt" to storedAt,
                    "platform" to "android",
                )
        val directory = File(context.filesDir, "feedback").apply { mkdirs() }
        File(directory, "${storedAt.replace(":", "-")}-$reportId.json")
            .writeText(JSONObject(payload).toString(2))
        return FeedbackRecord(reportId = reportId, storedAt = storedAt, payload = payload)
    }
}

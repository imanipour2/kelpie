package com.kelpie.browser

import org.json.JSONObject
import java.io.File

/** P3-only synthetic evidence, never full images, URLs, DOM or credentials. */
internal object P3EvidenceJournal {
    fun append(
        file: File,
        event: String,
        sequence: Int,
        activityVisible: Boolean,
        sameView: Boolean,
        sameDocument: Boolean,
        composeOwner: Boolean,
        composeMountCount: Int,
        matched: Boolean,
        red: Int?,
        green: Int?,
        blue: Int?,
        receivedNs: Long?,
        syntheticState: String,
    ): Boolean =
        runCatching {
            val record =
                JSONObject()
                    .put("event", event)
                    .put("sequence", sequence)
                    .put("activityVisible", activityVisible)
                    .put("tabStoreIdentity", sameView)
                    .put("sameDocument", sameDocument)
                    .put("composeOwner", composeOwner)
                    .put("composeMountCount", composeMountCount)
                    .put("matched", matched)
                    .put("elapsedMs", android.os.SystemClock.elapsedRealtime())
                    .put("sampleRgb", if (red == null || green == null || blue == null) "" else "$red,$green,$blue")
                    .put("frameReceivedNs", receivedNs ?: 0L)
                    .put("syntheticState", syntheticState.take(250))
            file.appendText(record.toString() + "\n")
        }.isSuccess
}

package com.kelpie.browser

import org.json.JSONObject
import org.json.JSONTokener

/**
 * An isolated P3 QA-only restoration of CSS scrollY after the same WebView has
 * moved between differently sized Android windows. Never recreates or reloads
 * the document; a malformed initial proof fails closed by yielding null.
 */
internal object P3ScrollRestore {
    fun script(initialProof: String?): String {
        val expectedY =
            runCatching {
                val serialized = JSONTokener(initialProof ?: "").nextValue() as String
                JSONObject(serialized).getInt("scrollY")
            }.getOrNull()
        return if (expectedY != null && expectedY >= 0) {
            "window.scrollTo(0,$expectedY); window.kelpieProof()"
        } else {
            "null"
        }
    }
}

package com.kelpie.browser

/** Pure, offline-only P3 guard snapshot, with no user data or URL logging. */
internal data class P3GuardSnapshot(
    val run: Boolean,
    val token: Boolean,
    val lease: Boolean,
    val tab: Boolean,
    val view: Boolean,
    val document: Boolean,
    val ready: Boolean,
    val origin: Boolean,
    val url: Boolean,
) {
    val valid: Boolean get() = run && token && lease && tab && view && document && ready && origin && url

    fun failedFields(): String =
        listOf(
            "RUN" to run,
            "TICKET" to token,
            "LEASE" to lease,
            "TAB" to tab,
            "VIEW" to view,
            "DOCUMENT" to document,
            "LOADING" to ready,
            "FIXTURE_ORIGIN" to origin,
            "VIEW_URL" to url,
        ).filterNot { it.second }.joinToString("_") { it.first }.ifBlank { "UNKNOWN" }
}

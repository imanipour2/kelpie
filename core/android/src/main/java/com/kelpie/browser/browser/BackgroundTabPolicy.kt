package com.kelpie.browser.browser

/** Pure guard for agent-created tabs. Human tab commands follow their own UI rules. */
internal object BackgroundTabPolicy {
    const val MAX_AGENT_TABS = 40

    fun canCreate(ids: List<String>, active: String?): Boolean =
        !active.isNullOrBlank() && active in ids && ids.size < MAX_AGENT_TABS && ids.distinct().size == ids.size

    fun canClose(
        ids: List<String>,
        active: String?,
        target: String?,
    ): Boolean =
        !active.isNullOrBlank() && active in ids && !target.isNullOrBlank() && target != active && target in ids
}

package com.kelpie.browser.agent.execution

import com.kelpie.browser.browser.TabStore

/**
 * R5 metadata projection for only app-owned tabs. Never export cookies,
 * passwords, query parameters or fragments. A tab is NOT switched by listing.
 */
internal data class R5AgentTabDescription(
    val id: String,
    val documentEpoch: String,
    val title: String,
    val url: String,
    val active: Boolean,
)

internal object R5AgentTabProjection {
    private const val MAX_TABS = 40

    fun fromStore(store: TabStore): List<R5AgentTabDescription> {
        val activeId = store.activeTabId.value
        return store.tabs.value.take(MAX_TABS).map { tab ->
            R5AgentTabDescription(
                id = tab.id,
                documentEpoch = tab.documentVersion,
                title = tab.pageTitle,
                url = tab.currentUrl,
                active = tab.id == activeId,
            )
        }
    }

    fun publicSnapshot(tabs: List<R5AgentTabDescription>): List<Map<String, Any?>> =
        tabs.take(MAX_TABS).map { tab ->
            mapOf(
                "tabId" to tab.id,
                "documentEpoch" to tab.documentEpoch,
                "active" to tab.active,
                "title" to tab.title.take(256),
                "urlWithoutQuery" to tab.url.substringBefore("?").substringBefore("#").take(2048),
            )
        }

    /** Independent post-observation check; identity must not change during one read. */
    fun stableIdentity(tabs: List<R5AgentTabDescription>): String =
        tabs.take(MAX_TABS).joinToString(separator = "|") {
            it.id + ":" + it.documentEpoch + ":" + it.active
        }
}

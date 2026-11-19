package com.kelpie.browser.llm

import com.kelpie.browser.ai.openai.OpenAIException
import com.kelpie.browser.ai.openai.OpenAIJson
import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.handlers.JSEscape
import com.kelpie.browser.handlers.observationPrivacyScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** One ephemeral read against the requested WebView; switching tabs cannot redirect it. */
object PageTextSnapshot {
    fun script(selector: String): String =
        """
        (function() {
            ${observationPrivacyScript()}
            const root = document.querySelector('${JSEscape.string(selector)}');
            const text = root ? kelpieSafeNodeText(root) : '';
            const labels = [];
            if (root) for (const element of root.querySelectorAll('img[alt], [aria-label], [aria-labelledby], svg')) {
                const style = getComputedStyle(element);
                if (element.closest('[aria-hidden="true"]') || style.display === 'none' || style.visibility === 'hidden') continue;
                if (kelpiePrivateElement(element)) continue;
                const label = kelpieSafeName(element);
                if (label && !labels.includes(label)) labels.push(label);
                if (labels.length >= 200) break;
            }
            return {title: kelpieSafeText(document.title), url: kelpieSafeURL(location.href) || '', content: text, accessibleLabels: labels,
                redacted: true, coverage: 'INCOMPLETE',
                wordCount: text ? text.split(/\s+/).length : 0,
                language: /^[a-z]{2,3}(?:-[a-z0-9]{2,8})*$/i.test(document.documentElement.lang) ? kelpieSafeText(document.documentElement.lang) : null,
                excerpt: text.substring(0, 200)};
        })()
        """.trimIndent()

    suspend fun read(
        context: HandlerContext,
        body: Map<String, Any?>,
    ): Map<String, Any?> = decodeSnapshot(readObject(context, body, script(body["selector"] as? String ?: "body")))

    suspend fun readObject(
        context: HandlerContext,
        body: Map<String, Any?>,
        script: String,
    ): Map<String, Any?> =
        withContext(Dispatchers.Main) {
            check(context.remoteDocumentGuard?.invoke() != false) { "Tab document changed or job stopped" }
            val requested = body["tabId"] as? String
            val webView =
                if (requested != null) {
                    context.tabStore
                        ?.tabs
                        ?.value
                        ?.firstOrNull { it.id == requested }
                        ?.webView
                        ?: throw OpenAIException("TAB_NOT_FOUND", "Requested tab not found")
                } else {
                    (context.tabStore?.activeTab?.webView ?: context.webView) ?: throw OpenAIException("NO_WEBVIEW", "No browser tab is available")
                }
            val raw = context.withBrowserTarget(webView) { context.evaluateJS("JSON.stringify(($script))") }
            check(context.remoteDocumentGuard?.invoke() != false) { "Tab document changed or job stopped" }
            decodeObject(raw)
        }

    fun decode(raw: String): Map<String, Any?> = decodeSnapshot(decodeObject(raw))

    private fun decodeSnapshot(result: Map<String, Any?>): Map<String, Any?> {
        if (result["title"] !is String ||
            result["url"] !is String ||
            result["content"] !is String ||
            (result["accessibleLabels"] as? List<*>)?.all { it is String } != true
        ) {
            throw OpenAIException("EVAL_ERROR", "Page text returned an invalid snapshot")
        }
        return result
    }

    private fun decodeObject(raw: String): Map<String, Any?> {
        try {
            val unescaped = Json.decodeFromString<String>(raw)
            val parsed = Json.parseToJsonElement(unescaped) as? JsonObject ?: error("Expected an object")
            val result = OpenAIJson.toAny(parsed) as? Map<*, *> ?: error("Expected a snapshot")
            return result.entries.associate { it.key.toString() to it.value }
        } catch (_: Exception) {
            throw OpenAIException("EVAL_ERROR", "Page text returned an invalid snapshot")
        }
    }
}

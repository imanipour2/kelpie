package com.kelpie.browser.handlers

import com.kelpie.browser.ai.openai.OpenAIException
import com.kelpie.browser.llm.PageTextSnapshot
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.CancellationException

class DOMHandler(
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("get-dom") { getDOM(it) }
        router.register("query-selector") { querySelector(it) }
        router.register("query-selector-all") { querySelectorAll(it) }
        router.register("get-element-text") { getElementText(it) }
        router.register("get-attributes") { getAttributes(it) }
    }

    private suspend fun getDOM(body: Map<String, Any?>): Map<String, Any?> {
        val selector = JSEscape.string(body["selector"] as? String ?: "html")
        return read(
            body,
            "var el=document.querySelector('$selector');var html=kelpieSafeHTML(el);" +
                "return{html:html.substring(0,50000),length:html.length,redacted:true};",
        )
    }

    private suspend fun querySelector(body: Map<String, Any?>): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        return read(
            body,
            "var el=document.querySelector('${JSEscape.string(selector)}');" +
                "if(!el)return{found:false};return{found:true,element:summary(el)};",
        )
    }

    private suspend fun querySelectorAll(body: Map<String, Any?>): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        val limit = ((body["limit"] as? Number)?.toInt() ?: 50).coerceIn(1, 200)
        return read(
            body,
            "var els=document.querySelectorAll('${JSEscape.string(selector)}');" +
                "return{elements:Array.from(els).slice(0,$limit).map(summary),total:els.length,truncated:els.length>$limit};",
        )
    }

    private suspend fun getElementText(body: Map<String, Any?>): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        return read(
            body,
            "var el=document.querySelector('${JSEscape.string(selector)}');if(!el)return{found:false};" +
                "return{text:kelpieSafeNodeText(el),html:kelpieSafeHTML(el).substring(0,5000),redacted:true};",
        )
    }

    private suspend fun getAttributes(body: Map<String, Any?>): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        return read(
            body,
            "var el=document.querySelector('${JSEscape.string(selector)}');if(!el)return{found:false};" +
                "return{attributes:kelpieSafeAttributes(el),redacted:true};",
        )
    }

    private suspend fun read(
        body: Map<String, Any?>,
        expression: String,
    ): Map<String, Any?> {
        val script =
            """
            (function() {
                ${observationPrivacyScript()}
                function summary(el) {
                    var r = el.getBoundingClientRect();
                    return {tag: el.tagName.toLowerCase(), text: kelpieSafeNodeText(el).substring(0,200),
                        classes: [], id: null, sensitivity: kelpiePrivateElement(el) ? 'private' : 'public',
                        rect: {x:r.x,y:r.y,width:r.width,height:r.height}};
                }
                $expression
            })()
            """.trimIndent()
        return try {
            val result = PageTextSnapshot.readObject(ctx, body, script)
            if (result["found"] == false) {
                errorResponse("ELEMENT_NOT_FOUND", "No element matches the requested selector")
            } else {
                successResponse(result)
            }
        } catch (error: OpenAIException) {
            errorResponse(error.code, "Could not read the requested document")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Could not read the requested document")
        }
    }
}

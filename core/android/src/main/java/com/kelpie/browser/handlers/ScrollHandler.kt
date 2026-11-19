package com.kelpie.browser.handlers

import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class ScrollHandler(
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("scroll") { scroll(it) }
        router.register("scroll2") { scroll2(it) }
        router.register("scroll-to-top") { scrollTo(true) }
        router.register("scroll-to-bottom") { scrollTo(false) }
        router.register("scroll-to-y") { scrollToY(it) }
    }

    private suspend fun scroll(body: Map<String, Any?>): Map<String, Any?> {
        val dx = (body["deltaX"] as? Number)?.toDouble() ?: 0.0
        val dy = (body["deltaY"] as? Number)?.toDouble() ?: 0.0
        if (!dx.isFinite() || !dy.isFinite() || kotlin.math.abs(dx) > 100_000 || kotlin.math.abs(dy) > 100_000) {
            return errorResponse("INVALID_PARAMS", "Finite bounded CSS scroll offsets are required")
        }
        var dispatched = false
        return try {
            val before =
                ScrollEffectEvidence.read(ctx.evaluateJSReturningJSON(ScrollEffectEvidence.SNAPSHOT))
                    ?: return errorResponse("INVALID_VIEWPORT", "A measured CSS viewport is required")
            if (!before.wouldMove(dx, dy)) return errorResponse("AT_SCROLL_BOUNDARY", "The requested scroll cannot change this viewport")
            // The shared per-request guard fences the actual posted JS dispatch and each readback.
            dispatched = true
            ctx.evaluateJSReturningJSON(ScrollEffectEvidence.dispatch(dx, dy))
            val after =
                withTimeoutOrNull(1000L) {
                    var last: ScrollEffectEvidence? = null
                    repeat(20) {
                        val observed = ScrollEffectEvidence.read(ctx.evaluateJSReturningJSON(ScrollEffectEvidence.SNAPSHOT))
                        if (observed != null && before.confirms(observed, dx, dy) && observed == last) return@withTimeoutOrNull observed
                        last = observed
                        delay(50L)
                    }
                    null
                } ?: return errorResponse("EFFECT_UNVERIFIED", "Scroll was dispatched but its effect is unverified") + ("mayHaveCompleted" to true)
            successResponse(
                mapOf(
                    "scrollX" to after.x,
                    "scrollY" to after.y,
                    "preScrollX" to before.x,
                    "preScrollY" to before.y,
                    "postconditionStatus" to "VERIFIED_SCROLL_OFFSET",
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            errorResponse("EFFECT_UNVERIFIED", "Scroll could not be verified") + ("mayHaveCompleted" to dispatched)
        }
    }

    private suspend fun scroll2(body: Map<String, Any?>): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        val position = validScrollPosition(body["position"] as? String)
        val safe = JSEscape.string(selector)
        val js =
            "(function(){var el=document.querySelector('$safe');" +
                "if(!el)return null;el.scrollIntoView({block:'$position',behavior:'smooth'});" +
                "var r=el.getBoundingClientRect();" +
                "return{element:{tag:el.tagName.toLowerCase()," +
                "visible:r.top>=0&&r.bottom<=window.innerHeight," +
                "rect:{x:r.x,y:r.y,width:r.width,height:r.height}}," +
                "scrollsPerformed:1,viewport:{width:window.innerWidth,height:window.innerHeight}};})()"
        return try {
            val result = ctx.evaluateJSReturningJSON(js)
            if (result.isEmpty()) {
                errorResponse("ELEMENT_NOT_FOUND", "Element not found: $selector")
            } else {
                ctx.showTouchIndicatorForElement(selector)
                successResponse(result)
            }
        } catch (e: Exception) {
            errorResponse("EVAL_ERROR", e.message ?: "Unknown error")
        }
    }

    private suspend fun scrollTo(top: Boolean): Map<String, Any?> {
        val js =
            if (top) {
                "window.scrollTo(0, 0); ({scrollY: 0})"
            } else {
                "window.scrollTo(0, document.documentElement.scrollHeight); ({scrollY: window.scrollY})"
            }
        return try {
            successResponse(ctx.evaluateJSReturningJSON(js))
        } catch (e: Exception) {
            errorResponse("EVAL_ERROR", e.message ?: "Unknown error")
        }
    }

    /**
     * Constrains scroll-into-view `block` value to the documented allowlist.
     * Anything else falls back to "center" — never interpolate raw input.
     */
    private fun validScrollPosition(raw: String?): String =
        when (raw) {
            "top", "center", "bottom" -> raw
            else -> "center"
        }

    private suspend fun scrollToY(body: Map<String, Any?>): Map<String, Any?> {
        val y =
            (body["y"] as? Number)?.toDouble()
                ?: return errorResponse("MISSING_PARAM", "y is required (pixel offset)")
        val x = (body["x"] as? Number)?.toDouble() ?: 0.0
        val js = "(function(){window.scrollTo($x,$y);return{scrollX:window.scrollX,scrollY:window.scrollY,maxScrollY:Math.max(0,document.documentElement.scrollHeight-window.innerHeight)};})()"
        return try {
            successResponse(ctx.evaluateJSReturningJSON(js))
        } catch (e: Exception) {
            errorResponse("EVAL_ERROR", e.message ?: "Unknown error")
        }
    }
}

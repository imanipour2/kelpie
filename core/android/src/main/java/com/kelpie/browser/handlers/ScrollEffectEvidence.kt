package com.kelpie.browser.handlers

import kotlin.math.abs

/** A post-dispatch read of the same document must reach the requested, clamped CSS offset. */
internal data class ScrollEffectEvidence(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val maxX: Double,
    val maxY: Double,
    val rtl: Boolean,
    val originX: Double = 0.0,
) {
    fun expected(
        dx: Double,
        dy: Double,
    ): Pair<Double, Double> = (x + dx).coerceIn(if (rtl) originX - maxX else 0.0, if (rtl) originX else maxX) to (y + dy).coerceIn(0.0, maxY)

    fun wouldMove(
        dx: Double,
        dy: Double,
    ): Boolean {
        val target = expected(dx, dy)
        return abs(target.first - x) > 0.5 || abs(target.second - y) > 0.5
    }

    fun confirms(
        after: ScrollEffectEvidence,
        dx: Double,
        dy: Double,
    ): Boolean {
        val target = expected(dx, dy)
        return wouldMove(dx, dy) &&
            rtl == after.rtl &&
            listOf(width - after.width, height - after.height, maxX - after.maxX, maxY - after.maxY, originX - after.originX).all { abs(it) <= 0.5 } &&
            abs(target.first - after.x) <= 0.5 &&
            abs(target.second - after.y) <= 0.5
    }

    companion object {
        fun read(value: Map<String, Any?>): ScrollEffectEvidence? {
            val numbers =
                listOf("scrollX", "scrollY", "width", "height", "maxX", "maxY", "originX").map { key ->
                    (value[key] as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: return null
                }
            if (numbers[2] <= 0 || numbers[3] <= 0 || numbers[4] < 0 || numbers[5] < 0) return null
            val rtl = value["rtl"] as? Boolean ?: return null
            if (numbers[6] > 0 || (!rtl && numbers[6] != 0.0)) return null
            return ScrollEffectEvidence(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5], rtl, numbers[6])
        }

        // Android's native overlay-scrollbar host can expand the layout viewport beyond
        // the root's CSS width on wide mobile RTL pages. window.scrollBy still uses
        // window CSS offsets; the shifted right edge is not a visualViewport offset.
        val SNAPSHOT =
            """
            (()=>{
              const root=document.scrollingElement,doc=document.documentElement,body=document.body;
              if(!root||!doc||root.clientWidth<=0||innerWidth<=0||innerHeight<=0)return null;
              const rtl=getComputedStyle(root).direction==='rtl';
              return {scrollX:window.scrollX,scrollY:window.scrollY,width:innerWidth,height:innerHeight,
                maxX:Math.max(0,Math.max(root.scrollWidth,doc.scrollWidth,body?.scrollWidth||0)-innerWidth),
                maxY:Math.max(0,Math.max(root.scrollHeight,doc.scrollHeight,body?.scrollHeight||0)-innerHeight),
                originX:rtl?Math.min(0,root.clientWidth-innerWidth):0,rtl};
            })()
            """.trimIndent()

        fun dispatch(
            dx: Double,
            dy: Double,
        ): String {
            require(dx.isFinite() && dy.isFinite())
            return "(()=>{window.scrollBy({left:$dx,top:$dy,behavior:'instant'});return{dispatched:true};})()"
        }
    }
}

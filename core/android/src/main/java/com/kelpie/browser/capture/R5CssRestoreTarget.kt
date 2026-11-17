package com.kelpie.browser.capture

/** Same-document CSS pixels observed at release supersede stale entry scroll. */
internal object R5CssRestoreTarget {
    fun choose(
        entryX: Double,
        entryY: Double,
        current: Pair<Double, Double>?,
    ): Pair<Double, Double> =
        current?.takeIf { (x, y) -> x.isFinite() && y.isFinite() && y >= 0.0 }
            ?: (entryX to entryY)
}

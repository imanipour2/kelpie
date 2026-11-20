package com.kelpie.browser.handlers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollEffectEvidenceTest {
    private val start = ScrollEffectEvidence(0.0, 20.0, 400.0, 800.0, 600.0, 1200.0, false)

    @Test fun movementNeedsTheRequestedPostOffset() {
        assertFalse(start.confirms(start, 0.0, 200.0))
        assertFalse(start.confirms(start.copy(y = 150.0), 0.0, 200.0))
        assertTrue(start.confirms(start.copy(y = 220.0), 0.0, 200.0))
    }

    @Test fun edgeClampIsVerifiedButBoundaryNoOpIsNot() {
        assertTrue(start.confirms(start.copy(y = 1200.0), 0.0, 5000.0))
        val edge = start.copy(y = 1200.0)
        assertFalse(edge.wouldMove(0.0, 100.0))
        assertFalse(edge.confirms(edge, 0.0, 100.0))
    }

    @Test fun rtlUsesTheActualNegativeHorizontalDomain() {
        val rtl = start.copy(rtl = true)
        assertEquals(-100.0 to 20.0, rtl.expected(-100.0, 0.0))
        assertTrue(rtl.confirms(rtl.copy(x = -100.0), -100.0, 0.0))
        assertFalse(rtl.wouldMove(100.0, 0.0))
    }

    @Test fun expandedMobileRtlViewportPreservesTheMeasuredNegativeOrigin() {
        val before = ScrollEffectEvidence(-1151.6444, 0.0, 1536.0, 2874.0, 648.0, 1044.0, true, -1152.0)
        assertEquals(-1331.6444, before.expected(-180.0, 260.0).first, 0.0001)
        assertTrue(before.confirms(before.copy(x = -1331.5555, y = 259.9111), -180.0, 260.0))
        assertFalse(before.confirms(before.copy(x = -648.0, y = 260.0), -180.0, 260.0))
        assertFalse(before.confirms(before.copy(x = -1331.5555, y = 259.9111, originX = -1151.0), -180.0, 260.0))
    }

    @Test fun shiftedRtlEndpointsClampWithoutInventingBoundaryMovement() {
        val right = ScrollEffectEvidence(-1151.6444, 0.0, 1536.0, 2874.0, 648.0, 1044.0, true, -1152.0)
        val left = right.copy(x = -1799.8222, y = 1044.2666)
        assertEquals(-1800.0 to 1044.0, right.expected(-100_000.0, 100_000.0))
        assertTrue(right.confirms(left, -100_000.0, 100_000.0))
        assertFalse(left.wouldMove(-100_000.0, 100_000.0))
        assertTrue(left.confirms(right, 100_000.0, -100_000.0))
        assertFalse(right.wouldMove(100_000.0, 0.0))
        val fixed = right.copy(maxX = 0.0)
        assertFalse(fixed.wouldMove(-180.0, 0.0))
        assertFalse(fixed.confirms(fixed, -180.0, 0.0))
    }

    @Test fun layoutChangeCannotVerifyAnOldGeometry() {
        assertFalse(start.confirms(start.copy(y = 220.0, height = 600.0), 0.0, 200.0))
        assertFalse(start.confirms(start.copy(y = 220.0, maxY = 1600.0), 0.0, 200.0))
    }

    @Test fun zeroMissingAndNonFiniteViewportsAreRejected() {
        val fields =
            mapOf<String, Any?>(
                "scrollX" to 0.0,
                "scrollY" to 0.0,
                "width" to 400.0,
                "height" to 800.0,
                "maxX" to 0.0,
                "maxY" to 1000.0,
                "originX" to 0.0,
                "rtl" to false,
            )
        assertTrue(ScrollEffectEvidence.read(fields) != null)
        assertNull(ScrollEffectEvidence.read(fields - "originX"))
        assertNull(ScrollEffectEvidence.read(fields - "rtl"))
        assertNull(ScrollEffectEvidence.read(fields + ("height" to 0.0)))
        assertNull(ScrollEffectEvidence.read(fields + ("scrollY" to Double.NaN)))
        for (origin in listOf(null, "0", Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.0)) {
            assertNull(ScrollEffectEvidence.read(fields + ("originX" to origin)))
        }
        assertNull(ScrollEffectEvidence.read(fields + ("originX" to -100.0)))
        val rtl = fields + mapOf("rtl" to true, "originX" to -1152.0, "scrollX" to -1151.6444, "width" to 1536.0)
        assertEquals(-1152.0, checkNotNull(ScrollEffectEvidence.read(rtl)).originX, 0.0)
    }
}

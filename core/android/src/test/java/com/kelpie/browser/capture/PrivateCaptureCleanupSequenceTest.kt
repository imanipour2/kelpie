package com.kelpie.browser.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateCaptureCleanupSequenceTest {
    @Test fun failureDoesNotSuppressRemainingResourceReleases() {
        val cleanup = PrivateCaptureCleanupSequence()
        val released = mutableListOf<String>()
        cleanup.release {
            released += "presentation"
            throw IllegalStateException("fault injection")
        }
        cleanup.release { released += "virtual-display" }
        cleanup.release { released += "image-reader" }
        cleanup.release { released += "worker" }
        cleanup.release { released += "compose" }
        assertTrue(cleanup.failed)
        assertEquals(
            listOf("presentation", "virtual-display", "image-reader", "worker", "compose"),
            released,
        )
    }

    @Test fun cleanShutdownReportsNoFailure() {
        val cleanup = PrivateCaptureCleanupSequence()
        cleanup.release {}
        cleanup.release {}
        assertFalse(cleanup.failed)
    }
}

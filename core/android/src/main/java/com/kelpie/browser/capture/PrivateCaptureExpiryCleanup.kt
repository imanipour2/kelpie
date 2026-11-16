package com.kelpie.browser.capture

/** The status API may trigger cleanup only for its own authenticated grant. */
internal object PrivateCaptureExpiryCleanup {
    fun shouldRelease(
        activeRequestId: String?,
        activeClientId: String?,
        queriedRequestId: String,
        verifiedClientId: String?,
        status: String,
    ): Boolean =
        activeRequestId != null &&
            activeClientId != null &&
            activeRequestId == queriedRequestId &&
            activeClientId == verifiedClientId &&
            status != "APPROVED"
}

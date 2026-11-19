package com.kelpie.browser.agent.execution

/** Metadata selection alone is never proof that the native private renderer was transferred. */
internal object R5MotherPrivateSelectionProof {
    fun accepts(
        performed: Boolean,
        nativeVerified: Boolean,
        ownerStillAdmitted: Boolean,
        selectedTabId: String?,
        requestedTabId: String,
    ): Boolean = performed && nativeVerified && ownerStillAdmitted && selectedTabId == requestedTabId
}

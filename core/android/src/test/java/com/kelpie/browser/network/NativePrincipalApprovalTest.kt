package com.kelpie.browser.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NativePrincipalApprovalTest {
    @get:Rule val folder = TemporaryFolder()

    private class Trust : ComputerTrust {
        var computers = emptyList<TrustedComputer>()

        override fun list(): List<TrustedComputer> = computers

        override fun request(
            operation: String,
            body: JsonObject,
        ): JsonObject {
            if (operation != "revoke") return JsonObject(emptyMap())
            computers = emptyList()
            return JsonObject(mapOf("success" to JsonPrimitive(true)))
        }
    }

    @Test
    fun pendingOrRevokedPairingCannotBeApprovedFromObservedClientCache() {
        for (persist in listOf(false, true)) {
            val store = PairingStore(File(folder.newFolder(), "pairings.json"), Trust())
            val pending = store.startPairing("fixture-agent", "Fixture", "127.0.0.1").request!!
            assertFalse(store.hasPairedPrincipal("fixture-agent"))
            store.approve(pending.requestId, persist)!!
            assertTrue(store.hasPairedPrincipal("fixture-agent"))
            assertTrue(store.revoke("fixture-agent"))
            assertFalse(store.hasPairedPrincipal("fixture-agent"))
            assertFalse(store.hasPairedPrincipal("localhost"))
        }
    }

    @Test
    fun nativeComputerRevocationIsReadFromCurrentTrustInsteadOfRecentObservation() {
        val trust = Trust()
        val store = PairingStore(File(folder.newFolder(), "pairings.json"), trust)
        trust.computers = listOf(TrustedComputer("fixture-key", "192.0.2.1", "Fixture", 1L))
        assertTrue(store.hasPairedPrincipal("fixture-key"))
        assertTrue(trust.revoke("fixture-key"))
        assertFalse(store.hasPairedPrincipal("fixture-key"))
    }

    @Test
    fun expiredAccountGrantCannotPassNativeApprovalCheck() {
        val store = PairingStore(File(folder.newFolder(), "pairings.json"), Trust())
        val pending = store.startPairing("fixture-account", "Fixture", "peer", "account-session").request!!
        val grant = PairingStore.AccountGrant("account-session", "proof", pending.requestId, System.currentTimeMillis() - 1L, "peer")
        assertNull(store.approve(pending.requestId, persist = false, accountGrant = grant))
        assertFalse(store.hasPairedPrincipal("fixture-account"))
    }
}

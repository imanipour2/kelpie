package com.kelpie.browser.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticatedClientContextTest {
    @Test
    fun missingPrincipalFailsClosed() =
        runBlocking {
            assertNull(verifiedClientId())
        }

    @Test
    fun verifiedClientSurvivesCoroutineDispatchAndDoesNotLeak() =
        runBlocking {
            val trusted = AuthDecision.Authenticated("paired-client-7")
            withContext(AuthenticatedClientContext.fromVerifiedDecision(trusted)) {
                assertEquals("paired-client-7", verifiedClientId())
                withContext(Dispatchers.Default) {
                    assertEquals("paired-client-7", verifiedClientId())
                    assertEquals(
                        "paired-client-7",
                        currentCoroutineContext()[AuthenticatedClientContext]?.clientId,
                    )
                }
            }
            assertNull(verifiedClientId())
        }
}

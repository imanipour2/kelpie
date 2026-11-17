package com.kelpie.browser.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

/** K1 regression: external browser changes the host, not the PKCE/state contract. */
class UOAAuthorizationSecurityTest {
    @Test
    fun authorizationUsesExactRedirectWithPkceS256AndNoClientSecret() {
        val auth = UOAAuthorization()
        val url = URI(auth.url("qa-client"))
        assertEquals("https", url.scheme)
        assertEquals("authentication.unlikeotherai.com", url.host)
        assertEquals("/oauth/authorize", url.path)
        val args =
            url.rawQuery
                .split('&')
                .associate { part ->
                    val chunks = part.split('=', limit = 2)
                    URLDecoder.decode(chunks[0], "UTF-8") to URLDecoder.decode(chunks[1], "UTF-8")
                }
        assertEquals(UOAAuthorization.CALLBACK, args["redirect_uri"])
        assertEquals("code", args["response_type"])
        assertEquals("S256", args["code_challenge_method"])
        assertEquals(auth.challenge, args["code_challenge"])
        assertEquals(auth.state, args["state"])
        assertFalse(args.containsKey("client_secret"))
        assertFalse(args.containsKey("code_verifier"))
    }

    @Test
    fun callbackRejectsWrongStateWrongOriginAndDuplicateCodes() {
        val auth = UOAAuthorization(state = "test-state", verifier = "test-verifier")
        val valid = UOAAuthorization.CALLBACK + "?state=test-state&code=one-code"
        assertEquals("one-code", auth.code(valid))
        val invalid =
            listOf(
                UOAAuthorization.CALLBACK + "?state=other&code=x",
                UOAAuthorization.CALLBACK + "?state=test-state&state=test-state&code=x",
                UOAAuthorization.CALLBACK + "?state=test-state&code=x&code=y",
                UOAAuthorization.CALLBACK + "?state=test-state&code=x&error=denied",
                "https://evil.example/oauth/callback?state=test-state&code=x",
                "com.unlikeotherai.kelpie://oauth/callback/extra?state=test-state&code=x",
                UOAAuthorization.CALLBACK + "?state=test-state&code=",
                UOAAuthorization.CALLBACK + "?state=test-state&code=x#fragment",
            )
        invalid.forEach { candidate ->
            assertTrue("Must reject: " + candidate, runCatching { auth.code(candidate) }.isFailure)
        }
    }

    @Test
    fun distinctAttemptsUseIndependentStateAndVerifier() {
        val a = UOAAuthorization()
        val b = UOAAuthorization()
        assertNotEquals(a.state, b.state)
        assertNotEquals(a.verifier, b.verifier)
        assertNotEquals(a.challenge, b.challenge)
    }
}

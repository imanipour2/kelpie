package com.kelpie.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpJsonTest {
    @Test
    fun malformedOrNonObjectWireInputCannotReachAControlMethod() {
        for (input in listOf("{method:initialize}", "{\"method\":", "[{}]", "null", "\"initialize\"")) {
            assertNull(HttpJson.parseJsonBody(input))
        }
    }

    @Test
    fun nestedValuesAndCorrelatedErrorsRoundTripWithoutImplicitFalseValues() {
        val error = AuthenticatedRequestDispatcher.correlatedError("INTERNAL_ERROR", "Unknown outcome", "opaque-fixture-id")
        assertEquals(error, HttpJson.parseJsonBody(HttpJson.mapToJsonString(error)))
        assertEquals("UNKNOWN", error["outcome"])
        assertNull(error["pending"])
        assertEquals(mapOf("method" to "ping", "params" to emptyMap<String, Any>()), HttpJson.parseJsonBody("{\"method\":\"ping\",\"params\":{}}"))
    }
}

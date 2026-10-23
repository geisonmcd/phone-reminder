package com.geison.phonereminder.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DriveTokenRetryTest {
    @Test
    fun retriesOnceWithFreshTokenAfter401() = runBlocking {
        val usedTokens = mutableListOf<String>()
        val refreshed = mutableListOf<String>()

        val result = DriveTokenRetry.run(
            initialToken = "stale",
            refreshToken = { stale -> refreshed += stale; "fresh" },
        ) { token ->
            usedTokens += token
            if (token == "stale") throw DriveApiException(401, "authError", "expired")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf("stale", "fresh"), usedTokens)
        assertEquals(listOf("stale"), refreshed)
    }

    @Test
    fun doesNotRetryOtherErrors() = runBlocking {
        var refreshCalls = 0
        try {
            DriveTokenRetry.run<String>(
                initialToken = "t",
                refreshToken = { refreshCalls++; "other" },
            ) { throw DriveApiException(403, "accessNotConfigured", "disabled") }
            fail("Expected DriveApiException")
        } catch (error: DriveApiException) {
            assertEquals(403, error.httpCode)
        }
        assertEquals(0, refreshCalls)
    }

    @Test
    fun rethrowsOriginal401WhenNoFreshTokenAvailable() = runBlocking {
        try {
            DriveTokenRetry.run<String>(
                initialToken = "t",
                refreshToken = { null },
            ) { throw DriveApiException(401, null, "expired") }
            fail("Expected DriveApiException")
        } catch (error: DriveApiException) {
            assertEquals(401, error.httpCode)
        }
    }
}

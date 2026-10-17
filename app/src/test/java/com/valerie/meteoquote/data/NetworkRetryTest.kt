package com.valerie.meteoquote.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkRetryTest {
    @Test fun exponentialBackoffThenSuccess() = runTest {
        var calls = 0
        val result = retryNetwork {
            calls++
            if (calls < 3) throw IOException("temporary connection failure")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
        assertEquals(1500L, currentTime)
    }

    @Test fun exhaustionIsBoundedAndKeepsOriginalFailure() = runTest {
        var calls = 0
        val failure = HttpStatusException(503)
        try { retryNetwork { calls++; throw failure }; fail("Expected failure") }
        catch (e: HttpStatusException) { assertSame(failure, e) }
        assertEquals(3, calls)
        assertEquals(1500L, currentTime)
    }

    @Test fun permanentFailuresAreNotRetried() = runTest {
        for (failure in listOf(HttpStatusException(404), HttpStatusException(401),
            HttpStatusException(501), InvalidResponseException(), SSLHandshakeException("certificate"))) {
            var calls = 0
            try { retryNetwork { calls++; throw failure }; fail("Expected failure") }
            catch (e: IOException) { assertSame(failure, e) }
            assertEquals(1, calls)
        }
        assertEquals(0L, currentTime)
    }

    @Test fun cancellationDuringBackoffPreventsNextRequest() = runTest {
        var calls = 0
        val job = launch { retryNetwork { calls++; throw IOException("offline") } }
        runCurrent()
        assertEquals(1, calls)
        job.cancel()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(job.isCancelled)
    }

    @Test fun retryAfterOverridesBackoff() = runTest {
        var calls = 0
        retryNetwork {
            if (++calls == 1) throw HttpStatusException(429, 4000)
        }
        assertEquals(4000L, currentTime)
    }

    @Test fun parsesRetryAfterWithoutOverflow() {
        val now = java.time.Instant.parse("2026-10-17T12:00:00Z")
        assertEquals(5000L, HttpTransport.retryAfterMillis("5", now))
        assertEquals(5000L, HttpTransport.retryAfterMillis("Sat, 17 Oct 2026 12:00:05 GMT", now))
        assertEquals(0L, HttpTransport.retryAfterMillis("Sat, 17 Oct 2026 11:00:00 GMT", now))
        assertNull(HttpTransport.retryAfterMillis("-1", now))
        assertNull(HttpTransport.retryAfterMillis("invalid", now))
        assertTrue(HttpTransport.retryAfterMillis(Long.MAX_VALUE.toString(), now)!! > 30_000L)
    }

    @Test fun totalBudgetCancelsActiveCall() = runTest {
        val client = io.mockk.mockk<okhttp3.OkHttpClient>()
        val call = io.mockk.mockk<okhttp3.Call>(relaxed = true)
        io.mockk.every { client.newCall(any()) } returns call
        io.mockk.every { call.enqueue(any()) } answers { }
        try { HttpTransport(client).get("https://example.invalid"); fail("Expected timeout") }
        catch (_: java.net.SocketTimeoutException) { }
        assertEquals(30_000L, currentTime)
        io.mockk.verify(exactly = 1) { call.cancel() }
        io.mockk.verify(exactly = 1) { client.newCall(any()) }
    }
}

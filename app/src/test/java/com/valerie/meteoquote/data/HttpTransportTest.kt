package com.valerie.meteoquote.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpTransportTest {
    @Test fun successAndHttpErrors() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"ok\":true}"))
            server.enqueue(MockResponse().setResponseCode(404))
            server.start()
            val transport = HttpTransport()
            assertEquals("{\"ok\":true}", transport.get(server.url("/").newBuilder().host("localhost").build().toString()))
            try { transport.get(server.url("/").newBuilder().host("localhost").build().toString()); fail("Expected HTTP failure") }
            catch (e: HttpStatusException) { assertEquals(404, e.statusCode) }
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun timeoutAndCancellation() = runBlocking {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) }
            server.start()
            val client = OkHttpClient.Builder().callTimeout(200, TimeUnit.MILLISECONDS).build()
            try { HttpTransport(client).get(server.url("/").newBuilder().host("localhost").build().toString()); fail("Expected timeout") }
            catch (_: IOException) { }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try {
                withTimeout(100) { HttpTransport().get(server.url("/").newBuilder().host("localhost").build().toString()) }
                fail("Expected cancellation")
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) { }
        }
    }

    @Test fun transientHttpFailureRecoversAndRetryAfterIsRespected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody("recovered"))
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
            server.start()
            assertEquals("recovered", HttpTransport().get(server.url("/").newBuilder().host("localhost").build().toString()))
            assertEquals(3, server.requestCount)
            try { HttpTransport().get(server.url("/").newBuilder().host("localhost").build().toString()); fail("Expected rate limit") }
            catch (e: HttpStatusException) {
                assertEquals(429, e.statusCode)
                assertEquals(60_000L, e.retryAfterMillis)
            }
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun oversizedResponseIsNotRetried() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(2 * 1024 * 1024 + 1)))
            server.start()
            try { HttpTransport().get(server.url("/").newBuilder().host("localhost").build().toString()); fail("Expected body rejection") }
            catch (_: InvalidResponseException) { }
            assertEquals(1, server.requestCount)
        }
    }
}

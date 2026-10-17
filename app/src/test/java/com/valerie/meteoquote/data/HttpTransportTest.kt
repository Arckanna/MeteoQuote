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
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()
            val transport = HttpTransport()
            assertEquals("{\"ok\":true}", transport.get(server.url("/").newBuilder().host("localhost").build().toString()))
            repeat(2) {
                try { transport.get(server.url("/").newBuilder().host("localhost").build().toString()); fail("Expected HTTP failure") }
                catch (_: IOException) { }
            }
        }
    }

    @Test fun timeoutAndCancellation() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
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
}

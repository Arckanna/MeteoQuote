package com.valerie.meteoquote.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared connections, bounded calls, no logging of coordinates or response bodies. */
class HttpTransport(private val client: OkHttpClient = sharedClient) {
    suspend fun get(url: String): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        require(request.url.isHttps || request.url.host == "localhost")
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException(
                            if (it.code == 429) "Service temporairement limité. Réessayez plus tard."
                            else "Service indisponible (HTTP ${it.code})"
                        )
                        val body = it.body ?: throw IOException("Réponse vide")
                        body.source().request(MAX_BYTES + 1)
                        if (body.source().buffer.size > MAX_BYTES) throw IOException("Réponse trop volumineuse")
                        body.string()
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    companion object {
        private const val MAX_BYTES = 2L * 1024 * 1024
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}

package com.valerie.meteoquote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Called only after the Activity has obtained foreground coarse permission. */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): Location? = withTimeoutOrNull(15_000) {
    suspendCancellableCoroutine { continuation ->
        val token = CancellationTokenSource()
        continuation.invokeOnCancellation { token.cancel() }
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(120_000)
            .setDurationMillis(12_000)
            .build()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(request, token.token)
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            .addOnCanceledListener { continuation.cancel() }
    }
}

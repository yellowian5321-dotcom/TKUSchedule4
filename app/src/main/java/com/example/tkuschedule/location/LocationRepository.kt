package com.example.tkuschedule.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    var lastLocationTimeMillis: Long? = null
        private set
    var lastLocationAccuracyMeters: Float? = null
        private set

    private fun hasFinePermission() = ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission() = hasFinePermission() || ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled() = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .any { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(forceFresh: Boolean = false): Result<GeoPoint> {
        if (!hasLocationPermission()) return Result.failure(SecurityException("尚未允許位置權限"))
        if (!isLocationEnabled()) return Result.failure(IllegalStateException("手機定位功能尚未開啟"))
        return try {
            // 自動更新優先使用 30 秒內的位置；手動重新定位仍要求新位置。
            val cached = if (forceFresh) null else readRecentLocation(30_000_000_000L)
            val fresh = if (cached == null) withContext(Dispatchers.Main.immediate) {
                getFreshLocation()
            } else null
            val location = cached ?: fresh ?: readRecentLocation(120_000_000_000L)
            ?: error("12 秒內未取得位置。請開啟精確位置，並靠近窗戶或到室外後重新定位。")
            lastLocationTimeMillis = location.time
            lastLocationAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null
            Result.success(GeoPoint(location.latitude, location.longitude))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun isRecent(location: Location): Boolean {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return ageNanos in 0..120_000_000_000L
    }

    @SuppressLint("MissingPermission")
    private suspend fun readRecentLocation(maxAgeNanos: Long): Location? = withContext(Dispatchers.IO) {
        availableProviders().mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { location ->
            val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            age in 0L..maxAgeNanos
        }.maxByOrNull { it.elapsedRealtimeNanos }
    }

    private fun availableProviders(): List<String> = buildList {
        add(LocationManager.NETWORK_PROVIDER)
        if (hasFinePermission()) add(LocationManager.GPS_PROVIDER)
    }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(): Location? = withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val providers = availableProviders()
            if (providers.isEmpty()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive && isRecent(location)) {
                        runCatching { manager.removeUpdates(this) }
                        continuation.resume(location)
                    }
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            var registered = false
            providers.forEach { provider ->
                if (continuation.isActive) {
                    // 一個來源失敗時，仍保留其他來源的定位請求。
                    val success = runCatching {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    }.isSuccess
                    registered = registered || success
                }
            }
            if (!registered && continuation.isActive) continuation.resume(null)
        }
    }
}
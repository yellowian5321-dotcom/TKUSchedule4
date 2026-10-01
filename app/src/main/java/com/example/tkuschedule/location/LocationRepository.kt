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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationRepository(
    context: Context
) {
    private val applicationContext =
        context.applicationContext

    private val locationManager =
        applicationContext.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    fun hasLocationPermission(): Boolean {
        val fineLocationGranted =
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted =
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return fineLocationGranted ||
                coarseLocationGranted
    }

    fun isLocationEnabled(): Boolean {
        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        }.getOrDefault(false)

        val networkEnabled = runCatching {
            locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )
        }.getOrDefault(false)

        return gpsEnabled || networkEnabled
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation():
            Result<GeoPoint> {

        if (!hasLocationPermission()) {
            return Result.failure(
                SecurityException(
                    "尚未允許位置權限"
                )
            )
        }

        if (!isLocationEnabled()) {
            return Result.failure(
                IllegalStateException(
                    "手機定位功能尚未開啟"
                )
            )
        }

        return runCatching {
            val location =
                getFreshLocation()
                    ?: getBestLastKnownLocation()
                    ?: error(
                        "暫時無法取得目前位置，" +
                                "請到室外或靠近窗戶後再試一次"
                    )

            GeoPoint(
                latitude =
                    location.latitude,
                longitude =
                    location.longitude
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation():
            Location? {

        return withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine {
                    continuation ->

                val providers =
                    availableProviders()

                if (providers.isEmpty()) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                var isCompleted = false

                lateinit var listener:
                        LocationListener

                fun finish(location: Location?) {
                    if (isCompleted) {
                        return
                    }

                    isCompleted = true

                    runCatching {
                        locationManager
                            .removeUpdates(listener)
                    }

                    if (
                        continuation.isActive
                    ) {
                        continuation.resume(
                            location
                        )
                    }
                }

                listener =
                    object : LocationListener {

                        override fun onLocationChanged(
                            location: Location
                        ) {
                            finish(location)
                        }

                        override fun onProviderDisabled(
                            provider: String
                        ) {
                            if (
                                availableProviders()
                                    .isEmpty()
                            ) {
                                finish(null)
                            }
                        }

                        override fun onProviderEnabled(
                            provider: String
                        ) = Unit

                        @Deprecated(
                            "Deprecated in Android"
                        )
                        override fun onStatusChanged(
                            provider: String?,
                            status: Int,
                            extras: Bundle?
                        ) = Unit
                    }

                try {
                    providers.forEach {
                            provider ->

                        locationManager
                            .requestLocationUpdates(
                                provider,
                                0L,
                                0f,
                                listener,
                                Looper.getMainLooper()
                            )
                    }
                } catch (
                    exception: Exception
                ) {
                    runCatching {
                        locationManager
                            .removeUpdates(listener)
                    }

                    if (
                        continuation.isActive
                    ) {
                        continuation
                            .resumeWithException(
                                exception
                            )
                    }
                }

                continuation
                    .invokeOnCancellation {
                        runCatching {
                            locationManager
                                .removeUpdates(
                                    listener
                                )
                        }
                    }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation():
            Location? {

        return availableProviders()
            .mapNotNull { provider ->
                runCatching {
                    locationManager
                        .getLastKnownLocation(
                            provider
                        )
                }.getOrNull()
            }
            .maxByOrNull {
                it.time
            }
    }

    private fun availableProviders():
            List<String> {

        return listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).filter { provider ->

            runCatching {
                locationManager.isProviderEnabled(
                    provider
                )
            }.getOrDefault(false)
        }
    }
}

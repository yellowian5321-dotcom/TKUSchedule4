package com.example.tkuschedule.location

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class ClassroomGeocoder(
    context: Context
) {
    private val applicationContext =
        context.applicationContext

    private val geocoder =
        Geocoder(
            applicationContext,
            Locale.TAIWAN
        )

    suspend fun findLocation(
        destination: ClassroomDestination
    ): Result<GeoPoint> {

        return withContext(
            Dispatchers.IO
        ) {
            runCatching {
                if (!Geocoder.isPresent()) {
                    error(
                        "這台手機目前無法使用地址定位服務"
                    )
                }

                @Suppress("DEPRECATION")
                val addresses =
                    geocoder.getFromLocationName(
                        destination.searchQuery,
                        1
                    )

                val address =
                    addresses?.firstOrNull()
                        ?: error(
                            "找不到${destination.buildingName}的位置"
                        )

                GeoPoint(
                    latitude =
                        address.latitude,
                    longitude =
                        address.longitude
                )
            }
        }
    }
}
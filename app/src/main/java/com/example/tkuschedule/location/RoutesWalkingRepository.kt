package com.example.tkuschedule.location

import com.example.tkuschedule.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class RoutesWalkingRepository {

    companion object {

        private const val ROUTES_URL =
            "https://routes.googleapis.com/" +
                    "directions/v2:computeRoutes"

        /*
         * 進入大樓、搭電梯及找教室的緩衝。
         */
        private const val INDOOR_BUFFER_MINUTES =
            3
    }

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                20,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                15,
                TimeUnit.SECONDS
            )
            .build()

    suspend fun estimateWalkingTime(
        currentLocation: GeoPoint,
        classroomLocation: GeoPoint,
        minutesUntilClass: Long
    ): Result<WalkingEstimate> {

        return withContext(
            Dispatchers.IO
        ) {
            runCatching {

                val apiKey =
                    BuildConfig
                        .ROUTES_API_KEY
                        .trim()

                if (apiKey.isBlank()) {

                    error(
                        "尚未設定 Routes API Key"
                    )
                }

                val requestJson =
                    createRequestJson(
                        currentLocation =
                            currentLocation,

                        classroomLocation =
                            classroomLocation
                    )

                val requestBody =
                    requestJson
                        .toString()
                        .toRequestBody(
                            "application/json; charset=utf-8"
                                .toMediaType()
                        )

                val request =
                    Request
                        .Builder()
                        .url(
                            ROUTES_URL
                        )
                        .post(
                            requestBody
                        )
                        .addHeader(
                            "Content-Type",
                            "application/json"
                        )
                        .addHeader(
                            "X-Goog-Api-Key",
                            apiKey
                        )
                        .addHeader(
                            "X-Goog-FieldMask",
                            "routes.duration," +
                                    "routes.distanceMeters"
                        )
                        .build()

                client
                    .newCall(request)
                    .execute()
                    .use { response ->

                        val responseText =
                            response
                                .body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {

                            val googleMessage =
                                parseGoogleError(
                                    responseText
                                )

                            error(
                                googleMessage
                                    ?: "Routes API 連線失敗，" +
                                    "錯誤代碼 ${response.code}"
                            )
                        }

                        parseWalkingEstimate(
                            responseText =
                                responseText,

                            minutesUntilClass =
                                minutesUntilClass
                        )
                    }
            }
        }
    }

    private fun createRequestJson(
        currentLocation: GeoPoint,
        classroomLocation: GeoPoint
    ): JSONObject {

        val originLatLng =
            JSONObject().apply {

                put(
                    "latitude",
                    currentLocation.latitude
                )

                put(
                    "longitude",
                    currentLocation.longitude
                )
            }

        val destinationLatLng =
            JSONObject().apply {

                put(
                    "latitude",
                    classroomLocation.latitude
                )

                put(
                    "longitude",
                    classroomLocation.longitude
                )
            }

        val origin =
            JSONObject().apply {

                put(
                    "location",
                    JSONObject().apply {

                        put(
                            "latLng",
                            originLatLng
                        )
                    }
                )
            }

        val destination =
            JSONObject().apply {

                put(
                    "location",
                    JSONObject().apply {

                        put(
                            "latLng",
                            destinationLatLng
                        )
                    }
                )
            }

        return JSONObject().apply {

            put(
                "origin",
                origin
            )

            put(
                "destination",
                destination
            )

            put(
                "travelMode",
                "WALK"
            )

            put(
                "languageCode",
                "zh-TW"
            )

            put(
                "units",
                "METRIC"
            )
        }
    }

    private fun parseWalkingEstimate(
        responseText: String,
        minutesUntilClass: Long
    ): WalkingEstimate {

        val root =
            JSONObject(
                responseText
            )

        val routes =
            root.getJSONArray(
                "routes"
            )

        if (routes.length() == 0) {

            error(
                "Google Routes 找不到可步行路線"
            )
        }

        val route =
            routes.getJSONObject(0)

        val distanceMeters =
            route.optInt(
                "distanceMeters",
                0
            )

        val durationText =
            route.optString(
                "duration"
            )

        val durationSeconds =
            durationText
                .removeSuffix("s")
                .toDoubleOrNull()
                ?: error(
                    "無法讀取 Routes 步行時間"
                )

        val routeWalkingMinutes =
            ceil(
                durationSeconds / 60.0
            ).toInt()

        val totalWalkingMinutes =
            routeWalkingMinutes +
                    INDOOR_BUFFER_MINUTES

        val departureMinutes =
            minutesUntilClass -
                    totalWalkingMinutes

        return WalkingEstimate(
            distanceMeters =
                distanceMeters,

            walkingMinutes =
                totalWalkingMinutes,

            suggestedDepartureMinutes =
                departureMinutes,

            canArriveOnTime =
                departureMinutes >= 0
        )
    }

    private fun parseGoogleError(
        responseText: String
    ): String? {

        return runCatching {

            JSONObject(
                responseText
            )
                .optJSONObject(
                    "error"
                )
                ?.optString(
                    "message"
                )
                ?.takeIf {
                    it.isNotBlank()
                }
        }.getOrNull()
    }
}

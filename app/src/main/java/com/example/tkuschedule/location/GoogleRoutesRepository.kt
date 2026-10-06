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

/*
 * Google Routes API 回傳的步行路線資料。
 */
data class GoogleWalkingRoute(
    val distanceMeters: Int,
    val outdoorWalkingMinutes: Int
)

/*
 * 使用 Google Routes API 計算實際步行路線。
 *
 * 起點：
 * 手機目前位置。
 *
 * 終點：
 * 下一堂課所在大樓位置。
 */
class GoogleRoutesRepository(
    private val apiKey: String =
        BuildConfig.ROUTES_API_KEY
) {
    companion object {
        private const val ROUTES_URL =
            "https://routes.googleapis.com/" +
                    "directions/v2:computeRoutes"

        /*
         * 預留進入大樓、搭電梯、
         * 上樓與尋找教室的時間。
         */
        private const val INDOOR_BUFFER_MINUTES = 3

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8"
                .toMediaType()
    }

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                20,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                20,
                TimeUnit.SECONDS
            )
            .callTimeout(
                30,
                TimeUnit.SECONDS
            )
            .build()

    /*
     * 計算完整步行資料。
     *
     * minutesUntilClass：
     * 距離下一堂課還有幾分鐘。
     */
    suspend fun calculateWalkingTime(
        currentLocation: GeoPoint,
        classroomLocation: GeoPoint,
        minutesUntilClass: Long
    ): Result<WalkingEstimate> {
        return withContext(
            Dispatchers.IO
        ) {
            runCatching {
                checkApiKey()

                val route =
                    requestWalkingRoute(
                        origin =
                            currentLocation,
                        destination =
                            classroomLocation
                    )

                val totalWalkingMinutes =
                    route.outdoorWalkingMinutes +
                            INDOOR_BUFFER_MINUTES

                val suggestedDepartureMinutes =
                    minutesUntilClass -
                            totalWalkingMinutes

                WalkingEstimate(
                    distanceMeters =
                        route.distanceMeters,
                    walkingMinutes =
                        totalWalkingMinutes,
                    suggestedDepartureMinutes =
                        suggestedDepartureMinutes,
                    canArriveOnTime =
                        suggestedDepartureMinutes >= 0
                )
            }
        }
    }

    /*
     * 呼叫 Google Routes API。
     */
    private fun requestWalkingRoute(
        origin: GeoPoint,
        destination: GeoPoint
    ): GoogleWalkingRoute {
        val requestJson =
            createRequestJson(
                origin = origin,
                destination = destination
            )

        val request =
            Request.Builder()
                .url(ROUTES_URL)
                .post(
                    requestJson
                        .toString()
                        .toRequestBody(
                            JSON_MEDIA_TYPE
                        )
                )
                .header(
                    "Content-Type",
                    "application/json"
                )
                .header(
                    "X-Goog-Api-Key",
                    apiKey
                )
                .header(
                    "X-Goog-FieldMask",
                    "routes.duration," +
                            "routes.distanceMeters"
                )
                .build()

        return httpClient
            .newCall(request)
            .execute()
            .use { response ->
                val responseText =
                    response.body?.string()
                        .orEmpty()

                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        createApiErrorMessage(
                            httpCode = response.code,
                            responseText = responseText
                        )
                    )
                }

                parseWalkingRoute(
                    responseText = responseText
                )
            }
    }

    /*
     * 建立 Routes API 要求內容。
     */
    private fun createRequestJson(
        origin: GeoPoint,
        destination: GeoPoint
    ): JSONObject {
        val originJson =
            createWaypointJson(origin)

        val destinationJson =
            createWaypointJson(destination)

        return JSONObject().apply {
            put(
                "origin",
                originJson
            )

            put(
                "destination",
                destinationJson
            )

            /*
             * 指定使用步行模式。
             */
            put(
                "travelMode",
                "WALK"
            )

            put(
                "computeAlternativeRoutes",
                false
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

    /*
     * 將經緯度轉成 Routes API 所需格式。
     */
    private fun createWaypointJson(
        point: GeoPoint
    ): JSONObject {
        val latitudeLongitudeJson =
            JSONObject().apply {
                put(
                    "latitude",
                    point.latitude
                )

                put(
                    "longitude",
                    point.longitude
                )
            }

        val locationJson =
            JSONObject().apply {
                put(
                    "latLng",
                    latitudeLongitudeJson
                )
            }

        return JSONObject().apply {
            put(
                "location",
                locationJson
            )
        }
    }

    /*
     * 解析 Google 回傳的距離與時間。
     */
    private fun parseWalkingRoute(
        responseText: String
    ): GoogleWalkingRoute {
        if (responseText.isBlank()) {
            throw IllegalStateException(
                "Google Routes 沒有回傳資料"
            )
        }

        val rootJson =
            JSONObject(responseText)

        val routes =
            rootJson.optJSONArray("routes")
                ?: throw IllegalStateException(
                    "Google 找不到可使用的步行路線"
                )

        if (routes.length() == 0) {
            throw IllegalStateException(
                "Google 找不到可使用的步行路線"
            )
        }

        val firstRoute =
            routes.getJSONObject(0)

        val distanceMeters =
            firstRoute.optInt(
                "distanceMeters",
                -1
            )

        val durationText =
            firstRoute.optString(
                "duration"
            )

        if (distanceMeters < 0) {
            throw IllegalStateException(
                "Google Routes 沒有提供步行距離"
            )
        }

        val durationSeconds =
            parseDurationSeconds(
                durationText
            )

        /*
         * 不足一分鐘仍以一分鐘顯示。
         */
        val outdoorWalkingMinutes =
            ceil(
                durationSeconds / 60.0
            )
                .toInt()
                .coerceAtLeast(1)

        return GoogleWalkingRoute(
            distanceMeters =
                distanceMeters,
            outdoorWalkingMinutes =
                outdoorWalkingMinutes
        )
    }

    /*
     * Google 的 duration 格式通常是：
     *
     * 245s
     * 245.5s
     */
    private fun parseDurationSeconds(
        durationText: String
    ): Double {
        if (
            durationText.isBlank() ||
            !durationText.endsWith("s")
        ) {
            throw IllegalStateException(
                "Google Routes 回傳的步行時間格式錯誤"
            )
        }

        return durationText
            .removeSuffix("s")
            .toDoubleOrNull()
            ?: throw IllegalStateException(
                "無法讀取 Google Routes 的步行時間"
            )
    }

    /*
     * 檢查 local.properties 是否成功載入金鑰。
     */
    private fun checkApiKey() {
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "尚未設定 ROUTES_API_KEY，" +
                        "請檢查 local.properties"
            )
        }
    }

    /*
     * 將常見錯誤轉成比較容易理解的訊息。
     */
    private fun createApiErrorMessage(
        httpCode: Int,
        responseText: String
    ): String {
        val googleMessage =
            runCatching {
                JSONObject(responseText)
                    .optJSONObject("error")
                    ?.optString("message")
                    .orEmpty()
            }.getOrDefault("")

        val basicMessage =
            when (httpCode) {
                400 ->
                    "Google Routes 要求格式錯誤"

                401 ->
                    "Google Routes API 金鑰無效"

                403 ->
                    "Google Routes API 沒有權限，" +
                            "請確認 API 已啟用及金鑰限制"

                429 ->
                    "Google Routes API 使用次數過多，" +
                            "請稍後再試"

                500,
                502,
                503,
                504 ->
                    "Google Routes 服務暫時無法使用"

                else ->
                    "Google Routes 連線失敗，" +
                            "錯誤代碼：$httpCode"
            }

        return if (googleMessage.isBlank()) {
            basicMessage
        } else {
            "$basicMessage\n$googleMessage"
        }
    }
}
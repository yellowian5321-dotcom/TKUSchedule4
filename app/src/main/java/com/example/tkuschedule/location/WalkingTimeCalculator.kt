package com.example.tkuschedule.location

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class WalkingEstimate(
    val distanceMeters: Int,
    val walkingMinutes: Int,
    val suggestedDepartureMinutes: Long,
    val canArriveOnTime: Boolean
)

object WalkingTimeCalculator {

    /*
     * 一般步行速度約為每分鐘 75 公尺。
     */
    private const val WALKING_METERS_PER_MINUTE =
        75.0

    /*
     * 預留進入大樓、找樓層與找教室的時間。
     */
    private const val INDOOR_BUFFER_MINUTES =
        3

    private const val EARTH_RADIUS_METERS =
        6_371_000.0

    fun estimate(
        currentLocation: GeoPoint,
        classroomLocation: GeoPoint,
        minutesUntilClass: Long
    ): WalkingEstimate {

        val straightDistance =
            calculateDistanceMeters(
                start = currentLocation,
                end = classroomLocation
            )

        /*
         * 校園道路不會是完全直線，
         * 暫時用 1.25 倍修正實際步行距離。
         */
        val estimatedRouteDistance =
            straightDistance * 1.25

        val outdoorWalkingMinutes =
            ceil(
                estimatedRouteDistance /
                        WALKING_METERS_PER_MINUTE
            ).toInt()

        val totalWalkingMinutes =
            outdoorWalkingMinutes +
                    INDOOR_BUFFER_MINUTES

        val suggestedDepartureMinutes =
            minutesUntilClass -
                    totalWalkingMinutes

        return WalkingEstimate(
            distanceMeters =
                estimatedRouteDistance
                    .toInt(),
            walkingMinutes =
                totalWalkingMinutes,
            suggestedDepartureMinutes =
                suggestedDepartureMinutes,
            canArriveOnTime =
                suggestedDepartureMinutes >= 0
        )
    }

    private fun calculateDistanceMeters(
        start: GeoPoint,
        end: GeoPoint
    ): Double {

        val latitude1 =
            Math.toRadians(start.latitude)

        val latitude2 =
            Math.toRadians(end.latitude)

        val latitudeDifference =
            Math.toRadians(
                end.latitude -
                        start.latitude
            )

        val longitudeDifference =
            Math.toRadians(
                end.longitude -
                        start.longitude
            )

        val a =
            sin(latitudeDifference / 2) *
                    sin(latitudeDifference / 2) +
                    cos(latitude1) *
                    cos(latitude2) *
                    sin(longitudeDifference / 2) *
                    sin(longitudeDifference / 2)

        val c =
            2 * atan2(
                sqrt(a),
                sqrt(1 - a)
            )

        return EARTH_RADIUS_METERS * c
    }
}


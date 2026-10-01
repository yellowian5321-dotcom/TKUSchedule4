package com.example.tkuschedule.location

import com.example.tkuschedule.data.Course
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

object NextClassCalculator {

    private val taipeiZone =
        ZoneId.of("Asia/Taipei")

    private val periodStartTimes = mapOf(
        "1" to LocalTime.of(8, 10),
        "2" to LocalTime.of(9, 10),
        "3" to LocalTime.of(10, 10),
        "4" to LocalTime.of(11, 10),
        "5" to LocalTime.of(12, 10),
        "6" to LocalTime.of(13, 10),
        "7" to LocalTime.of(14, 10),
        "8" to LocalTime.of(15, 10),
        "9" to LocalTime.of(16, 10),
        "10" to LocalTime.of(17, 10),
        "11" to LocalTime.of(18, 10),
        "12" to LocalTime.of(19, 10),
        "13" to LocalTime.of(20, 10),
        "14" to LocalTime.of(21, 10)
    )

    fun findNextClass(
        courses: List<Course>,
        now: LocalDateTime =
            LocalDateTime.now(taipeiZone)
    ): NextClassInfo? {

        if (courses.isEmpty()) {
            return null
        }

        val possibleClasses = courses.mapNotNull { course ->
            createUpcomingClassTime(
                course = course,
                now = now
            )
        }

        return possibleClasses
            .minByOrNull {
                it.startDateTime
            }
    }

    private fun createUpcomingClassTime(
        course: Course,
        now: LocalDateTime
    ): NextClassInfo? {

        val weekday = course.weekday

        if (weekday !in 1..7) {
            return null
        }

        val firstPeriod = course.periods
            .map(::normalizePeriod)
            .minByOrNull(::periodNumber)
            ?: return null

        val startTime =
            periodStartTimes[firstPeriod]
                ?: return null

        val targetDayOfWeek =
            DayOfWeek.of(weekday)

        var classDate = findNextDate(
            currentDate = now.toLocalDate(),
            targetDayOfWeek = targetDayOfWeek
        )

        var classDateTime =
            LocalDateTime.of(
                classDate,
                startTime
            )

        /*
         * 如果今天有這堂課，但上課時間已經過了，
         * 就找下個星期同一天。
         */
        if (!classDateTime.isAfter(now)) {
            classDate = classDate.plusWeeks(1)

            classDateTime =
                LocalDateTime.of(
                    classDate,
                    startTime
                )
        }

        val minutesUntilClass =
            java.time.Duration
                .between(
                    now,
                    classDateTime
                )
                .toMinutes()

        return NextClassInfo(
            course = course,
            startDateTime = classDateTime,
            minutesUntilClass =
                minutesUntilClass
        )
    }

    private fun findNextDate(
        currentDate: LocalDate,
        targetDayOfWeek: DayOfWeek
    ): LocalDate {

        val currentDay =
            currentDate.dayOfWeek

        return if (
            currentDay == targetDayOfWeek
        ) {
            currentDate
        } else {
            currentDate.with(
                TemporalAdjusters.next(
                    targetDayOfWeek
                )
            )
        }
    }

    private fun normalizePeriod(
        value: String
    ): String {
        return value
            .trim()
            .replace("第", "")
            .replace("節", "")
            .trim()
    }

    private fun periodNumber(
        value: String
    ): Int {
        return value.toIntOrNull()
            ?: Int.MAX_VALUE
    }
}
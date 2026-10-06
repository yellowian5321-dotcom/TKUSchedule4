package com.example.tkuschedule.reminder

import com.example.tkuschedule.data.Course
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class ReminderSession(
    val id: String,
    val courseName: String,
    val classroom: String,
    val teacher: String,
    val weekday: Int,
    val startPeriod: Int
)

data class ReminderMoment(
    val classStartMillis: Long,
    val reminderMillis: Long
)

object ClassReminderPlan {
    // 保留原本常數，供既有程式相容使用。
    const val MINUTES_BEFORE = 15L

    const val DEFAULT_MINUTES_BEFORE = 15
    const val MIN_MINUTES_BEFORE = 1
    const val MAX_MINUTES_BEFORE = 180

    val zone: ZoneId = ZoneId.of("Asia/Taipei")

    fun sessions(
        courses: List<Course>
    ): List<ReminderSession> {
        return courses
            .filter {
                it.weekday in 1..7
            }
            .groupBy { course ->
                val offering =
                    if (course.courseNo.isNotBlank()) {
                        course.courseNo
                    } else {
                        "${course.subjectCode}|" +
                                "${course.className}|" +
                                course.courseName
                    }

                "${course.semester}|" +
                        "${course.departmentCode}|" +
                        "$offering|" +
                        "${course.weekday}|" +
                        "${course.classroom}|" +
                        course.teacher
            }
            .flatMap { (key, entries) ->
                val course = entries.first()

                val periods = entries
                    .flatMap {
                        it.periods
                    }
                    .mapNotNull {
                        it.trim()
                            .replace("第", "")
                            .replace("節", "")
                            .trim()
                            .toIntOrNull()
                    }
                    .filter {
                        it in 1..14
                    }
                    .distinct()
                    .sorted()

                periods
                    .filterIndexed { index, period ->
                        index == 0 ||
                                period != periods[index - 1] + 1
                    }
                    .map { firstPeriod ->
                        ReminderSession(
                            id = digest("$key|$firstPeriod"),
                            courseName = course.courseName,
                            classroom = course.classroom,
                            teacher = course.teacher,
                            weekday = course.weekday,
                            startPeriod = firstPeriod
                        )
                    }
            }
            .sortedBy {
                it.id
            }
    }

    fun nextMoment(
        session: ReminderSession,
        nowMillis: Long = System.currentTimeMillis(),
        deliveredClassStartMillis: Long = 0L,
        minutesBefore: Int = DEFAULT_MINUTES_BEFORE
    ): ReminderMoment {
        require(
            session.weekday in 1..7 &&
                    session.startPeriod in 1..14
        )

        require(
            minutesBefore in
                    MIN_MINUTES_BEFORE..MAX_MINUTES_BEFORE
        ) {
            "提醒時間請設定為 " +
                    "$MIN_MINUTES_BEFORE～" +
                    "$MAX_MINUTES_BEFORE 分鐘"
        }

        val now = Instant
            .ofEpochMilli(nowMillis)
            .atZone(zone)

        val date = now
            .toLocalDate()
            .with(
                TemporalAdjusters.nextOrSame(
                    DayOfWeek.of(session.weekday)
                )
            )

        var start = date
            .atTime(
                LocalTime.of(
                    7 + session.startPeriod,
                    10
                )
            )
            .atZone(zone)

        if (
            !start.isAfter(now.plusSeconds(2)) ||
            start.toInstant().toEpochMilli() <=
            deliveredClassStartMillis
        ) {
            start = start.plusWeeks(1)
        }

        val regularReminder = start
            .minusMinutes(minutesBefore.toLong())
            .toInstant()
            .toEpochMilli()

        // 若已進入設定的提醒範圍，安排兩秒後補發。
        val reminder =
            if (regularReminder <= nowMillis) {
                nowMillis + Duration.ofSeconds(2).toMillis()
            } else {
                regularReminder
            }

        return ReminderMoment(
            classStartMillis =
                start.toInstant().toEpochMilli(),
            reminderMillis = reminder
        )
    }

    private fun digest(
        value: String
    ): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
    }
}
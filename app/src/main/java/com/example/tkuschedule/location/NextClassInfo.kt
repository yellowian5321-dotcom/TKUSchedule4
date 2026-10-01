package com.example.tkuschedule.location

import com.example.tkuschedule.data.Course
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class NextClassInfo(
    val course: Course,
    val startDateTime: LocalDateTime,
    val minutesUntilClass: Long
) {
    val courseName: String
        get() = course.courseName

    val classroom: String
        get() = course.classroom.ifBlank {
            "教室未公告"
        }

    val teacher: String
        get() = course.teacher.ifBlank {
            "教師未公告"
        }

    val startTimeText: String
        get() = startDateTime.format(
            DateTimeFormatter.ofPattern(
                "HH:mm"
            )
        )
}
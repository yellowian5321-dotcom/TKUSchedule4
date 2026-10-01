package com.example.tkuschedule.data

data class Course(
    val id: String,
    val semester: String,
    val departmentCode: String,
    val departmentName: String,
    val grade: Int,
    val className: String,
    val courseNo: String,
    val subjectCode: String,
    val courseName: String,
    val teacher: String,
    val required: Boolean,
    val credits: Double,
    val weekday: Int,
    val periods: List<String>,
    val classroom: String
) {
    val weekdayText: String
        get() = when (weekday) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            7 -> "星期日"
            else -> "未排定"
        }

    val periodsText: String
        get() = periods.joinToString("、")

    val requiredText: String
        get() = if (required) "必修" else "選修"

    val creditsText: String
        get() = if (credits % 1.0 == 0.0) {
            credits.toInt().toString()
        } else {
            credits.toString()
        }
}
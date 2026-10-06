package com.example.tkuschedule.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 儲存與讀取使用者課表。
 *
 * 使用 SharedPreferences 儲存在 App 內部，
 * 關閉 App 或手機重新開機後資料仍會存在。
 */
class ScheduleStorage(
    context: Context
) {

    companion object {
        private const val PREFERENCES_NAME =
            "tku_schedule_storage"

        private const val KEY_LOADED_COURSES =
            "loaded_courses"

        private const val KEY_MANUAL_COURSES =
            "manual_courses"

        private const val KEY_SEMESTER =
            "semester"

        private const val KEY_DEPARTMENT_CODE =
            "department_code"

        private const val KEY_GRADE =
            "grade"

        private const val KEY_CLASS_NAME =
            "class_name"

        private const val NO_GRADE = -1
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    /**
     * 儲存整份課表。
     */
    fun save(
        loadedCourses: List<Course>,
        manualCourses: List<Course>,
        semester: String,
        selectedDepartmentCode: String,
        selectedGrade: Int?,
        selectedClassName: String
    ) {
        val loadedJson =
            coursesToJson(
                loadedCourses
            ).toString()

        val manualJson =
            coursesToJson(
                manualCourses
            ).toString()

        preferences
            .edit()
            .putString(
                KEY_LOADED_COURSES,
                loadedJson
            )
            .putString(
                KEY_MANUAL_COURSES,
                manualJson
            )
            .putString(
                KEY_SEMESTER,
                semester
            )
            .putString(
                KEY_DEPARTMENT_CODE,
                selectedDepartmentCode
            )
            .putInt(
                KEY_GRADE,
                selectedGrade
                    ?: NO_GRADE
            )
            .putString(
                KEY_CLASS_NAME,
                selectedClassName
            )
            .apply()
    }

    /**
     * 讀取之前儲存的課表。
     */
    fun load(): SavedSchedule {
        val loadedCourses =
            jsonToCourses(
                preferences.getString(
                    KEY_LOADED_COURSES,
                    null
                )
            )

        val manualCourses =
            jsonToCourses(
                preferences.getString(
                    KEY_MANUAL_COURSES,
                    null
                )
            )

        val savedGrade =
            preferences.getInt(
                KEY_GRADE,
                NO_GRADE
            )

        return SavedSchedule(
            loadedCourses =
                loadedCourses,
            manualCourses =
                manualCourses,
            semester =
                preferences.getString(
                    KEY_SEMESTER,
                    ""
                ).orEmpty(),
            selectedDepartmentCode =
                preferences.getString(
                    KEY_DEPARTMENT_CODE,
                    ""
                ).orEmpty(),
            selectedGrade =
                if (
                    savedGrade ==
                    NO_GRADE
                ) {
                    null
                } else {
                    savedGrade
                },
            selectedClassName =
                preferences.getString(
                    KEY_CLASS_NAME,
                    ""
                ).orEmpty()
        )
    }

    /**
     * 清除已儲存的課表。
     */
    fun clear() {
        preferences
            .edit()
            .clear()
            .apply()
    }

    /**
     * 將課程清單轉成 JSON。
     */
    private fun coursesToJson(
        courses: List<Course>
    ): JSONArray {
        val array =
            JSONArray()

        courses
            .distinctBy {
                it.id
            }
            .forEach { course ->
                array.put(
                    courseToJson(
                        course
                    )
                )
            }

        return array
    }

    /**
     * 將一筆課程轉成 JSON。
     */
    private fun courseToJson(
        course: Course
    ): JSONObject {
        return JSONObject().apply {
            put(
                "id",
                course.id
            )

            put(
                "semester",
                course.semester
            )

            put(
                "departmentCode",
                course.departmentCode
            )

            put(
                "departmentName",
                course.departmentName
            )

            put(
                "grade",
                course.grade
            )

            put(
                "className",
                course.className
            )

            put(
                "courseNo",
                course.courseNo
            )

            put(
                "subjectCode",
                course.subjectCode
            )

            put(
                "courseName",
                course.courseName
            )

            put(
                "teacher",
                course.teacher
            )

            put(
                "required",
                course.required
            )

            put(
                "credits",
                course.credits
            )

            put(
                "weekday",
                course.weekday
            )

            put(
                "periods",
                JSONArray(
                    course.periods
                )
            )

            put(
                "classroom",
                course.classroom
            )
        }
    }

    /**
     * 將儲存的 JSON 還原成課程清單。
     *
     * 如果其中一筆資料損壞，
     * 只略過損壞的資料，不會讓 App 閃退。
     */
    private fun jsonToCourses(
        jsonText: String?
    ): List<Course> {
        if (jsonText.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            val array =
                JSONArray(jsonText)

            buildList {
                for (
                index in
                0 until array.length()
                ) {
                    val jsonObject =
                        array.optJSONObject(
                            index
                        ) ?: continue

                    jsonToCourse(
                        jsonObject
                    )?.let {
                        add(it)
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    /**
     * 將一筆 JSON 還原成 Course。
     */
    private fun jsonToCourse(
        json: JSONObject
    ): Course? {
        return runCatching {
            Course(
                id =
                    json.optString(
                        "id"
                    ),
                semester =
                    json.optString(
                        "semester"
                    ),
                departmentCode =
                    json.optString(
                        "departmentCode"
                    ),
                departmentName =
                    json.optString(
                        "departmentName"
                    ),
                grade =
                    json.optInt(
                        "grade",
                        0
                    ),
                className =
                    json.optString(
                        "className"
                    ),
                courseNo =
                    json.optString(
                        "courseNo"
                    ),
                subjectCode =
                    json.optString(
                        "subjectCode"
                    ),
                courseName =
                    json.optString(
                        "courseName"
                    ),
                teacher =
                    json.optString(
                        "teacher"
                    ),
                required =
                    json.optBoolean(
                        "required",
                        false
                    ),
                credits =
                    json.optDouble(
                        "credits",
                        0.0
                    ),
                weekday =
                    json.optInt(
                        "weekday",
                        0
                    ),
                periods =
                    jsonArrayToStringList(
                        json.optJSONArray(
                            "periods"
                        )
                    ),
                classroom =
                    json.optString(
                        "classroom"
                    )
            )
        }.getOrNull()
    }

    private fun jsonArrayToStringList(
        array: JSONArray?
    ): List<String> {
        if (array == null) {
            return emptyList()
        }

        return buildList {
            for (
            index in
            0 until array.length()
            ) {
                val value =
                    array.optString(
                        index
                    )

                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }
}

/**
 * 從手機內部讀出的課表資料。
 */
data class SavedSchedule(
    val loadedCourses: List<Course> =
        emptyList(),

    val manualCourses: List<Course> =
        emptyList(),

    val semester: String = "",

    val selectedDepartmentCode: String = "",

    val selectedGrade: Int? = null,

    val selectedClassName: String = ""
)


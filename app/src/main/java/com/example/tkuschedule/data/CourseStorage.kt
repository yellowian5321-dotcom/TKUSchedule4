package com.example.tkuschedule.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CourseStorageSnapshot(val loadedCourses: List<Course>, val manualCourses: List<Course>)

/** 呼叫端在 IO 執行緒讀寫；保留必修載入與手動選修的分類。 */
class CourseStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tku_saved_schedule_v1", Context.MODE_PRIVATE)

    fun load(): CourseStorageSnapshot {
        val text = prefs.getString("schedule", null) ?: return CourseStorageSnapshot(emptyList(), emptyList())
        val json = JSONObject(text)
        return CourseStorageSnapshot(decode(json.getJSONArray("loaded")), decode(json.getJSONArray("manual")))
    }

    fun save(loaded: List<Course>, manual: List<Course>) {
        val json = JSONObject().put("loaded", encode(loaded)).put("manual", encode(manual)).toString()
        check(prefs.edit().putString("schedule", json).commit()) { "課表儲存失敗，請確認手機儲存空間" }
    }

    private fun encode(courses: List<Course>): JSONArray = JSONArray().apply {
        courses.forEach { c ->
            put(JSONObject().put("id", c.id).put("semester", c.semester)
                .put("departmentCode", c.departmentCode).put("departmentName", c.departmentName)
                .put("grade", c.grade).put("className", c.className).put("courseNo", c.courseNo)
                .put("subjectCode", c.subjectCode).put("courseName", c.courseName).put("teacher", c.teacher)
                .put("required", c.required).put("credits", c.credits).put("weekday", c.weekday)
                .put("periods", JSONArray(c.periods)).put("classroom", c.classroom))
        }
    }

    private fun decode(array: JSONArray): List<Course> = List(array.length()) { index ->
        val c = array.getJSONObject(index)
        val periods = c.getJSONArray("periods")
        Course(
            id = c.getString("id"), semester = c.getString("semester"),
            departmentCode = c.getString("departmentCode"), departmentName = c.getString("departmentName"),
            grade = c.getInt("grade"), className = c.getString("className"), courseNo = c.getString("courseNo"),
            subjectCode = c.getString("subjectCode"), courseName = c.getString("courseName"), teacher = c.getString("teacher"),
            required = c.getBoolean("required"), credits = c.getDouble("credits"), weekday = c.getInt("weekday"),
            periods = List(periods.length()) { periods.getString(it) }, classroom = c.getString("classroom")
        )
    }
}
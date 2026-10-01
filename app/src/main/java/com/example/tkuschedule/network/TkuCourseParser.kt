package com.example.tkuschedule.network

import com.example.tkuschedule.data.Course
import com.example.tkuschedule.data.Department
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class TkuCourseParser {

    companion object {
        private const val INDEX_URL =
            "https://esquery.tku.edu.tw/acad/upload/"

        private const val COURSE_BASE_URL =
            "https://esquery.tku.edu.tw/acad/upload/data"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var departmentCache: List<Department>? = null

    @Volatile
    private var allCoursesCache: List<Course>? = null

    suspend fun fetchDepartments(): List<Department> =
        withContext(Dispatchers.IO) {
            departmentCache
                ?: parseDepartments(getText(INDEX_URL)).also {
                    departmentCache = it
                }
        }

    suspend fun fetchCourses(
        departmentCode: String,
        grade: Int? = null,
        className: String? = null
    ): List<Course> = withContext(Dispatchers.IO) {

        val department = fetchDepartments()
            .firstOrNull { it.code == departmentCode }
            ?: Department(
                code = departmentCode,
                name = departmentCode
            )

        parseCoursePage(department).filter { course ->
            (grade == null || course.grade == grade) &&
                    (
                            className.isNullOrBlank() ||
                                    course.className == className
                            )
        }
    }

    suspend fun searchCourses(
        query: String,
        maxResults: Int = 120
    ): List<Course> = withContext(Dispatchers.IO) {

        val keyword = normalize(query)

        require(keyword.isNotBlank()) {
            "請輸入開課序號、科目代碼或課程名稱"
        }

        val catalog = allCoursesCache
            ?: buildCatalog().also {
                allCoursesCache = it
            }

        catalog.asSequence()
            .filter { course ->
                normalize(course.courseNo).contains(keyword) ||
                        normalize(course.subjectCode).contains(keyword) ||
                        normalize(course.courseName).contains(keyword)
            }
            .sortedWith(
                compareBy<Course> { course ->
                    when {
                        normalize(course.courseNo) == keyword -> 0
                        normalize(course.subjectCode) == keyword -> 1
                        normalize(course.courseName) == keyword -> 2
                        else -> 3
                    }
                }
                    .thenBy { it.courseNo }
                    .thenBy { it.courseName }
            )
            .take(maxResults)
            .toList()
    }

    private fun buildCatalog(): List<Course> {
        val departments = departmentCache
            ?: parseDepartments(getText(INDEX_URL)).also {
                departmentCache = it
            }

        return departments
            .flatMap { department ->
                runCatching {
                    parseCoursePage(department)
                }.getOrDefault(emptyList())
            }
            .distinctBy { it.id }
    }

    private fun parseDepartments(
        html: String
    ): List<Department> {

        val optionRegex =
            Regex("""new\s+Option\("([^"]+)"""")

        return optionRegex
            .findAll(html)
            .mapNotNull { match ->

                val text = match.groupValues[1].trim()
                val separator = text.indexOf('_')

                if (separator <= 0) {
                    return@mapNotNull null
                }

                val code = text
                    .substring(0, separator)
                    .trim()

                val name = text
                    .substring(separator + 1)
                    .trim()

                if (code.isBlank() || name.isBlank()) {
                    null
                } else {
                    Department(
                        code = code,
                        name = name
                    )
                }
            }
            .distinctBy { it.code }
            .toList()
    }

    private fun parseCoursePage(
        department: Department
    ): List<Course> {

        val url =
            "$COURSE_BASE_URL/${department.code}.htm"

        val document = Jsoup.parse(
            getText(url),
            url
        )

        val semester = parseSemester(document)
        val courses = mutableListOf<Course>()

        var previousBaseCourse: BaseCourse? = null

        document.select("tr").forEach rowLoop@{ row ->

            val cells = row.select("td")

            if (cells.size < 15) {
                return@rowLoop
            }

            val courseNo = cells[1]
                .text()
                .clean()

            val baseCourse =
                if (courseNo.isNotBlank()) {

                    BaseCourse(
                        grade = cells[0]
                            .text()
                            .firstOrNull(Char::isDigit)
                            ?.digitToInt()
                            ?: 0,

                        courseNo = courseNo,

                        subjectCode = cells[2]
                            .text()
                            .clean(),

                        className = cells[5]
                            .text()
                            .clean(),

                        required = cells[7]
                            .text()
                            .contains("必"),

                        credits = cells[8]
                            .text()
                            .clean()
                            .toDoubleOrNull()
                            ?: 0.0,

                        courseName =
                            cleanCourseName(cells[10])
                    ).also {
                        previousBaseCourse = it
                    }
                } else {
                    previousBaseCourse
                        ?: return@rowLoop
                }

            val teacher = cells[12]
                .text()
                .replace(
                    Regex("""\([^)]*\)"""),
                    ""
                )
                .clean()
                .ifBlank { "未公告" }

            val meetingColumns = listOf(
                cells[13].text(),
                cells[14].text()
            )

            meetingColumns.forEach meetingLoop@{ meetingText ->

                val meeting = parseMeeting(meetingText)
                    ?: return@meetingLoop

                val id = listOf(
                    semester,
                    department.code,
                    baseCourse.courseNo,
                    baseCourse.subjectCode,
                    baseCourse.className,
                    meeting.weekday,
                    meeting.periods.joinToString(","),
                    meeting.classroom,
                    teacher
                ).joinToString("|")

                courses += Course(
                    id = id,
                    semester = semester,
                    departmentCode = department.code,
                    departmentName = department.name,
                    grade = baseCourse.grade,
                    className = baseCourse.className,
                    courseNo = baseCourse.courseNo,
                    subjectCode = baseCourse.subjectCode,
                    courseName = baseCourse.courseName,
                    teacher = teacher,
                    required = baseCourse.required,
                    credits = baseCourse.credits,
                    weekday = meeting.weekday,
                    periods = meeting.periods,
                    classroom = meeting.classroom
                )
            }
        }

        return courses.distinctBy { it.id }
    }

    private fun cleanCourseName(
        cell: Element
    ): String {
        val copy = cell.clone()

        copy.select(
            "font[color=red], font[color=maroon]"
        ).remove()

        return copy.text().clean()
    }

    private fun parseSemester(
        document: Document
    ): String {
        return Regex(
            """(\d{2,3}學年度第\d學期)"""
        )
            .find(document.text())
            ?.groupValues
            ?.get(1)
            ?: ""
    }

    private fun parseMeeting(
        raw: String
    ): Meeting? {

        val parts = raw
            .split('/')
            .map { it.clean() }

        if (parts.size < 2) {
            return null
        }

        val weekday = when (parts[0].takeLast(1)) {
            "一" -> 1
            "二" -> 2
            "三" -> 3
            "四" -> 4
            "五" -> 5
            "六" -> 6
            "日" -> 7
            else -> return null
        }

        val periods = parts[1]
            .split(Regex("""[,、\s]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (periods.isEmpty()) {
            return null
        }

        val classroom = parts
            .drop(2)
            .joinToString(" / ")
            .clean()

        return Meeting(
            weekday = weekday,
            periods = periods,
            classroom = classroom
        )
    }

    private fun getText(
        url: String
    ): String {

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "TKUSchedule/1.0"
            )
            .build()

        client.newCall(request)
            .execute()
            .use { response ->

                if (!response.isSuccessful) {
                    error(
                        "讀取課程資料失敗（HTTP ${response.code}）"
                    )
                }

                return response.body
                    ?.string()
                    ?: error("課程資料內容為空")
            }
    }

    private fun String.clean(): String {
        return replace('\u00a0', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalize(
        value: String
    ): String {
        return value
            .clean()
            .uppercase()
    }

    private data class BaseCourse(
        val grade: Int,
        val courseNo: String,
        val subjectCode: String,
        val className: String,
        val required: Boolean,
        val credits: Double,
        val courseName: String
    )

    private data class Meeting(
        val weekday: Int,
        val periods: List<String>,
        val classroom: String
    )
}
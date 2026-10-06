package com.example.tkuschedule.network

import com.example.tkuschedule.data.Course
import com.example.tkuschedule.data.Department
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class CourseSearchResult(
    val courses: List<Course>,
    val failedDepartments: List<Department>
) {
    val isComplete: Boolean
        get() = failedDepartments.isEmpty()
}

class TkuCourseParser internal constructor(
    private val client: OkHttpClient,
    private val indexUrl: String,
    private val courseBaseUrl: String,
    private val retryDelayMillis: Long
) {
    constructor() : this(
        client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build(),
        indexUrl = INDEX_URL,
        courseBaseUrl = COURSE_BASE_URL,
        retryDelayMillis = 1_000L
    )

    companion object {
        private const val INDEX_URL =
            "https://esquery.tku.edu.tw/acad/upload/"

        private const val COURSE_BASE_URL =
            "https://esquery.tku.edu.tw/acad/upload/data"
    }

    private val departmentMutex = Mutex()
    private val catalogMutex = Mutex()
    private val pageCache = DepartmentPageCache<List<Course>>()
    private val downloads = Semaphore(4)

    @Volatile
    private var departmentCache: List<Department>? = null

    @Volatile
    private var allCoursesCache: List<Course>? = null

    suspend fun fetchDepartments(): List<Department> =
        withContext(Dispatchers.IO) {
            departmentMutex.withLock {
                departmentCache
                    ?: parseDepartments(getText(indexUrl)).also {
                        check(it.isNotEmpty()) {
                            "無法讀取系所清單，請稍後重試"
                        }
                        departmentCache = it
                    }
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

        fetchDepartmentCourses(department).filter { course ->
            (grade == null || course.grade == grade) &&
                    (
                            className.isNullOrBlank() ||
                                    course.className == className
                            )
        }
    }

    // 保留原本只回傳 List 的呼叫介面。
    suspend fun searchCourses(
        query: String,
        maxResults: Int = 120
    ): List<Course> {
        val result = searchCoursesDetailed(query, maxResults)

        if (!result.isComplete) {
            throw IOException(
                "部分系所課程下載失敗，請稍後重新搜尋"
            )
        }

        return result.courses
    }

    // 畫面使用這個介面，取得課程及資料完整性。
    suspend fun searchCoursesDetailed(
        query: String,
        maxResults: Int = 120
    ): CourseSearchResult = withContext(Dispatchers.IO) {
        val keyword = normalize(query)

        require(keyword.isNotBlank()) {
            "請輸入開課序號、科目代碼或課程名稱"
        }

        val catalog = catalogMutex.withLock {
            allCoursesCache?.let {
                CatalogSnapshot(
                    courses = it,
                    failedDepartments = emptyList()
                )
            } ?: buildCatalog().also { snapshot ->
                // 只有全部下載成功，才快取為完整目錄。
                if (snapshot.failedDepartments.isEmpty()) {
                    allCoursesCache = snapshot.courses
                }
            }
        }

        val matches = catalog.courses
            .asSequence()
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

        CourseSearchResult(
            courses = matches,
            failedDepartments = catalog.failedDepartments
        )
    }

    private suspend fun fetchDepartmentCourses(
        department: Department
    ): List<Course> =
        pageCache.get(department.code) {
            downloads.withPermit {
                parseCoursePage(department)
            }
        }

    private suspend fun buildCatalog(): CatalogSnapshot =
        coroutineScope {
            val departments = fetchDepartments()

            val results = departments.map { department ->
                async {
                    try {
                        Result.success(
                            fetchDepartmentCourses(department)
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure<List<Course>>(error)
                    }
                }
            }.awaitAll()

            // 部分失敗時，仍保留成功取得的課程。
            CatalogSnapshot(
                courses = results
                    .flatMap { it.getOrNull().orEmpty() }
                    .distinctBy { it.id },

                failedDepartments = departments.filterIndexed {
                        index, _ ->
                    results[index].isFailure
                }
            )
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

    private suspend fun parseCoursePage(
        department: Department
    ): List<Course> {
        val url =
            "$courseBaseUrl/${department.code}.htm"

        val document = Jsoup.parse(
            getText(url),
            url
        )

        val semester = parseSemester(document)
        val courses = mutableListOf<Course>()

        var previousBaseCourse: BaseCourse? = null

        document.select("tr").forEach rowLoop@{ row ->
            coroutineContext.ensureActive()

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

    private suspend fun getText(
        url: String
    ): String {
        // 暫時性錯誤，程式最多額外重試一次。
        repeat(2) { attempt ->
            coroutineContext.ensureActive()

            try {
                return getTextOnce(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                val retryable =
                    error !is CourseHttpException ||
                            error.statusCode in setOf(
                        408,
                        429,
                        500,
                        502,
                        503,
                        504
                    )

                if (attempt == 1 || !retryable) {
                    throw error
                }

                delay(retryDelayMillis)
            }
        }

        error("Unreachable retry state")
    }

    private suspend fun getTextOnce(
        url: String
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "TKUSchedule/1.0")
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(
                    call: Call,
                    error: IOException
                ) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(error)
                        )
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    val result = response.use {
                        if (!continuation.isActive) {
                            return
                        }

                        runCatching {
                            if (!it.isSuccessful) {
                                throw CourseHttpException(it.code)
                            }

                            it.body?.string()
                                ?: error("課程資料內容為空")
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resumeWith(result)
                    }
                }
            })
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

    private data class CatalogSnapshot(
        val courses: List<Course>,
        val failedDepartments: List<Department>
    )

    private class CourseHttpException(
        val statusCode: Int
    ) : IOException(
        "讀取課程資料失敗（HTTP $statusCode）"
    )

    private data class Meeting(
        val weekday: Int,
        val periods: List<String>,
        val classroom: String
    )
}
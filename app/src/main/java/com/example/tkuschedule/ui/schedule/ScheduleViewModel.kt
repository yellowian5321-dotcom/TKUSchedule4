package com.example.tkuschedule.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tkuschedule.data.Course
import com.example.tkuschedule.data.CourseRepository
import com.example.tkuschedule.data.CourseStorage
import com.example.tkuschedule.data.Department
import com.example.tkuschedule.reminder.ClassReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class CourseOffering(
    val key: String,
    val sessions: List<Course>
) {
    val primary: Course
        get() = sessions.first()

    val scheduleText: String
        get() = sessions.joinToString("、") {
            "${it.weekdayText} ${it.periodsText} ${it.classroom}"
                .trim()
        }
}

data class ScheduleUiState(
    val isRestoringSchedule: Boolean = true,
    val isLoadingDepartments: Boolean = false,
    val isLoadingCourses: Boolean = false,
    val isSearchingCourses: Boolean = false,

    val departments: List<Department> = emptyList(),

    val selectedDepartmentCode: String = "",
    val selectedGrade: Int? = null,
    val selectedClassName: String = "",

    val loadedCourses: List<Course> = emptyList(),
    val manualCourses: List<Course> = emptyList(),

    val semester: String = "",
    val courseSearchQuery: String = "",

    val courseSearchResults: List<CourseOffering> = emptyList(),
    val pendingConflictOffering: CourseOffering? = null,

    val searchMessage: String? = null,
    val errorMessage: String? = null
) {
    val courses: List<Course> =
        (loadedCourses + manualCourses).distinctBy { it.id }
}

class ScheduleViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: CourseRepository =
        CourseRepository()
) : AndroidViewModel(application) {

    private val courseStorage = CourseStorage(application)

    private var departmentsJob: Job? = null
    private var coursesJob: Job? = null
    private var searchJob: Job? = null

    private val _uiState =
        MutableStateFlow(ScheduleUiState())

    val uiState: StateFlow<ScheduleUiState> =
        _uiState.asStateFlow()

    init {
        restoreAndObserveSchedule()
        loadDepartments()
    }

    private fun restoreAndObserveSchedule() {
        viewModelScope.launch {
            var restored = true

            try {
                val saved = withContext(Dispatchers.IO) {
                    courseStorage.load()
                }

                _uiState.update {
                    it.copy(
                        isRestoringSchedule = false,
                        loadedCourses = saved.loadedCourses,
                        manualCourses = saved.manualCourses,
                        semester = (
                                saved.loadedCourses.firstOrNull()
                                    ?: saved.manualCourses.firstOrNull()
                                )?.semester.orEmpty()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                restored = false

                _uiState.update {
                    it.copy(
                        isRestoringSchedule = false,
                        errorMessage =
                            "課表還原失敗，請重新匯入課程"
                    )
                }
            }

            // 還原完成後才開始儲存，避免初始空課表取消通知。
            _uiState
                .map {
                    it.loadedCourses to it.manualCourses
                }
                .distinctUntilChanged()
                .drop(if (restored) 0 else 1)
                .collect { (loaded, manual) ->
                    try {
                        withContext(Dispatchers.IO) {
                            courseStorage.save(
                                loaded,
                                manual
                            )

                            ClassReminderScheduler.updateCourses(
                                getApplication<Application>(),
                                (loaded + manual)
                                    .distinctBy { it.id }
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        _uiState.update {
                            it.copy(
                                errorMessage =
                                    error.message
                                        ?: "課表或通知排程儲存失敗"
                            )
                        }
                    }
                }
        }
    }

    fun loadDepartments() {
        departmentsJob?.cancel()

        departmentsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingDepartments = true,
                    errorMessage = null
                )
            }

            repository.loadDepartments().fold(
                onSuccess = { departments ->
                    coroutineContext.ensureActive()

                    _uiState.update {
                        it.copy(
                            isLoadingDepartments = false,
                            departments = departments
                        )
                    }
                },
                onFailure = { error ->
                    coroutineContext.ensureActive()

                    _uiState.update {
                        it.copy(
                            isLoadingDepartments = false,
                            errorMessage = error.userMessage()
                        )
                    }
                }
            )
        }
    }

    fun selectDepartment(
        code: String
    ) {
        coursesJob?.cancel()

        _uiState.update {
            it.copy(
                isLoadingCourses = false,
                selectedDepartmentCode = code,
                selectedGrade = null,
                selectedClassName = ""
            )
        }
    }

    fun selectGrade(
        grade: Int
    ) {
        coursesJob?.cancel()

        _uiState.update {
            it.copy(
                isLoadingCourses = false,
                selectedGrade = grade,
                selectedClassName = ""
            )
        }
    }

    fun selectClassName(
        className: String
    ) {
        coursesJob?.cancel()

        _uiState.update {
            it.copy(
                isLoadingCourses = false,
                selectedClassName = className
            )
        }
    }

    fun loadCourses() {
        val state = _uiState.value

        if (
            state.selectedDepartmentCode.isBlank() ||
            state.selectedGrade == null ||
            state.selectedClassName.isBlank()
        ) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "請先選擇系所、年級與班級"
                )
            }
            return
        }

        coursesJob?.cancel()

        coursesJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingCourses = true,
                    errorMessage = null
                )
            }

            repository.loadCourses(
                departmentCode =
                    state.selectedDepartmentCode,
                grade =
                    state.selectedGrade,
                className =
                    state.selectedClassName
            ).fold(
                onSuccess = { courses ->
                    coroutineContext.ensureActive()

                    _uiState.update {
                        it.copy(
                            isLoadingCourses = false,
                            loadedCourses = courses,
                            semester = courses
                                .firstOrNull()
                                ?.semester
                                .orEmpty(),
                            errorMessage =
                                if (courses.isEmpty()) {
                                    "找不到這個班級的課程"
                                } else {
                                    null
                                }
                        )
                    }
                },
                onFailure = { error ->
                    coroutineContext.ensureActive()

                    _uiState.update {
                        it.copy(
                            isLoadingCourses = false,
                            errorMessage = error.userMessage()
                        )
                    }
                }
            )
        }
    }

    fun updateCourseSearchQuery(
        value: String
    ) {
        _uiState.update {
            it.copy(
                courseSearchQuery = value,
                searchMessage = null
            )
        }
    }

    fun searchCourses() {
        val query =
            _uiState.value.courseSearchQuery.trim()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    searchMessage =
                        "請輸入開課序號、科目代碼或課程名稱"
                )
            }
            return
        }

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchingCourses = true,
                    courseSearchResults = emptyList(),
                    searchMessage = null
                )
            }

            repository.searchCoursesDetailed(query).fold(
                onSuccess = { result ->
                    coroutineContext.ensureActive()

                    val offerings = result.courses
                        .groupBy(::offeringKey)
                        .map { (key, sessions) ->
                            CourseOffering(
                                key = key,
                                sessions = sessions
                                    .distinctBy { it.id }
                            )
                        }

                    _uiState.update {
                        it.copy(
                            isSearchingCourses = false,
                            courseSearchResults = offerings,
                            searchMessage = when {
                                !result.isComplete &&
                                        offerings.isEmpty() ->
                                    "${result.failedDepartments.size} 個系所暫時無法讀取，尚未找到符合課程；請重新搜尋，結果可能不完整"

                                !result.isComplete ->
                                    "${result.failedDepartments.size} 個系所暫時無法讀取，先顯示已找到的課程；可重新搜尋補齊"

                                offerings.isEmpty() ->
                                    "查無符合的課程"

                                else -> null
                            }
                        )
                    }
                },
                onFailure = { error ->
                    coroutineContext.ensureActive()

                    _uiState.update {
                        it.copy(
                            isSearchingCourses = false,
                            searchMessage = error.userMessage()
                        )
                    }
                }
            )
        }
    }

    fun chooseOffering(
        offering: CourseOffering
    ) {
        val state = _uiState.value

        val alreadyExists = state.courses.any {
            offeringKey(it) == offering.key
        }

        if (alreadyExists) {
            _uiState.update {
                it.copy(
                    searchMessage =
                        "這門課已經在課表中",
                    courseSearchResults = emptyList()
                )
            }
            return
        }

        if (
            hasConflict(
                existing = state.courses,
                incoming = offering.sessions
            )
        ) {
            _uiState.update {
                it.copy(
                    pendingConflictOffering = offering
                )
            }
        } else {
            addOffering(offering)
        }
    }

    fun confirmConflict() {
        val offering =
            _uiState.value.pendingConflictOffering

        if (offering != null) {
            addOffering(offering)
        }
    }

    fun dismissConflict() {
        _uiState.update {
            it.copy(
                pendingConflictOffering = null
            )
        }
    }

    fun removeManualCourse(
        course: Course
    ) {
        _uiState.update { state ->
            val key = offeringKey(course)

            state.copy(
                manualCourses =
                    state.manualCourses.filter {
                        offeringKey(it) != key
                    }
            )
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()

        _uiState.update {
            it.copy(
                isSearchingCourses = false,
                courseSearchResults = emptyList(),
                searchMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                errorMessage = null
            )
        }
    }

    fun clearCourses() {
        coursesJob?.cancel()

        _uiState.update {
            it.copy(
                isLoadingCourses = false,
                loadedCourses = emptyList(),
                manualCourses = emptyList(),
                semester = ""
            )
        }
    }

    fun getCoursesForDay(
        day: Int
    ): List<Course> {
        return _uiState.value
            .courses
            .filter {
                it.weekday == day
            }
    }

    private fun addOffering(
        offering: CourseOffering
    ) {
        _uiState.update { state ->
            state.copy(
                manualCourses = (
                        state.manualCourses +
                                offering.sessions
                        ).distinctBy { it.id },

                pendingConflictOffering = null,
                courseSearchResults = emptyList(),
                courseSearchQuery = "",

                searchMessage =
                    "已匯入「${offering.primary.courseName}」"
            )
        }
    }

    private fun hasConflict(
        existing: List<Course>,
        incoming: List<Course>
    ): Boolean {
        return incoming.any { newCourse ->
            existing.any { oldCourse ->
                oldCourse.weekday == newCourse.weekday &&
                        oldCourse.periods.any {
                            it in newCourse.periods
                        }
            }
        }
    }

    private fun Throwable.userMessage(): String {
        return message
            ?.takeIf { it.isNotBlank() }
            ?: "讀取資料失敗，請檢查網路後再試一次"
    }
}

private fun offeringKey(
    course: Course
): String {
    return if (course.courseNo.isNotBlank()) {
        "${course.departmentCode}|${course.courseNo}"
    } else {
        "${course.departmentCode}|" +
                "${course.subjectCode}|" +
                "${course.className}|" +
                course.courseName
    }
}
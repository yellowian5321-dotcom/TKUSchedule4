package com.example.tkuschedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tkuschedule.data.Course
import com.example.tkuschedule.data.CourseRepository
import com.example.tkuschedule.data.Department
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val isLoadingDepartments: Boolean = false,
    val isLoadingCourses: Boolean = false,
    val isSearchingCourses: Boolean = false,

    val departments: List<Department> =
        emptyList(),

    val selectedDepartmentCode: String = "",
    val selectedGrade: Int? = null,
    val selectedClassName: String = "",

    val loadedCourses: List<Course> =
        emptyList(),

    val manualCourses: List<Course> =
        emptyList(),

    val semester: String = "",

    val courseSearchQuery: String = "",

    val courseSearchResults:
    List<CourseOffering> = emptyList(),

    val pendingConflictOffering:
    CourseOffering? = null,

    val searchMessage: String? = null,
    val errorMessage: String? = null
) {
    val courses: List<Course>
        get() = (
                loadedCourses + manualCourses
                ).distinctBy { it.id }
}

class ScheduleViewModel(
    private val repository: CourseRepository =
        CourseRepository()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(ScheduleUiState())

    val uiState: StateFlow<ScheduleUiState> =
        _uiState.asStateFlow()

    init {
        loadDepartments()
    }

    fun loadDepartments() {
        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    isLoadingDepartments = true,
                    errorMessage = null
                )
            }

            repository.loadDepartments().fold(
                onSuccess = { departments ->

                    _uiState.update {
                        it.copy(
                            isLoadingDepartments = false,
                            departments = departments
                        )
                    }
                },

                onFailure = { error ->

                    _uiState.update {
                        it.copy(
                            isLoadingDepartments = false,
                            errorMessage =
                                error.userMessage()
                        )
                    }
                }
            )
        }
    }

    fun selectDepartment(
        code: String
    ) {
        _uiState.update {
            it.copy(
                selectedDepartmentCode = code,
                selectedGrade = null,
                selectedClassName = ""
            )
        }
    }

    fun selectGrade(
        grade: Int
    ) {
        _uiState.update {
            it.copy(
                selectedGrade = grade,
                selectedClassName = ""
            )
        }
    }

    fun selectClassName(
        className: String
    ) {
        _uiState.update {
            it.copy(
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

        viewModelScope.launch {

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

                    _uiState.update {
                        it.copy(
                            isLoadingCourses = false,

                            loadedCourses =
                                courses,

                            semester =
                                courses
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

                    _uiState.update {
                        it.copy(
                            isLoadingCourses = false,
                            errorMessage =
                                error.userMessage()
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

        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    isSearchingCourses = true,
                    courseSearchResults =
                        emptyList(),
                    searchMessage = null
                )
            }

            repository.searchCourses(query).fold(
                onSuccess = { courses ->

                    val offerings = courses
                        .groupBy(::offeringKey)
                        .map { (key, sessions) ->

                            CourseOffering(
                                key = key,

                                sessions =
                                    sessions.distinctBy {
                                        it.id
                                    }
                            )
                        }

                    _uiState.update {
                        it.copy(
                            isSearchingCourses = false,

                            courseSearchResults =
                                offerings,

                            searchMessage =
                                if (offerings.isEmpty()) {
                                    "查無符合的課程"
                                } else {
                                    null
                                }
                        )
                    }
                },

                onFailure = { error ->

                    _uiState.update {
                        it.copy(
                            isSearchingCourses = false,

                            searchMessage =
                                error.userMessage()
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

        val alreadyExists =
            state.courses.any {
                offeringKey(it) == offering.key
            }

        if (alreadyExists) {
            _uiState.update {
                it.copy(
                    searchMessage =
                        "這門課已經在課表中",

                    courseSearchResults =
                        emptyList()
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
                    pendingConflictOffering =
                        offering
                )
            }
        } else {
            addOffering(offering)
        }
    }

    fun confirmConflict() {
        val offering =
            _uiState.value
                .pendingConflictOffering

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
        _uiState.update {
            it.copy(
                courseSearchResults =
                    emptyList(),

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
        _uiState.update {
            it.copy(
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

                courseSearchResults =
                    emptyList(),

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

                oldCourse.weekday ==
                        newCourse.weekday &&

                        oldCourse.periods.any {
                            it in newCourse.periods
                        }
            }
        }
    }

    private fun Throwable.userMessage():
            String {

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
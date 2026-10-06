package com.example.tkuschedule.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tkuschedule.data.Course
import com.example.tkuschedule.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val PAGE_COURSE_MANAGEMENT = 0
private const val PAGE_MY_SCHEDULE = 1
private const val PAGE_SETTINGS = 2

private val weekdayLabels = listOf(
    1 to "星期一",
    2 to "星期二",
    3 to "星期三",
    4 to "星期四",
    5 to "星期五",
    6 to "星期六",
    7 to "星期日"
)

private val periodLabels = listOf(
    "1" to "08:10",
    "2" to "09:10",
    "3" to "10:10",
    "4" to "11:10",
    "5" to "12:10",
    "6" to "13:10",
    "7" to "14:10",
    "8" to "15:10",
    "9" to "16:10",
    "10" to "17:10",
    "11" to "18:10",
    "12" to "19:10",
    "13" to "20:10",
    "14" to "21:10"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
) {
    // 搜尋文字只由搜尋輸入區收集，減少整個畫面更新。
    val screenFlow = remember(viewModel) {
        viewModel.uiState
            .map { it.copy(courseSearchQuery = "") }
            .distinctUntilChanged()
    }

    val state by screenFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.uiState.value.copy(
            courseSearchQuery = ""
        )
    )

    if (state.isRestoringSchedule) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    var selectedPage by rememberSaveable {
        mutableIntStateOf(PAGE_COURSE_MANAGEMENT)
    }

    var selectedWeekday by rememberSaveable {
        mutableIntStateOf(1)
    }

    val managementScrollState = rememberLazyListState()
    val scheduleScrollState = rememberLazyListState()

    val courseCount = remember(state.courses) {
        state.courses.distinctBy(::courseKey).size
    }

    var selectedCourse by remember {
        mutableStateOf<Course?>(null)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.searchMessage) {
        state.searchMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (selectedPage) {
                                PAGE_COURSE_MANAGEMENT -> "課程管理"
                                PAGE_MY_SCHEDULE -> "我的課表"
                                else -> "設定"
                            }
                        )

                        if (
                            selectedPage != PAGE_SETTINGS &&
                            state.semester.isNotBlank()
                        ) {
                            Text(
                                text = state.semester,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                actions = {
                    if (
                        selectedPage == PAGE_MY_SCHEDULE &&
                        state.courses.isNotEmpty()
                    ) {
                        Text(
                            text = "$courseCount 門課",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected =
                        selectedPage == PAGE_COURSE_MANAGEMENT,
                    onClick = {
                        selectedPage = PAGE_COURSE_MANAGEMENT
                    },
                    icon = {
                        Text(
                            text = "＋",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    label = {
                        Text("課程管理")
                    }
                )

                NavigationBarItem(
                    selected = selectedPage == PAGE_MY_SCHEDULE,
                    onClick = {
                        selectedPage = PAGE_MY_SCHEDULE
                    },
                    icon = {
                        Text(
                            text = "▦",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    label = {
                        Text("我的課表")
                    }
                )

                NavigationBarItem(
                    selected = selectedPage == PAGE_SETTINGS,
                    onClick = {
                        selectedPage = PAGE_SETTINGS
                    },
                    icon = {
                        Text(
                            text = "⚙",
                            fontSize = 22.sp
                        )
                    },
                    label = {
                        Text("設定")
                    }
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedPage) {
                PAGE_COURSE_MANAGEMENT -> {
                    CourseManagementPage(
                        state = state,
                        viewModel = viewModel,
                        scrollState = managementScrollState,
                        onDepartment = viewModel::selectDepartment,
                        onGrade = viewModel::selectGrade,
                        onClassName = viewModel::selectClassName,
                        onLoadCourses = viewModel::loadCourses,
                        onRetryDepartments = viewModel::loadDepartments,
                        onSearchQueryChange =
                            viewModel::updateCourseSearchQuery,
                        onSearch = viewModel::searchCourses,
                        onCourseClick = {
                            selectedCourse = it
                        },
                        onRemove = viewModel::removeManualCourse,
                        onOpenSchedule = {
                            selectedPage = PAGE_MY_SCHEDULE
                        }
                    )
                }

                PAGE_MY_SCHEDULE -> {
                    MySchedulePage(
                        courses = state.courses,
                        scrollState = scheduleScrollState,
                        selectedWeekday = selectedWeekday,
                        onWeekdaySelected = {
                            selectedWeekday = it
                        },
                        onCourseClick = {
                            selectedCourse = it
                        },
                        onGoToManagement = {
                            selectedPage = PAGE_COURSE_MANAGEMENT
                        }
                    )
                }

                PAGE_SETTINGS -> {
                    SettingsScreen(
                        courseCount = courseCount
                    )
                }
            }
        }
    }

    if (state.courseSearchResults.isNotEmpty()) {
        SearchResultDialog(
            results = state.courseSearchResults,
            onChoose = viewModel::chooseOffering,
            onDismiss = viewModel::clearSearchResults
        )
    }

    state.pendingConflictOffering?.let { offering ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = {
                Text("課程時間衝堂")
            },
            text = {
                Text(
                    "「${offering.primary.courseName}」" +
                            "與目前課表中的課程時間重疊，" +
                            "仍然要匯入嗎？"
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmConflict
                ) {
                    Text("仍要匯入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissConflict
                ) {
                    Text("取消")
                }
            }
        )
    }

    selectedCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            isManualCourse = state.manualCourses.any {
                courseKey(it) == courseKey(course)
            },
            onRemove = {
                viewModel.removeManualCourse(course)
                selectedCourse = null
            },
            onDismiss = {
                selectedCourse = null
            }
        )
    }
}

@Composable
private fun CourseManagementPage(
    state: ScheduleUiState,
    viewModel: ScheduleViewModel,
    scrollState: LazyListState,
    onDepartment: (String) -> Unit,
    onGrade: (Int) -> Unit,
    onClassName: (String) -> Unit,
    onLoadCourses: () -> Unit,
    onRetryDepartments: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCourseClick: (Course) -> Unit,
    onRemove: (Course) -> Unit,
    onOpenSchedule: () -> Unit
) {
    val manualKeys = remember(state.manualCourses) {
        state.manualCourses.map(::courseKey).toSet()
    }

    val courseOfferings = remember(state.courses) {
        state.courses
            .distinctBy(::courseKey)
            .sortedWith(
                compareBy<Course> { it.weekday }
                    .thenBy {
                        periodNumber(it.periods.firstOrNull())
                    }
            )
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(
            key = "required_course",
            contentType = "required_course"
        ) {
            RequiredCoursePanel(
                state = state,
                onDepartment = onDepartment,
                onGrade = onGrade,
                onClassName = onClassName,
                onLoad = onLoadCourses,
                onRetryDepartments = onRetryDepartments,
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp
                )
            )
        }

        item(
            key = "course_search",
            contentType = "course_search"
        ) {
            CourseSearchInputPanel(
                viewModel = viewModel,
                loading = state.isSearchingCourses,
                onQueryChange = onSearchQueryChange,
                onSearch = onSearch,
                modifier = Modifier.padding(
                    horizontal = 12.dp
                )
            )
        }

        if (state.courses.isNotEmpty()) {
            item(
                key = "open_schedule",
                contentType = "open_schedule"
            ) {
                Button(
                    onClick = onOpenSchedule,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text("查看我的課表")
                }
            }

            item(
                key = "course_heading",
                contentType = "course_heading"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = 2.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "目前課程",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${courseOfferings.size} 門",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                items = courseOfferings,
                key = {
                    "course:${courseKey(it)}"
                },
                contentType = {
                    "course_card"
                }
            ) { course ->
                ManagementCourseCard(
                    course = course,
                    isManual = courseKey(course) in manualKeys,
                    onClick = {
                        onCourseClick(course)
                    },
                    onRemove = {
                        onRemove(course)
                    },
                    modifier = Modifier.padding(
                        horizontal = 12.dp
                    )
                )
            }

            item(
                key = "footer",
                contentType = "spacer"
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )
            }
        } else {
            item(
                key = "empty_courses",
                contentType = "empty_courses"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "尚未載入或匯入任何課程",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MySchedulePage(
    courses: List<Course>,
    scrollState: LazyListState,
    selectedWeekday: Int,
    onWeekdaySelected: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    onGoToManagement: () -> Unit
) {
    val counts = remember(courses) {
        weekdayLabels.associate { (day, _) ->
            day to courses
                .filter { it.weekday == day }
                .distinctBy(::courseKey)
                .size
        }
    }

    val dayCourses = remember(courses, selectedWeekday) {
        courses.filter {
            it.weekday == selectedWeekday
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedWeekday - 1,
            edgePadding = 8.dp
        ) {
            weekdayLabels.forEach { (weekday, label) ->
                val count = counts[weekday] ?: 0

                Tab(
                    selected = selectedWeekday == weekday,
                    onClick = {
                        onWeekdaySelected(weekday)
                    },
                    text = {
                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {
                            Text(label)

                            if (count > 0) {
                                Text(
                                    text = "$count 門",
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                )
            }
        }

        if (courses.isEmpty()) {
            EmptySchedule(
                title = "目前沒有課程",
                message =
                    "請先到課程管理載入班級課程，或搜尋選修課程。",
                buttonText = "前往課程管理",
                onClick = onGoToManagement
            )
        } else if (dayCourses.isEmpty()) {
            EmptySchedule(
                title = weekdayName(selectedWeekday) + "沒有課程",
                message = "這一天目前沒有排入任何課程。",
                buttonText = "管理課程",
                onClick = onGoToManagement
            )
        } else {
            DayScheduleGrid(
                courses = dayCourses,
                scrollState = scrollState,
                onCourseClick = onCourseClick
            )
        }
    }
}

@Composable
private fun DayScheduleGrid(
    courses: List<Course>,
    scrollState: LazyListState,
    onCourseClick: (Course) -> Unit
) {
    val cells = remember(courses) {
        periodLabels.associate { (period, _) ->
            period to courses.filter { course ->
                course.periods.any {
                    normalizePeriod(it) == normalizePeriod(period)
                }
            }.distinctBy(::courseKey)
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 10.dp,
                end = 10.dp,
                top = 10.dp
            )
    ) {
        items(
            items = periodLabels,
            key = {
                it.first
            },
            contentType = {
                "period_row"
            }
        ) { (period, startTime) ->
            DayPeriodRow(
                period = period,
                startTime = startTime,
                courses = cells[period].orEmpty(),
                onCourseClick = onCourseClick
            )
        }

        item(
            key = "schedule_footer",
            contentType = "spacer"
        ) {
            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }
    }
}

@Composable
private fun DayPeriodRow(
    period: String,
    startTime: String,
    courses: List<Course>,
    onCourseClick: (Course) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(88.dp)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                .background(
                    MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = period,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "第$period 節",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = startTime,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(88.dp)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                .padding(4.dp)
        ) {
            when {
                courses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "無課程",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                courses.size == 1 -> {
                    DayCourseCard(
                        course = courses.first(),
                        onClick = {
                            onCourseClick(courses.first())
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement =
                            Arrangement.spacedBy(4.dp)
                    ) {
                        courses.take(2).forEach { course ->
                            DayCourseCard(
                                course = course,
                                onClick = {
                                    onCourseClick(course)
                                },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCourseCard(
    course: Course,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val color = courseColor(courseKey(course))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.16f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 8.dp,
                    vertical = 5.dp
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = course.courseName,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 10.sp else 13.sp,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!compact) {
                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text =
                        "${course.classroom.ifBlank { "教室未公告" }}｜" +
                                course.teacher,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text =
                        "${course.courseNo}｜${course.subjectCode}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RequiredCoursePanel(
    state: ScheduleUiState,
    onDepartment: (String) -> Unit,
    onGrade: (Int) -> Unit,
    onClassName: (String) -> Unit,
    onLoad: () -> Unit,
    onRetryDepartments: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "載入班級課程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "選擇系所、年級與班級，載入公開課程資料。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.isLoadingDepartments) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text("正在讀取系所…")
                }
            } else if (state.departments.isEmpty()) {
                OutlinedButton(
                    onClick = onRetryDepartments,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重新讀取系所")
                }
            } else {
                SimpleDropdown(
                    label = "系所",
                    selectedText = state.departments
                        .firstOrNull {
                            it.code == state.selectedDepartmentCode
                        }
                        ?.displayName
                        .orEmpty(),
                    options = state.departments,
                    optionText = {
                        it.displayName
                    },
                    onSelected = {
                        onDepartment(it.code)
                    }
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        SimpleDropdown(
                            label = "年級",
                            selectedText = state.selectedGrade
                                ?.let {
                                    "${it}年級"
                                }
                                .orEmpty(),
                            options = (1..4).toList(),
                            optionText = {
                                "${it}年級"
                            },
                            onSelected = onGrade
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        SimpleDropdown(
                            label = "班級",
                            selectedText = state.selectedClassName,
                            options = ('A'..'Z').map(Char::toString),
                            optionText = {
                                it
                            },
                            onSelected = onClassName
                        )
                    }
                }

                Button(
                    onClick = onLoad,
                    enabled = !state.isLoadingCourses,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isLoadingCourses) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )
                    }

                    Text(
                        if (state.isLoadingCourses) {
                            "載入中…"
                        } else {
                            "載入班級課程"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseSearchInputPanel(
    viewModel: ScheduleViewModel,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val queryFlow = remember(viewModel) {
        viewModel.uiState
            .map { it.courseSearchQuery }
            .distinctUntilChanged()
    }

    val query by queryFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.uiState.value.courseSearchQuery
    )

    CourseImportPanel(
        query = query,
        loading = loading,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        modifier = modifier
    )
}

@Composable
private fun CourseImportPanel(
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "搜尋並匯入課程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "可輸入開課序號、科目代碼或課程名稱。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = {
                    Text("開課序號、科目代碼或名稱")
                },
                placeholder = {
                    Text("例如：2246、V0024、LINUX")
                },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onSearch,
                enabled = !loading && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                }

                Text(
                    if (loading) {
                        "正在搜尋全校課程…"
                    } else {
                        "搜尋課程"
                    }
                )
            }

            if (loading) {
                Text(
                    text =
                        "第一次搜尋需要讀取全校公開課程，請稍候。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ManagementCourseCard(
    course: Course,
    isManual: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = courseColor(courseKey(course))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(65.dp)
                    .background(color)
            )

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = course.courseName,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "${course.weekdayText} ${course.periodsText}"
                )

                Text(
                    text =
                        "${course.classroom.ifBlank { "教室未公告" }}｜" +
                                course.teacher,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text =
                        "${course.courseNo}｜${course.subjectCode}｜" +
                                course.requiredText,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isManual) {
                TextButton(
                    onClick = onRemove
                ) {
                    Text("移除")
                }
            }
        }
    }
}

@Composable
private fun SearchResultDialog(
    results: List<CourseOffering>,
    onChoose: (CourseOffering) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("選擇要匯入的課程")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = results,
                    key = {
                        it.key
                    }
                ) { offering ->
                    val course = offering.primary

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChoose(offering)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = course.courseName,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "開課序號：${course.courseNo}"
                            )

                            Text(
                                text = "科目代碼：${course.subjectCode}"
                            )

                            Text(
                                text =
                                    "${course.departmentName} " +
                                            "${course.grade}年級 " +
                                            "${course.className}班"
                            )

                            Text(
                                text = "教師：${course.teacher}"
                            )

                            Text(
                                text =
                                    "${course.requiredText}｜" +
                                            course.creditsText
                            )

                            Text(
                                text = offering.scheduleText,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "點一下匯入課表",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun CourseDetailDialog(
    course: Course,
    isManualCourse: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(course.courseName)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                DetailRow(
                    label = "開課序號",
                    value = course.courseNo
                )

                DetailRow(
                    label = "科目代碼",
                    value = course.subjectCode
                )

                DetailRow(
                    label = "系所班級",
                    value =
                        "${course.departmentName} " +
                                "${course.grade}年級 " +
                                "${course.className}班"
                )

                DetailRow(
                    label = "教師",
                    value = course.teacher
                )

                DetailRow(
                    label = "類別／學分",
                    value =
                        "${course.requiredText}／" +
                                course.creditsText
                )

                DetailRow(
                    label = "時間",
                    value =
                        "${course.weekdayText} " +
                                course.periodsText
                )

                DetailRow(
                    label = "教室",
                    value = course.classroom.ifBlank {
                        "未公告"
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("關閉")
            }
        },
        dismissButton = {
            if (isManualCourse) {
                TextButton(
                    onClick = onRemove
                ) {
                    Text(
                        text = "移除課程",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Composable
private fun EmptySchedule(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onClick
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun <T> SimpleDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = {
                expanded = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (selectedText.isBlank()) {
                    "請選擇$label"
                } else {
                    selectedText
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (expanded) {
            AlertDialog(
                onDismissRequest = {
                    expanded = false
                },
                title = {
                    Text("選擇$label")
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(
                            count = options.size,
                            key = {
                                it
                            },
                            contentType = {
                                "selector_option"
                            }
                        ) { index ->
                            val option = options[index]

                            TextButton(
                                onClick = {
                                    onSelected(option)
                                    expanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = optionText(option),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            expanded = false
                        }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label：",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(90.dp)
        )

        Text(
            text = value,
            modifier = Modifier.weight(1f)
        )
    }

    Divider()
}

private fun normalizePeriod(
    period: String
): String {
    return period
        .trim()
        .replace("第", "")
        .replace("節", "")
}

private fun periodNumber(
    period: String?
): Int {
    return normalizePeriod(
        period.orEmpty()
    ).toIntOrNull() ?: Int.MAX_VALUE
}

private fun weekdayName(
    weekday: Int
): String {
    return weekdayLabels
        .firstOrNull {
            it.first == weekday
        }
        ?.second
        ?: ""
}

private fun courseKey(
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

private val coursePalette = listOf(
    Color(0xFF1565C0),
    Color(0xFF00897B),
    Color(0xFF7B1FA2),
    Color(0xFFEF6C00),
    Color(0xFFC62828),
    Color(0xFF2E7D32),
    Color(0xFF6A1B9A),
    Color(0xFF0277BD)
)

private fun courseColor(
    seed: String
): Color {
    val index =
        (seed.hashCode() and Int.MAX_VALUE) % coursePalette.size

    return coursePalette[index]
}

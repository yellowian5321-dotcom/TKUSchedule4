package com.example.tkuschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tkuschedule.ai.AiAssistantScreen
import com.example.tkuschedule.assistant.FloatingCatAssistant
import com.example.tkuschedule.ui.schedule.ScheduleScreen
import com.example.tkuschedule.ui.schedule.ScheduleViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                /*
                 * 課表、AI 與小貓共用
                 * 同一個 ScheduleViewModel。
                 */
                val scheduleViewModel:
                        ScheduleViewModel =
                    viewModel()

                val scheduleState by
                scheduleViewModel
                    .uiState
                    .collectAsStateWithLifecycle()

                /*
                 * 控制 AI 對話頁面是否開啟。
                 */
                var showAiAssistant by remember {
                    mutableStateOf(false)
                }

                Surface(
                    modifier =
                        Modifier.fillMaxSize(),

                    color =
                        MaterialTheme
                            .colorScheme
                            .background
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                    ) {
                        /*
                         * 原本的課程管理與格子課表。
                         */
                        ScheduleScreen(
                            viewModel =
                                scheduleViewModel
                        )

                        /*
                         * 浮動小貓助理。
                         *
                         * courses：
                         * 傳入目前已匯入的課程，
                         * 讓小貓計算下一堂課。
                         *
                         * walkingMinutes：
                         * 目前尚未接入定位結果，
                         * 所以暫時傳入 null。
                         */
                        FloatingCatAssistant(
                            courses =
                                scheduleState
                                    .courses,

                            modifier =
                                Modifier
                                    .fillMaxSize(),

                            walkingMinutes =
                                null,

                            onCatClick = {
                                /*
                                 * 點擊小貓後，
                                 * 開啟 Gemini AI。
                                 */
                                showAiAssistant =
                                    true
                            }
                        )

                        /*
                         * AI 對話頁面。
                         */
                        if (showAiAssistant) {
                            Dialog(
                                onDismissRequest = {
                                    showAiAssistant =
                                        false
                                },

                                properties =
                                    DialogProperties(
                                        usePlatformDefaultWidth =
                                            false,

                                        decorFitsSystemWindows =
                                            false
                                    )
                            ) {
                                Surface(
                                    modifier =
                                        Modifier
                                            .fillMaxSize(),

                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .background
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                    ) {
                                        /*
                                         * AI 對話頁上方標題列。
                                         */
                                        AiAssistantTopBar(
                                            courseCount =
                                                countUniqueCourses(
                                                    scheduleState
                                                        .courses
                                                ),

                                            onClose = {
                                                showAiAssistant =
                                                    false
                                            }
                                        )

                                        /*
                                         * AI 對話內容。
                                         */
                                        Box(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth()
                                        ) {
                                            AiAssistantScreen(
                                                courses =
                                                    scheduleState
                                                        .courses
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAssistantTopBar(
    courseCount: Int,
    onClose: () -> Unit
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),

        color =
            MaterialTheme
                .colorScheme
                .primaryContainer,

        shadowElevation = 4.dp
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),

            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        "小貓課表助理",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Text(
                    text =
                        if (courseCount == 0) {
                            "目前尚未匯入課程"
                        } else {
                            "已讀取 $courseCount 門課程"
                        },

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }

            TextButton(
                onClick = onClose
            ) {
                Text("關閉")
            }
        }
    }
}

/*
 * 計算實際課程數量。
 *
 * 同一門課可能有多個上課時段，
 * 但只會計算成一門課。
 */
private fun countUniqueCourses(
    courses:
    List<com.example.tkuschedule.data.Course>
): Int {
    return courses
        .groupBy { course ->
            if (
                course.courseNo
                    .isNotBlank()
            ) {
                "${course.departmentCode}|" +
                        course.courseNo
            } else {
                "${course.departmentCode}|" +
                        "${course.subjectCode}|" +
                        "${course.className}|" +
                        course.courseName
            }
        }
        .size
}
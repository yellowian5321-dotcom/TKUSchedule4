package com.example.tkuschedule

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import com.example.tkuschedule.reminder.ClassReminderScheduler
import com.example.tkuschedule.reminder.ClassReminderNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.activity.ComponentActivity
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tkuschedule.location.NextClassCalculator
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import com.example.tkuschedule.data.Course
import com.example.tkuschedule.ai.AiAssistantScreen
import com.example.tkuschedule.assistant.FloatingCatAssistantController
import com.example.tkuschedule.ui.schedule.ScheduleScreen
import com.example.tkuschedule.ui.schedule.ScheduleViewModel

class MainActivity : ComponentActivity() {
    private lateinit var catController: FloatingCatAssistantController
    private lateinit var scheduleViewModel: ScheduleViewModel
    private val showAiAssistant = mutableStateOf(false)
    private var lastReminderKey: String? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) refreshPhoneReminders(force = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleViewModel = ViewModelProvider(this)[ScheduleViewModel::class.java]
        val root = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            ScheduleScreen(viewModel = scheduleViewModel)
                            if (showAiAssistant.value) {
                                AiAssistantDialog(scheduleViewModel) { showAiAssistant.value = false }
                            }
                        }
                    }
                }
            }
        }
        root.addView(composeView, FrameLayout.LayoutParams(-1, -1))
        val overlay = FrameLayout(this).apply {
            isClickable = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        // 原生小貓移動不會讓 Compose 課表跟著重組。
        catController = FloatingCatAssistantController(
            activity = this, overlayContainer = overlay,
            onOpenAssistant = { showAiAssistant.value = true }
        )
        ClassReminderNotifier.createChannel(this)
        requestNotificationPermissionOnce()
        // 新 Activity 啟動時補回可能被強制停止或系統清除的 PendingIntent。
        refreshPhoneReminders(force = true)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                scheduleViewModel.uiState.map { it.courses }.distinctUntilChanged().collect { courses ->
                    if (scheduleViewModel.uiState.value.isRestoringSchedule) return@collect
                    val next = NextClassCalculator.findNextClass(courses)
                    if (next == null) {
                        lastReminderKey = null
                        return@collect
                    }
                    val key = "${next.course.id}|${next.startDateTime}"
                    if (key != lastReminderKey) {
                        catController.showReminder("下一堂是「${next.courseName}」\n${next.startDateTime.toLocalDate()} ${next.startTimeText}｜${next.classroom}")
                        lastReminderKey = key
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        catController.resume()
        refreshPhoneReminders()
    }

    override fun onPause() {
        catController.pause()
        super.onPause()
    }

    override fun onDestroy() {
        catController.destroy()
        super.onDestroy()
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("tku_reminder_permission", MODE_PRIVATE)
        if (!prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPhoneReminders(force: Boolean = false) {
        val appContext = applicationContext
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    ClassReminderScheduler.refresh(appContext, force)
                } catch (error: Exception) {
                    Log.e("ClassReminder", "無法更新提醒排程", error)
                }
            }
        }
    }
}

@Composable
private fun AiAssistantDialog(scheduleViewModel: ScheduleViewModel, onClose: () -> Unit) {
    // 只在開啟 AI 對話時讀取完整課表狀態，首頁不跟著輸入欄位更新。
    val state by scheduleViewModel.uiState.collectAsStateWithLifecycle()
    val count = remember(state.courses) { countUniqueCourses(state.courses) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                AiAssistantTopBar(count, onClose)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AiAssistantScreen(courses = state.courses)
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
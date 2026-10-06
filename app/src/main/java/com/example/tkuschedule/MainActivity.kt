package com.example.tkuschedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tkuschedule.assistant.CatClassMessage
import com.example.tkuschedule.assistant.FloatingCatAssistantController
import com.example.tkuschedule.data.Course
import com.example.tkuschedule.location.NextClassCalculator
import com.example.tkuschedule.location.NextClassInfo
import com.example.tkuschedule.location.NextClassLocationViewModel
import com.example.tkuschedule.reminder.ClassReminderNotifier
import com.example.tkuschedule.reminder.ClassReminderPlan
import com.example.tkuschedule.reminder.ClassReminderScheduler
import com.example.tkuschedule.ui.schedule.ScheduleScreen
import com.example.tkuschedule.ui.schedule.ScheduleViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private lateinit var catController: FloatingCatAssistantController
    private lateinit var scheduleViewModel: ScheduleViewModel
    private lateinit var locationViewModel: NextClassLocationViewModel

    private var lastNextClassKey: Pair<Course, LocalDateTime>? = null
    private var pendingCatRequestKey: Pair<Course, LocalDateTime>? = null
    private var lastClassReminderKey: Pair<Course, LocalDateTime>? = null
    private val taipeiZone = ZoneId.of("Asia/Taipei")

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) refreshPhoneReminders(force = true)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasLocationPermission()) {
            showCatClassInfo()
        } else if (::catController.isInitialized) {
            catController.showReminder(
                CatClassMessage.build(
                    scheduleViewModel.uiState.value.courses,
                    locationViewModel.uiState.value
                ) + "\n可到手機設定允許本 App 使用位置喵。"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = ViewModelProvider(this)
        scheduleViewModel = provider[ScheduleViewModel::class.java]
        locationViewModel = provider[NextClassLocationViewModel::class.java]

        val root = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ScheduleScreen(viewModel = scheduleViewModel)
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

        catController = FloatingCatAssistantController(
            activity = this,
            overlayContainer = overlay,
            // 沿用控制器的回呼名稱，現在只顯示貓貓泡泡。
            onOpenAssistant = ::showCatClassInfo
        )

        ClassReminderNotifier.createChannel(this)
        requestNotificationPermissionOnce()
        refreshPhoneReminders(force = true)
        observeCatReminders()
    }

    private fun showCatClassInfo() {
        val state = scheduleViewModel.uiState.value
        if (state.isRestoringSchedule) {
            catController.showReminder("正在讀取你的課表，等一下再點我喵！")
            return
        }
        refreshCatLocation(state.courses, force = true)
        if (NextClassCalculator.findNextClass(state.courses) != null &&
            !hasLocationPermission()
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun refreshCatLocation(courses: List<Course>, force: Boolean) {
        val next = NextClassCalculator.findNextClass(courses)
        if (next == null) {
            pendingCatRequestKey = null
            locationViewModel.cancelRefresh()
            catController.showReminder(
                CatClassMessage.build(courses, locationViewModel.uiState.value)
            )
            return
        }
        pendingCatRequestKey = classKey(next)
        locationViewModel.refresh(courses, force)
        val location = locationViewModel.uiState.value
        catController.showReminder(CatClassMessage.build(courses, location))
        if (!location.isLoading) pendingCatRequestKey = null
    }

    private fun observeCatReminders() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(
                    scheduleViewModel.uiState.map {
                        it.courses to it.isRestoringSchedule
                    }.distinctUntilChanged(),
                    locationViewModel.uiState,
                    catClock()
                ) { schedule, location, clock ->
                    Triple(schedule, location, clock)
                }.collect { (schedule, location, clock) ->
                    val (courses, restoring) = schedule
                    if (restoring) return@collect

                    val nowMillis = System.currentTimeMillis()
                    val now = Instant.ofEpochMilli(nowMillis)
                        .atZone(taipeiZone).toLocalDateTime()
                    val next = NextClassCalculator.findNextClass(courses, now)
                    if (next == null) {
                        lastNextClassKey = null
                        lastClassReminderKey = null
                        pendingCatRequestKey = null
                        locationViewModel.cancelRefresh()
                        return@collect
                    }

                    val key = classKey(next)
                    if (lastNextClassKey != key) {
                        lastNextClassKey = key
                        refreshCatLocation(courses, force = false)
                        return@collect
                    }

                    if (pendingCatRequestKey == key && !location.isLoading &&
                        location.nextClass?.course == next.course &&
                        location.nextClass?.startDateTime == next.startDateTime
                    ) {
                        pendingCatRequestKey = null
                        catController.showReminder(
                            CatClassMessage.build(courses, location, nowMillis)
                        )
                    }

                    val remindAt = next.startDateTime
                        .minusMinutes(clock.minutesBefore.toLong())
                    if (clock.enabled && !now.isBefore(remindAt) &&
                        lastClassReminderKey != key
                    ) {
                        lastClassReminderKey = key
                        catController.showReminder(
                            "快上課了喵！\n" +
                                    CatClassMessage.build(courses, location, nowMillis)
                        )
                    }
                }
            }
        }
    }

    // 只更新時間及提醒設定，不會每 30 秒要求 GPS。
    private fun catClock() = flow {
        while (true) {
            val clock = withContext(Dispatchers.IO) {
                CatClock(
                    enabled = ClassReminderScheduler.isEnabled(applicationContext),
                    minutesBefore = ClassReminderScheduler.getMinutesBefore(
                        applicationContext
                    )
                )
            }
            emit(clock)
            delay(30_000L)
        }
    }

    private fun classKey(next: NextClassInfo) =
        next.course to next.startDateTime

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (::catController.isInitialized) catController.resume()
        lastNextClassKey = null
        refreshPhoneReminders()
    }

    override fun onPause() {
        pendingCatRequestKey = null
        if (::locationViewModel.isInitialized) locationViewModel.cancelRefresh()
        if (::catController.isInitialized) catController.pause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::catController.isInitialized) catController.destroy()
        super.onDestroy()
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences("tku_reminder_permission", MODE_PRIVATE)
        if (!prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPhoneReminders(force: Boolean = false) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    ClassReminderScheduler.refresh(applicationContext, force)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("ClassReminder", "無法更新提醒排程", error)
                }
            }
        }
    }

    private data class CatClock(
        val enabled: Boolean,
        val minutesBefore: Int = ClassReminderPlan.DEFAULT_MINUTES_BEFORE
    )
}

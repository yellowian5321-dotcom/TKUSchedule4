package com.example.tkuschedule.reminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReminderSettingsStatus(
    val enabled: Boolean,
    val allowed: Boolean,
    val exact: Boolean,
    val minutesBefore: Int
)

@Composable
fun ClassReminderSettingsCard(
    courseCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var version by remember {
        mutableIntStateOf(0)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var ready by remember {
        mutableStateOf(false)
    }

    var enabled by remember {
        mutableStateOf(true)
    }

    var allowed by remember {
        mutableStateOf(false)
    }

    var exact by remember {
        mutableStateOf(false)
    }

    var minutesBefore by remember {
        mutableIntStateOf(
            ClassReminderPlan.DEFAULT_MINUTES_BEFORE
        )
    }

    var showMinutesDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var minutesInput by rememberSaveable {
        mutableStateOf("15")
    }

    LaunchedEffect(version, appContext) {
        try {
            val status = withContext(Dispatchers.IO) {
                ClassReminderNotifier.createChannel(appContext)

                ReminderSettingsStatus(
                    enabled =
                        ClassReminderScheduler.isEnabled(appContext),
                    allowed =
                        ClassReminderNotifier.isAllowed(appContext),
                    exact =
                        ClassReminderScheduler.canScheduleExactly(appContext),
                    minutesBefore =
                        ClassReminderScheduler.getMinutesBefore(appContext)
                )
            }

            enabled = status.enabled
            allowed = status.allowed
            exact = status.exact
            minutesBefore = status.minutesBefore
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ready = false

            Toast.makeText(
                context,
                "提醒設定讀取失敗，請重新開啟設定頁",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun refreshSchedule() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ClassReminderScheduler.refresh(
                        appContext,
                        force = true
                    )
                }
            }

            if (result.isFailure) {
                Toast.makeText(
                    context,
                    "提醒排程更新失敗，請重新開啟 App",
                    Toast.LENGTH_LONG
                ).show()
            }

            version++
        }
    }

    fun saveMinutes(value: Int) {
        if (saving) return

        saving = true

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        ClassReminderScheduler.setMinutesBefore(
                            appContext,
                            value
                        )
                    }
                }

                version++

                if (result.isSuccess) {
                    minutesBefore = value
                    showMinutesDialog = false

                    Toast.makeText(
                        context,
                        "已設定上課前 $value 分鐘提醒",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "提醒時間或排程更新失敗，請再按儲存重試",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                saving = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        version++
        refreshSchedule()
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                version++
            }
        }

        owner.lifecycle.addObserver(observer)

        onDispose {
            owner.lifecycle.removeObserver(observer)
        }
    }

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "請到手機設定中開啟這個 App 的通知或鬧鐘與提醒權限",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "上課提醒",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = if (ready) {
                            "上課前 $minutesBefore 分鐘"
                        } else {
                            "正在讀取提醒設定…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = enabled,
                    enabled = ready && !saving,
                    onCheckedChange = { value ->
                        saving = true

                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        ClassReminderScheduler.setEnabled(
                                            appContext,
                                            value
                                        )

                                        ClassReminderScheduler.isEnabled(
                                            appContext
                                        )
                                    }
                                }

                                enabled = result.getOrDefault(enabled)
                                version++

                                if (result.isFailure) {
                                    Toast.makeText(
                                        context,
                                        "提醒設定或排程更新失敗，請重試",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } finally {
                                saving = false
                            }
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "提醒時間",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = "可自訂提前幾分鐘",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    enabled = ready && !saving,
                    onClick = {
                        minutesInput = minutesBefore.toString()
                        showMinutesDialog = true
                    }
                ) {
                    Text("提前 $minutesBefore 分鐘")
                }
            }

            Text(
                text = when {
                    !ready ->
                        "正在讀取提醒設定…"

                    saving ->
                        "正在更新提醒…"

                    !enabled ->
                        "提醒已關閉，時間設定仍會保留"

                    !allowed ->
                        "尚未允許通知，請先開啟通知權限"

                    courseCount == 0 ->
                        "匯入課表後會自動安排提醒"

                    !exact ->
                        "已啟用；開啟準時提醒可減少系統延後"

                    else ->
                        "已啟用，離開 App 後仍會提醒"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (ready && !allowed) {
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            if (
                                Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permission.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                openSettings(
                                    Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    ).putExtra(
                                        Settings.EXTRA_APP_PACKAGE,
                                        context.packageName
                                    )
                                )
                            }
                        }
                    ) {
                        Text("允許通知")
                    }
                }

                if (
                    ready &&
                    Build.VERSION.SDK_INT >= 31 &&
                    !exact
                ) {
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            openSettings(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse(
                                        "package:${context.packageName}"
                                    )
                                )
                            )
                        }
                    ) {
                        Text("準時提醒")
                    }
                }

                TextButton(
                    enabled = ready && !saving,
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    ClassReminderNotifier.showTest(
                                        appContext
                                    )
                                }
                            }

                            val message = when {
                                result.isFailure ->
                                    "測試通知發送失敗，請重試"

                                result.getOrDefault(false) ->
                                    "已送出測試通知，請查看手機通知列"

                                else ->
                                    "請先允許 App 通知"
                            }

                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("測試通知")
                }
            }

            TextButton(
                onClick = {
                    openSettings(
                        Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        ).putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            context.packageName
                        )
                    )
                }
            ) {
                Text("手機通知設定")
            }
        }
    }

    if (showMinutesDialog) {
        val inputMinutes = minutesInput.toIntOrNull()

        val valid =
            inputMinutes != null &&
                    inputMinutes in
                    ClassReminderPlan.MIN_MINUTES_BEFORE..
                    ClassReminderPlan.MAX_MINUTES_BEFORE

        AlertDialog(
            onDismissRequest = {
                if (!saving) {
                    showMinutesDialog = false
                }
            },
            title = {
                Text("上課前幾分鐘提醒？")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "快速選擇",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(5, 10, 15, 30).forEach { minutes ->
                            TextButton(
                                modifier = Modifier.weight(1f),
                                enabled = !saving,
                                onClick = {
                                    minutesInput = minutes.toString()
                                }
                            ) {
                                Text("$minutes 分")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { value ->
                            if (value.all { it in '0'..'9' }) {
                                minutesInput = value
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text("提前分鐘數")
                        },
                        suffix = {
                            Text("分鐘")
                        },
                        isError = !valid,
                        supportingText = {
                            Text(
                                "請輸入 " +
                                        "${ClassReminderPlan.MIN_MINUTES_BEFORE}～" +
                                        "${ClassReminderPlan.MAX_MINUTES_BEFORE} 分鐘"
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (valid && !saving) {
                                    inputMinutes?.let {
                                        saveMinutes(it)
                                    }
                                }
                            }
                        )
                    )

                    Text(
                        text = "儲存後會套用到課表中的所有課程。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = valid && !saving,
                    onClick = {
                        inputMinutes?.let {
                            saveMinutes(it)
                        }
                    }
                ) {
                    Text(
                        if (saving) {
                            "儲存中…"
                        } else {
                            "儲存"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        showMinutesDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}
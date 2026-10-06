package com.example.tkuschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tkuschedule.reminder.ClassReminderSettingsCard

@Composable
fun SettingsScreen(
    courseCount: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(
            key = "settings_intro",
            contentType = "text"
        ) {
            Text(
                text = "管理上課提醒與手機通知",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item(
            key = "class_reminders",
            contentType = "reminder_settings"
        ) {
            ClassReminderSettingsCard(
                courseCount = courseCount
            )
        }
    }
}
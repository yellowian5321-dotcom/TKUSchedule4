package com.example.tkuschedule.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.tkuschedule.MainActivity
import com.example.tkuschedule.R
import java.time.Instant
import java.time.format.DateTimeFormatter

object ClassReminderNotifier {
    const val CHANNEL_ID =
        "tku_class_reminders_v1"

    fun createChannel(
        context: Context
    ) {
        val manager = context.getSystemService(
            NotificationManager::class.java
        )

        val channel = NotificationChannel(
            CHANNEL_ID,
            "上課提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                "依照設定的提前時間提醒課表中的課程"

            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }

    fun isAllowed(
        context: Context
    ): Boolean {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = context.getSystemService(
            NotificationManager::class.java
        )

        return manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL_ID)
                    ?.importance !=
                NotificationManager.IMPORTANCE_NONE
    }

    fun showClass(
        context: Context,
        session: ReminderSession,
        classStartMillis: Long
    ): Boolean {
        val time = Instant
            .ofEpochMilli(classStartMillis)
            .atZone(ClassReminderPlan.zone)
            .format(
                DateTimeFormatter.ofPattern(
                    "M/d HH:mm"
                )
            )

        val text = buildString {
            append(
                "$time 上課\n教室：" +
                        session.classroom.ifBlank {
                            "未公告"
                        }
            )

            if (session.teacher.isNotBlank()) {
                append(
                    "\n教師：${session.teacher}"
                )
            }
        }

        return show(
            context = context,
            tag = session.id,
            title = "快上課了：${session.courseName}",
            text = text
        )
    }

    fun showTest(
        context: Context
    ): Boolean {
        return show(
            context = context,
            tag = "test",
            title = "課表通知測試成功",
            text =
                "手機已允許 App 通知。" +
                        "上課提醒仍依課表與提醒開關設定。"
        )
    }

    private fun show(
        context: Context,
        tag: String,
        title: String,
        text: String
    ): Boolean {
        createChannel(context)

        if (!isAllowed(context)) {
            return false
        }

        val openApp = Intent(
            context,
            MainActivity::class.java
        ).apply {
            data = Uri.parse(
                "tkuschedule://open/$tag"
            )

            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pending = PendingIntent.getActivity(
            context,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.drawable.ic_class_reminder
            )
            .setContentTitle(title)
            .setContentText(
                text.replace('\n', ' ')
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(text)
            )
            .setPriority(
                NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(
                NotificationCompat.CATEGORY_REMINDER
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        return try {
            context
                .getSystemService(
                    NotificationManager::class.java
                )
                .notify(
                    tag,
                    0,
                    notification
                )

            true
        } catch (_: SecurityException) {
            false
        }
    }
}
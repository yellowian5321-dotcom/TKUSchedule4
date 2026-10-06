package com.example.tkuschedule.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ClassReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        ClassReminderScheduler.receiverExecutor.execute {
            try {
                ClassReminderScheduler.refresh(context.applicationContext, force = true)
            } catch (error: Exception) {
                Log.e("ClassReminder", "無法恢復上課提醒", error)
            } finally {
                pending.finish()
            }
        }
    }
}
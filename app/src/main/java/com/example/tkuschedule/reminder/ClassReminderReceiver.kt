package com.example.tkuschedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ClassReminderScheduler.ACTION_TRIGGER) return
        val id = intent.data?.lastPathSegment ?: return
        val start = intent.getLongExtra(ClassReminderScheduler.EXTRA_CLASS_START, 0L)
        if (start <= 0L) return
        val pending = goAsync()
        ClassReminderScheduler.receiverExecutor.execute {
            try {
                ClassReminderScheduler.receive(context.applicationContext, id, start)
            } catch (error: Exception) {
                Log.e("ClassReminder", "無法處理上課提醒", error)
            } finally {
                pending.finish()
            }
        }
    }
}
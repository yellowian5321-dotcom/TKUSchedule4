package com.example.tkuschedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
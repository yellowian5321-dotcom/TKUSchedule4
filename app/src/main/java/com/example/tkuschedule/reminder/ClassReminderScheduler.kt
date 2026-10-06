package com.example.tkuschedule.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.tkuschedule.data.Course
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 所有讀寫與重排使用同一把鎖。
 * 呼叫端應在背景執行緒執行。
 */
object ClassReminderScheduler {
    const val ACTION_TRIGGER =
        "com.example.tkuschedule.CLASS_REMINDER"

    const val EXTRA_CLASS_START =
        "class_start_millis"

    private const val PREFS =
        "tku_class_reminders_v1"

    private val lock = Any()

    val receiverExecutor =
        Executors.newSingleThreadExecutor()

    fun isEnabled(
        context: Context
    ): Boolean {
        return prefs(context).getBoolean(
            "enabled",
            true
        )
    }

    fun getMinutesBefore(
        context: Context
    ): Int {
        return prefs(context)
            .getInt(
                "minutes_before",
                ClassReminderPlan.DEFAULT_MINUTES_BEFORE
            )
            .coerceIn(
                ClassReminderPlan.MIN_MINUTES_BEFORE,
                ClassReminderPlan.MAX_MINUTES_BEFORE
            )
    }

    fun setMinutesBefore(
        context: Context,
        minutes: Int
    ) = synchronized(lock) {
        require(
            minutes in
                    ClassReminderPlan.MIN_MINUTES_BEFORE..
                    ClassReminderPlan.MAX_MINUTES_BEFORE
        ) {
            "提醒時間請設定為 " +
                    "${ClassReminderPlan.MIN_MINUTES_BEFORE}～" +
                    "${ClassReminderPlan.MAX_MINUTES_BEFORE} 分鐘"
        }

        check(
            prefs(context)
                .edit()
                .putInt("minutes_before", minutes)
                .commit()
        ) {
            "提醒時間儲存失敗"
        }

        // 改變時間後立即重新安排通知。
        refreshLocked(context, true)
    }

    fun canScheduleExactly(
        context: Context
    ): Boolean {
        return Build.VERSION.SDK_INT < 31 ||
                context
                    .getSystemService(AlarmManager::class.java)
                    .canScheduleExactAlarms()
    }

    fun updateCourses(
        context: Context,
        courses: List<Course>
    ) = synchronized(lock) {
        val sessions = ClassReminderPlan.sessions(courses)
        val old = readSessions(context)
        val activeIds = sessions.map { it.id }.toSet()
        val editor = prefs(context).edit()

        old.filter {
            it.id !in activeIds
        }.forEach {
            cancel(context, it.id)

            editor
                .remove("scheduled.${it.id}")
                .remove("delivered.${it.id}")
        }

        val encoded = encode(sessions)

        if (
            prefs(context).getString("sessions", null) !=
            encoded
        ) {
            editor.putString("sessions", encoded)
        }

        check(editor.commit()) {
            "通知課表儲存失敗"
        }

        refreshLocked(context, false)
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) = synchronized(lock) {
        check(
            prefs(context)
                .edit()
                .putBoolean("enabled", enabled)
                .commit()
        ) {
            "提醒設定儲存失敗"
        }

        refreshLocked(context, true)
    }

    fun refresh(
        context: Context,
        force: Boolean = false
    ) = synchronized(lock) {
        refreshLocked(context, force)
    }

    fun receive(
        context: Context,
        id: String,
        expectedStart: Long
    ) = synchronized(lock) {
        val session = readSessions(context)
            .firstOrNull {
                it.id == id
            }
            ?: return@synchronized

        if (!isEnabled(context)) {
            return@synchronized
        }

        val store = prefs(context)
        val now = System.currentTimeMillis()
        val minutesBefore = getMinutesBefore(context)

        val dueStart = ClassReminderPlan.nextMoment(
            session = session,
            nowMillis = now,
            deliveredClassStartMillis = 0L,
            minutesBefore = minutesBefore
        ).classStartMillis

        // 依照目前設定檢查提醒範圍，避免舊排程提前發送。
        if (
            expectedStart == dueStart &&
            now >= expectedStart -
            minutesBefore.toLong() * 60_000L &&
            now < expectedStart &&
            store.getLong("delivered.$id", 0L) <
            expectedStart
        ) {
            check(
                store.edit()
                    .putLong("delivered.$id", expectedStart)
                    .commit()
            ) {
                "提醒狀態儲存失敗"
            }

            ClassReminderNotifier.showClass(
                context,
                session,
                expectedStart
            )
        }

        store.edit()
            .remove("scheduled.$id")
            .apply()

        refreshLocked(context, false)
    }

    private fun refreshLocked(
        context: Context,
        force: Boolean
    ) {
        ClassReminderNotifier.createChannel(context)

        val store = prefs(context)
        val sessions = readSessions(context)
        val minutesBefore = getMinutesBefore(context)

        val timeChanged =
            store.getInt(
                "scheduled_minutes_before",
                -1
            ) != minutesBefore

        val mode = when {
            !isEnabled(context) ||
                    !ClassReminderNotifier.isAllowed(context) ->
                "off"

            canScheduleExactly(context) ->
                "exact"

            else ->
                "approximate"
        }

        val modeChanged =
            store.getString("mode", null) != mode

        if (mode == "off") {
            if (force || modeChanged) {
                val editor = store.edit()

                sessions.forEach {
                    cancel(context, it.id)
                    editor.remove("scheduled.${it.id}")
                }

                check(
                    editor.putString("mode", mode).commit()
                ) {
                    "提醒狀態儲存失敗"
                }
            }

            return
        }

        val manager = context.getSystemService(
            AlarmManager::class.java
        )

        sessions.forEach { session ->
            val moment = ClassReminderPlan.nextMoment(
                session = session,
                deliveredClassStartMillis =
                    store.getLong(
                        "delivered.${session.id}",
                        0L
                    ),
                minutesBefore = minutesBefore
            )

            if (
                !force &&
                !modeChanged &&
                !timeChanged &&
                store.getLong(
                    "scheduled.${session.id}",
                    0L
                ) == moment.classStartMillis
            ) {
                return@forEach
            }

            val pending = alarmIntent(
                context = context,
                id = session.id,
                start = moment.classStartMillis,
                existingOnly = false
            )!!

            if (mode == "exact") {
                try {
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        moment.reminderMillis,
                        pending
                    )
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        moment.reminderMillis,
                        pending
                    )
                }
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    moment.reminderMillis,
                    pending
                )
            }

            check(
                store.edit()
                    .putLong(
                        "scheduled.${session.id}",
                        moment.classStartMillis
                    )
                    .commit()
            ) {
                "提醒狀態儲存失敗"
            }
        }

        check(
            store.edit()
                .putString("mode", mode)
                .putInt(
                    "scheduled_minutes_before",
                    minutesBefore
                )
                .commit()
        ) {
            "提醒狀態儲存失敗"
        }
    }

    private fun cancel(
        context: Context,
        id: String
    ) {
        alarmIntent(
            context = context,
            id = id,
            start = 0L,
            existingOnly = true
        )?.let {
            context
                .getSystemService(AlarmManager::class.java)
                .cancel(it)

            it.cancel()
        }

        context
            .getSystemService(NotificationManager::class.java)
            .cancel(id, 0)
    }

    private fun alarmIntent(
        context: Context,
        id: String,
        start: Long,
        existingOnly: Boolean
    ): PendingIntent? {
        val intent = Intent(
            context,
            ClassReminderReceiver::class.java
        ).apply {
            action = ACTION_TRIGGER
            data = Uri.parse(
                "tkuschedule://class-reminder/$id"
            )
            putExtra(EXTRA_CLASS_START, start)
        }

        val flags =
            PendingIntent.FLAG_IMMUTABLE or
                    if (existingOnly) {
                        PendingIntent.FLAG_NO_CREATE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            flags
        )
    }

    private fun prefs(
        context: Context
    ) = context.applicationContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    private fun readSessions(
        context: Context
    ): List<ReminderSession> {
        val array = JSONArray(
            prefs(context).getString(
                "sessions",
                "[]"
            )
        )

        return List(array.length()) { index ->
            val session = array.getJSONObject(index)

            ReminderSession(
                id = session.getString("id"),
                courseName = session.getString("courseName"),
                classroom = session.getString("classroom"),
                teacher = session.getString("teacher"),
                weekday = session.getInt("weekday"),
                startPeriod = session.getInt("startPeriod")
            )
        }
    }

    private fun encode(
        sessions: List<ReminderSession>
    ): String {
        return JSONArray().apply {
            sessions.forEach { session ->
                put(
                    JSONObject()
                        .put("id", session.id)
                        .put("courseName", session.courseName)
                        .put("classroom", session.classroom)
                        .put("teacher", session.teacher)
                        .put("weekday", session.weekday)
                        .put("startPeriod", session.startPeriod)
                )
            }
        }.toString()
    }
}
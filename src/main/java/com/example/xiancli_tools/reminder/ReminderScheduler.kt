package com.example.xiancli_tools.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.xiancli_tools.timer.TimerScheduler

/**
 * 备忘提醒的闹钟调度。
 *
 * 走「一次性闹钟 + 到点再由通知上的『稍后』重排」的方式，而不是 `setRepeating`：
 * 后者在 Doze 下会被打散。请求码由备忘 id 推导，所以重启后能找回同一个闹钟。
 */
object ReminderScheduler {

    const val EXTRA_TARGET_ID = "extra_target_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"

    /** 推迟提醒的间隔。 */
    const val SNOOZE_MILLIS = 10 * 60_000L

    fun scheduleNote(context: Context, noteId: Long, fireAt: Long, content: String) {
        val title = "备忘提醒"
        val body = content.trim().ifEmpty { "有一条备忘到点了" }
        arm(
            context = context,
            requestCode = noteRequestCode(noteId),
            fireAt = fireAt,
            targetId = noteId,
            title = title,
            body = body
        )
    }

    fun cancelNote(context: Context, noteId: Long) {
        cancel(context, noteRequestCode(noteId))
    }

    fun snooze(context: Context, noteId: Long, title: String, body: String) {
        arm(
            context = context,
            requestCode = noteRequestCode(noteId),
            fireAt = System.currentTimeMillis() + SNOOZE_MILLIS,
            targetId = noteId,
            title = title,
            body = body
        )
    }

    /** 通知 id：按 id 取模，够用且不会和计时响铃撞车。 */
    fun notificationId(noteId: Long): Int =
        20_000 + (noteId % 1_000L).toInt() + ((noteId / 1_000L) % 900L).toInt() * 10

    private fun arm(
        context: Context,
        requestCode: Int,
        fireAt: Long,
        targetId: Long,
        title: String,
        body: String
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(
            appContext,
            requestCode,
            ReminderReceiver.ACTION_FIRE,
            targetId,
            title,
            body
        )
        val safeFireAt = fireAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        if (TimerScheduler.canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeFireAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeFireAt, pendingIntent)
        }
    }

    private fun cancel(context: Context, requestCode: Int) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(
            appContext,
            requestCode,
            ReminderReceiver.ACTION_FIRE,
            0L,
            "",
            ""
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        action: String,
        targetId: Long,
        title: String,
        body: String
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TARGET_ID, targetId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun noteRequestCode(noteId: Long): Int = (noteId % 100_000L).toInt()

    /** 精确闹钟权限没开时的提示，和计时页共用同一套判断。 */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
}

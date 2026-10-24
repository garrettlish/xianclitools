package com.example.xiancli_tools.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.xiancli_tools.data.HabitAnalytics
import com.example.xiancli_tools.timer.TimerScheduler

/**
 * 备忘提醒与习惯的每日提醒共用的闹钟调度。
 *
 * 两者都走「一次性闹钟 + 到点再排下一次」的方式，而不是 `setRepeating`：
 * 后者在 Doze 下会被打散，且改时间要先取消重排，反而更麻烦。
 * 请求码由业务 id 推导，所以同一条备忘重启后能找回同一个闹钟。
 */
object ReminderScheduler {

    const val KIND_NOTE = "note"
    const val KIND_HABIT = "habit"

    const val EXTRA_KIND = "extra_kind"
    const val EXTRA_TARGET_ID = "extra_target_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_DAILY = "extra_daily"
    const val EXTRA_MINUTE_OF_DAY = "extra_minute_of_day"

    /** 推迟提醒的间隔。 */
    const val SNOOZE_MILLIS = 10 * 60_000L

    fun scheduleNote(context: Context, noteId: Long, fireAt: Long, content: String) {
        val title = "备忘提醒"
        val body = content.trim().ifEmpty { "有一条备忘到点了" }
        arm(
            context = context,
            requestCode = noteRequestCode(noteId),
            fireAt = fireAt,
            kind = KIND_NOTE,
            targetId = noteId,
            title = title,
            body = body,
            daily = false,
            minuteOfDay = -1
        )
    }

    fun cancelNote(context: Context, noteId: Long) {
        cancel(context, noteRequestCode(noteId), KIND_NOTE, noteId)
    }

    /** 习惯：排下一次提醒（今天还没到就今天，否则明天）。 */
    fun scheduleHabit(context: Context, habitId: Long, minuteOfDay: Int, habitName: String) {
        arm(
            context = context,
            requestCode = habitRequestCode(habitId),
            fireAt = HabitAnalytics.nextReminderAt(minuteOfDay),
            kind = KIND_HABIT,
            targetId = habitId,
            title = "习惯打卡",
            body = "该「$habitName」了",
            daily = true,
            minuteOfDay = minuteOfDay
        )
    }

    fun cancelHabit(context: Context, habitId: Long) {
        cancel(context, habitRequestCode(habitId), KIND_HABIT, habitId)
    }

    /** 今天已经打过卡：把提醒推到明天，别在同一天反复催。 */
    fun scheduleHabitTomorrow(context: Context, habitId: Long, minuteOfDay: Int, habitName: String) {
        arm(
            context = context,
            requestCode = habitRequestCode(habitId),
            fireAt = HabitAnalytics.tomorrowReminderAt(minuteOfDay),
            kind = KIND_HABIT,
            targetId = habitId,
            title = "习惯打卡",
            body = "该「$habitName」了",
            daily = true,
            minuteOfDay = minuteOfDay
        )
    }

    /** 到点后再排下一次（每日习惯用），或者推迟 10 分钟（用户的「稍后」）。 */
    fun scheduleNextDaily(context: Context, kind: String, targetId: Long, minuteOfDay: Int, title: String, body: String) {
        arm(
            context = context,
            requestCode = habitRequestCode(targetId),
            fireAt = HabitAnalytics.nextReminderAt(minuteOfDay),
            kind = kind,
            targetId = targetId,
            title = title,
            body = body,
            daily = true,
            minuteOfDay = minuteOfDay
        )
    }

    fun snooze(context: Context, kind: String, targetId: Long, title: String, body: String) {
        val code = if (kind == KIND_HABIT) habitRequestCode(targetId) else noteRequestCode(targetId)
        arm(
            context = context,
            requestCode = code,
            fireAt = System.currentTimeMillis() + SNOOZE_MILLIS,
            kind = kind,
            targetId = targetId,
            title = title,
            body = body,
            daily = false,
            minuteOfDay = -1
        )
    }

    /** 通知 id：同一类里按 id 取模，够用且不会和计时响铃撞车。 */
    fun notificationId(kind: String, targetId: Long): Int {
        val offset = if (kind == KIND_HABIT) 30_000 else 20_000
        return offset + (targetId % 1_000L).toInt() + ((targetId / 1_000L) % 900L).toInt() * 10
    }

    private fun arm(
        context: Context,
        requestCode: Int,
        fireAt: Long,
        kind: String,
        targetId: Long,
        title: String,
        body: String,
        daily: Boolean,
        minuteOfDay: Int
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(
            appContext,
            requestCode,
            ReminderReceiver.ACTION_FIRE,
            kind,
            targetId,
            title,
            body,
            daily,
            minuteOfDay
        )
        val safeFireAt = fireAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        if (TimerScheduler.canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeFireAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeFireAt, pendingIntent)
        }
    }

    private fun cancel(context: Context, requestCode: Int, kind: String, targetId: Long) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(
            appContext,
            requestCode,
            ReminderReceiver.ACTION_FIRE,
            kind,
            targetId,
            "",
            "",
            false,
            -1
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        action: String,
        kind: String,
        targetId: Long,
        title: String,
        body: String,
        daily: Boolean,
        minuteOfDay: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_TARGET_ID, targetId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_DAILY, daily)
            putExtra(EXTRA_MINUTE_OF_DAY, minuteOfDay)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun noteRequestCode(noteId: Long): Int = (noteId % 100_000L).toInt()

    private fun habitRequestCode(habitId: Long): Int = 200_000 + (habitId % 100_000L).toInt()

    /** 精确闹钟权限没开时的提示，和计时页共用同一套判断。 */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
}

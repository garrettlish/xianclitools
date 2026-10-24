package com.example.xiancli_tools.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.xiancli_tools.data.HabitAnalytics
import com.example.xiancli_tools.data.HabitRepository
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.timer.NotificationHelper

/**
 * 备忘 / 习惯提醒的接收器，同时处理通知上的两个动作：
 *
 * - 完成：备忘勾成已办、习惯补上今天的打卡，然后撤掉通知。
 * - 稍后：10 分钟后再响一次（只响一次，不会打乱每日习惯的下一次提醒）。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val targetId = intent.getLongExtra(ReminderScheduler.EXTRA_TARGET_ID, 0L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "提醒"
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: ""
        val daily = intent.getBooleanExtra(ReminderScheduler.EXTRA_DAILY, false)
        val minuteOfDay = intent.getIntExtra(ReminderScheduler.EXTRA_MINUTE_OF_DAY, -1)

        when (intent.action) {
            ACTION_FIRE -> {
                // 每日习惯：先把明天那次排好，再发通知，顺序反了会漏一天。
                if (kind == ReminderScheduler.KIND_HABIT && daily && minuteOfDay in 0..1439) {
                    ReminderScheduler.scheduleNextDaily(
                        context, kind, targetId, minuteOfDay, title, body
                    )
                }
                post(context, kind, targetId, title, body, daily, minuteOfDay)
            }

            ACTION_DONE -> {
                markDone(context, kind, targetId)
                dismiss(context, kind, targetId)
            }

            ACTION_SNOOZE -> {
                ReminderScheduler.snooze(context, kind, targetId, title, body)
                dismiss(context, kind, targetId)
            }
        }
    }

    private fun post(
        context: Context,
        kind: String,
        targetId: Long,
        title: String,
        body: String,
        daily: Boolean,
        minuteOfDay: Int
    ) {
        NotificationHelper.ensureChannels(context)
        val notification = NotificationHelper.buildReminderNotification(
            context = context,
            title = title,
            body = body,
            kind = kind,
            targetId = targetId,
            daily = daily,
            minuteOfDay = minuteOfDay
        )
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ReminderScheduler.notificationId(kind, targetId), notification)
        }.onFailure { Log.e(TAG, "发送提醒通知失败", it) }
    }

    private fun dismiss(context: Context, kind: String, targetId: Long) {
        runCatching {
            NotificationManagerCompat.from(context)
                .cancel(ReminderScheduler.notificationId(kind, targetId))
        }
    }

    private fun markDone(context: Context, kind: String, targetId: Long) {
        val appContext = context.applicationContext
        runCatching {
            if (kind == ReminderScheduler.KIND_HABIT) {
                val repository = HabitRepository(appContext)
                repository.loadHabits()
                    .firstOrNull { it.id == targetId }
                    ?.let { repository.saveHabit(HabitAnalytics.checkIn(it)) }
            } else {
                val repository = NoteRepository(appContext)
                repository.loadNotes()
                    .firstOrNull { it.id == targetId }
                    ?.let { repository.saveNote(it.copy(done = true, remindAt = null)) }
            }
        }.onFailure { Log.e(TAG, "处理提醒失败", it) }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_FIRE = "com.example.xiancli_tools.action.REMINDER_FIRE"
        const val ACTION_DONE = "com.example.xiancli_tools.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.example.xiancli_tools.action.REMINDER_SNOOZE"
    }
}

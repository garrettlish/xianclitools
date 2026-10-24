package com.example.xiancli_tools.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.timer.NotificationHelper

/**
 * 备忘提醒的接收器，同时处理通知上的两个动作：
 *
 * - 完成：把待办勾成已办、清掉提醒时间，然后撤掉通知。
 * - 稍后：10 分钟后再响一次。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val targetId = intent.getLongExtra(ReminderScheduler.EXTRA_TARGET_ID, 0L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "提醒"
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: ""

        when (intent.action) {
            ACTION_FIRE -> post(context, targetId, title, body)

            ACTION_DONE -> {
                markDone(context, targetId)
                dismiss(context, targetId)
            }

            ACTION_SNOOZE -> {
                ReminderScheduler.snooze(context, targetId, title, body)
                dismiss(context, targetId)
            }
        }
    }

    private fun post(context: Context, targetId: Long, title: String, body: String) {
        NotificationHelper.ensureChannels(context)
        val notification = NotificationHelper.buildReminderNotification(
            context = context,
            title = title,
            body = body,
            noteId = targetId
        )
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ReminderScheduler.notificationId(targetId), notification)
        }.onFailure { Log.e(TAG, "发送提醒通知失败", it) }
    }

    private fun dismiss(context: Context, targetId: Long) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(ReminderScheduler.notificationId(targetId))
        }
    }

    private fun markDone(context: Context, targetId: Long) {
        val appContext = context.applicationContext
        runCatching {
            val repository = NoteRepository(appContext)
            repository.loadNotes()
                .firstOrNull { it.id == targetId }
                ?.let { repository.saveNote(it.copy(done = true, remindAt = null)) }
        }.onFailure { Log.e(TAG, "处理提醒失败", it) }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_FIRE = "com.example.xiancli_tools.action.REMINDER_FIRE"
        const val ACTION_DONE = "com.example.xiancli_tools.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.example.xiancli_tools.action.REMINDER_SNOOZE"
    }
}

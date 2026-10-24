package com.example.xiancli_tools.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.xiancli_tools.R
import com.example.xiancli_tools.reminder.ReminderReceiver
import com.example.xiancli_tools.reminder.ReminderScheduler

object NotificationHelper {

    const val CHANNEL_ALARM = "timer_alarm"
    const val CHANNEL_REMINDER = "reminder"
    const val NOTIFICATION_ID_RINGING = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (manager.getNotificationChannel(CHANNEL_ALARM) == null) {
            val channel = NotificationChannel(
                CHANNEL_ALARM,
                "定时提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "计时结束时的提醒"
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        if (manager.getNotificationChannel(CHANNEL_REMINDER) == null) {
            val channel = NotificationChannel(
                CHANNEL_REMINDER,
                "备忘与习惯提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "备忘、待办的到点提醒，以及习惯的每日提醒"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildRingingNotification(context: Context, label: String): Notification {
        val stopIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val openPendingIntent = openIntent?.let {
            PendingIntent.getActivity(
                context,
                2,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_tool_timer)
            .setContentTitle("定时结束")
            .setContentText("$label 的定时已完成")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_stop, "停止", stopPendingIntent)
            .build()
    }

    /**
     * 备忘 / 习惯的到点通知：带「完成」与「稍后 10 分钟」两个动作，
     * 不用打开 App 就能处理掉，这是提醒类功能最要紧的一点。
     */
    fun buildReminderNotification(
        context: Context,
        title: String,
        body: String,
        kind: String,
        targetId: Long,
        daily: Boolean,
        minuteOfDay: Int
    ): Notification {
        val doneIntent = ReminderScheduler.buildPendingIntent(
            context = context,
            requestCode = ACTION_REQUEST_CODE,
            action = ReminderReceiver.ACTION_DONE,
            kind = kind,
            targetId = targetId,
            title = title,
            body = body,
            daily = daily,
            minuteOfDay = minuteOfDay
        )
        val snoozeIntent = ReminderScheduler.buildPendingIntent(
            context = context,
            requestCode = ACTION_REQUEST_CODE,
            action = ReminderReceiver.ACTION_SNOOZE,
            kind = kind,
            targetId = targetId,
            title = title,
            body = body,
            daily = daily,
            minuteOfDay = minuteOfDay
        )
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPendingIntent = openIntent?.let {
            PendingIntent.getActivity(
                context,
                OPEN_REQUEST_CODE,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(
                if (kind == ReminderScheduler.KIND_HABIT) R.drawable.ic_tool_habit
                else R.drawable.ic_tool_note
            )
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_check, "完成", doneIntent)
            .addAction(R.drawable.ic_alarm, "稍后 10 分钟", snoozeIntent)
            .build()
    }

    private const val ACTION_REQUEST_CODE = 4_001
    private const val OPEN_REQUEST_CODE = 4_002
}

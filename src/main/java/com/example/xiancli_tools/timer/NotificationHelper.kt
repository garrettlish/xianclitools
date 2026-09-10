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

object NotificationHelper {

    const val CHANNEL_ALARM = "timer_alarm"
    const val NOTIFICATION_ID_RINGING = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ALARM) != null) return
        val channel = NotificationChannel(
            CHANNEL_ALARM,
            "定时提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "定时器结束时的提醒"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
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
}

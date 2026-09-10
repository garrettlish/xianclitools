package com.example.xiancli_tools.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.xiancli_tools.data.SettingsRepository

object TimerScheduler {

    private const val REQUEST_CODE = 2001
    const val EXTRA_LABEL = "extra_label"

    fun schedule(context: Context, durationMillis: Long, label: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + durationMillis
        val pendingIntent = buildPendingIntent(appContext, label)

        if (canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }

        SettingsRepository(appContext).apply {
            scheduledEndAt = triggerAt
            scheduledLabel = label
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        appContext.getSystemService(AlarmManager::class.java)
            .cancel(buildPendingIntent(appContext, null))
        SettingsRepository(appContext).apply {
            scheduledEndAt = 0L
            scheduledLabel = null
        }
    }

    fun canScheduleExact(alarmManager: AlarmManager?): Boolean {
        if (alarmManager == null) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
    }

    private fun buildPendingIntent(context: Context, label: String?): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_FIRE
            if (label != null) putExtra(EXTRA_LABEL, label)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

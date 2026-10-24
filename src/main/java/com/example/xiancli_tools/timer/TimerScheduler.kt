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

    /**
     * 开始一次计时：装好闹钟，同时记下这次计时，等它结束或取消时结算。
     * 返回闹钟的触发时刻，方便调用方直接拿来倒计时。
     */
    fun schedule(context: Context, durationMillis: Long, label: String): Long {
        val triggerAt = System.currentTimeMillis() + durationMillis
        arm(context, triggerAt, label)
        TimerSessionRecorder.start(context, label, durationMillis, triggerAt)
        return triggerAt
    }

    /**
     * 只装闹钟、不动计时记录。用于开机后把之前那次计时重新挂回去：
     * 这时 [TimerSessionRecorder] 里已经有起点，再 start 一次会把起点冲掉。
     */
    fun restore(context: Context, triggerAt: Long, label: String) {
        arm(context, triggerAt, label)
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        // 先结算再清状态：结算要读 scheduledEndAt 之外的起点信息。
        TimerSessionRecorder.finish(appContext, completed = false)
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

    private fun arm(context: Context, triggerAt: Long, label: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
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

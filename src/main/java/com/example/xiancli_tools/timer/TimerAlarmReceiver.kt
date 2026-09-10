package com.example.xiancli_tools.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.xiancli_tools.data.SettingsRepository

class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val label = intent.getStringExtra(TimerScheduler.EXTRA_LABEL) ?: "定时"

        SettingsRepository(context).apply {
            scheduledEndAt = 0L
            scheduledLabel = null
        }

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(TimerScheduler.EXTRA_LABEL, label)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "无法启动响铃服务", e)
        }
    }

    companion object {
        private const val TAG = "TimerAlarmReceiver"
        const val ACTION_FIRE = "com.example.xiancli_tools.action.TIMER_FIRE"
    }
}

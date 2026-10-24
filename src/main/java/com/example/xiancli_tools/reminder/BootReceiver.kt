package com.example.xiancli_tools.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 开机 / 应用更新后恢复计时与提醒。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        runCatching { ReminderRestorer.restoreAll(context) }
            .onFailure { Log.e(TAG, "恢复闹钟失败", it) }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}

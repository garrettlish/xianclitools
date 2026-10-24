package com.example.xiancli_tools.reminder

import android.content.Context
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.data.SettingsRepository
import com.example.xiancli_tools.timer.TimerScheduler
import com.example.xiancli_tools.timer.TimerSessionRecorder

/**
 * 开机 / 升级后把闹钟重新挂回去。
 *
 * Android 重启会清空 AlarmManager 里的所有闹钟，如果不补，用户会以为
 * 「昨天设的提醒怎么没响」——这是提醒类功能最常被吐槽的坑。
 */
object ReminderRestorer {

    fun restoreAll(context: Context) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        restoreTimer(appContext, now)
        restoreNotes(appContext, now)
    }

    private fun restoreTimer(context: Context, now: Long) {
        val settings = SettingsRepository(context)
        val endAt = settings.scheduledEndAt
        if (endAt <= 0L) return
        val label = settings.scheduledLabel ?: "定时"
        if (endAt > now) {
            TimerScheduler.restore(context, endAt, label)
        } else {
            // 关机期间计时已经走完了：补一条「完成」记录，不再响铃打扰。
            TimerSessionRecorder.finish(context, completed = true)
            settings.scheduledEndAt = 0L
            settings.scheduledLabel = null
        }
    }

    private fun restoreNotes(context: Context, now: Long) {
        NoteRepository(context).loadNotes().forEach { note ->
            val remindAt = note.remindAt
            if (remindAt != null && remindAt > now && !note.done) {
                ReminderScheduler.scheduleNote(context, note.id, remindAt, note.content)
            }
        }
    }
}

package com.example.xiancli_tools.timer

import android.content.Context
import com.example.xiancli_tools.data.SettingsRepository
import com.example.xiancli_tools.data.TimerSession
import com.example.xiancli_tools.data.TimerSessionRepository

/**
 * 计时的「草稿」：开始时先记下起点，等闹钟真的响了、或者用户取消时，
 * 才结算成一条 [TimerSession] 落库。
 *
 * 状态放在 SharedPreferences 里而不是内存里——闹钟到点时进程可能已经被回收，
 * 接收器只能靠持久化状态把这次计时捡回来。
 */
object TimerSessionRecorder {

    fun start(context: Context, label: String, plannedMillis: Long, triggerAt: Long) {
        val settings = SettingsRepository(context.applicationContext)
        // 上一个还没结算就开了新的，先把旧的按「中断」收尾，别留下悬空记录。
        if (settings.activeSessionStart > 0L) {
            finish(context, completed = false)
        }
        settings.activeSessionStart = System.currentTimeMillis()
        settings.activeSessionPlanned = plannedMillis
        settings.activeSessionLabel = label
        settings.scheduledEndAt = triggerAt
        settings.scheduledLabel = label
    }

    /**
     * 结算当前计时并落库。[completed] 为 true 表示走到了闹钟。
     * 没有进行中的计时时什么也不做，返回 null。
     */
    fun finish(context: Context, completed: Boolean): TimerSession? {
        val appContext = context.applicationContext
        val settings = SettingsRepository(appContext)
        val start = settings.activeSessionStart
        if (start <= 0L) return null

        val planned = settings.activeSessionPlanned
        val label = settings.activeSessionLabel ?: "定时"
        // 完成的计时以「计划结束点」为准，避免响铃被拖延后时长越算越长。
        val end = if (completed && planned > 0L) start + planned else System.currentTimeMillis()

        val session = TimerSession(
            id = start,
            label = label,
            plannedMillis = planned,
            startMillis = start,
            endMillis = end,
            completed = completed
        )
        TimerSessionRepository(appContext).addSession(session)

        settings.activeSessionStart = 0L
        settings.activeSessionPlanned = 0L
        settings.activeSessionLabel = null
        return session
    }
}

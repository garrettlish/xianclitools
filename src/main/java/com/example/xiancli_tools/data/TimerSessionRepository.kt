package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class TimerSessionRepository(context: Context) {

    private val appContext = context.applicationContext
    private val sessionsFile = File(appContext.filesDir, SESSIONS_FILE)

    /** 界面用的专注记录：墓碑不参与展示。 */
    @Synchronized
    fun loadSessions(): List<TimerSession> = readRaw()
        .filter { it.deletedAt == null }
        .sortedByDescending { it.startMillis }

    /** 含墓碑的全量，备份与合并必须用它，否则删除传不到别的设备。 */
    @Synchronized
    fun loadAllSessions(): List<TimerSession> = readRaw().sortedByDescending { it.startMillis }

    @Synchronized
    fun addSession(session: TimerSession) {
        val all = readRaw().toMutableList()
        val previous = all.firstOrNull { it.id == session.id }
        all.removeAll { it.id == session.id }
        all.add(session.copy(updatedAt = nextStamp(previous?.updatedAt), deletedAt = null))
        write(all)
    }

    @Synchronized
    fun deleteSession(id: Long) {
        val all = readRaw()
        val stamp = nextStamp(all.firstOrNull { it.id == id }?.updatedAt)
        write(all.map { if (it.id == id) it.copy(deletedAt = stamp, updatedAt = stamp) else it })
    }

    @Synchronized
    fun replaceAll(sessions: List<TimerSession>) {
        write(sessions)
    }

    private fun readRaw(): List<TimerSession> {
        if (!sessionsFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.timerSessionsFromJson(JSONArray(sessionsFile.readText()))
        }.onFailure { Log.e(TAG, "读取专注记录失败", it) }.getOrDefault(emptyList())
    }

    /** 修改时间戳；同一毫秒内改两次时强制比上一版大 1，保证先后可判。 */
    private fun nextStamp(previous: Long?): Long =
        maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1L)

    private fun write(sessions: List<TimerSession>) {
        writeAtomically(
            JsonCodec.timerSessionsToJson(sessions.sortedByDescending { it.startMillis }).toString()
        )
    }

    private fun writeAtomically(content: String) {
        runCatching {
            val temp = File(sessionsFile.parentFile, "${sessionsFile.name}.tmp")
            temp.writeText(content)
            if (sessionsFile.exists()) sessionsFile.delete()
            temp.renameTo(sessionsFile)
        }.onFailure { Log.e(TAG, "写入专注记录失败", it) }
    }

    private companion object {
        const val TAG = "TimerSessionRepository"
        const val SESSIONS_FILE = "timer_sessions.json"
    }
}

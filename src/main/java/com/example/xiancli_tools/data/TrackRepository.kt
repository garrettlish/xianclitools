package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TrackRepository(context: Context) {

    private val appContext = context.applicationContext
    private val sessionsFile = File(appContext.filesDir, SESSIONS_FILE)
    private val activeFile = File(appContext.filesDir, ACTIVE_FILE)

    /** 界面用的轨迹：墓碑不参与展示。 */
    @Synchronized
    fun loadSessions(): List<TrackSession> =
        readSessions(sessionsFile).filter { it.deletedAt == null }

    /** 含墓碑的全量，备份与合并必须用它，否则删除传不到别的设备。 */
    @Synchronized
    fun loadAllSessions(): List<TrackSession> = readSessions(sessionsFile)

    @Synchronized
    fun appendSession(session: TrackSession) {
        val sessions = readSessions(sessionsFile).toMutableList()
        val previous = sessions.firstOrNull { it.id == session.id }
        sessions.removeAll { it.id == session.id }
        sessions.add(session.copy(updatedAt = nextStamp(previous?.updatedAt), deletedAt = null))
        writeSessions(sessionsFile, sessions.sortedByDescending { it.startMillis })
    }

    @Synchronized
    fun deleteSession(id: Long) {
        val sessions = readSessions(sessionsFile)
        val stamp = nextStamp(sessions.firstOrNull { it.id == id }?.updatedAt)
        // 墓碑只留骨架：轨迹点最占空间，删掉就没必要再跟着同步。
        writeSessions(
            sessionsFile,
            sessions.map {
                if (it.id == id) {
                    it.copy(deletedAt = stamp, updatedAt = stamp, points = emptyList())
                } else {
                    it
                }
            }
        )
    }

    /** 用给定列表整体替换本地轨迹（导入备份时使用）。墓碑要一并写入。 */
    @Synchronized
    fun replaceAll(sessions: List<TrackSession>) {
        writeSessions(sessionsFile, sessions.sortedByDescending { it.startMillis })
    }

    @Synchronized
    fun activeSession(): TrackSession? {
        if (!activeFile.exists()) return null
        return runCatching {
            JsonCodec.sessionFromJson(JSONObject(activeFile.readText()))
        }.getOrNull()
    }

    @Synchronized
    fun saveActiveSession(session: TrackSession) {
        writeAtomically(activeFile, JsonCodec.sessionToJson(session).toString())
    }

    @Synchronized
    fun clearActiveSession() {
        runCatching { activeFile.delete() }
    }

    private fun readSessions(file: File): List<TrackSession> {
        if (!file.exists()) return emptyList()
        return runCatching {
            JsonCodec.sessionsFromJson(JSONArray(file.readText()))
        }.onFailure { Log.e(TAG, "读取轨迹失败", it) }.getOrDefault(emptyList())
    }

    /** 修改时间戳；同一毫秒内改两次时强制比上一版大 1，保证先后可判。 */
    private fun nextStamp(previous: Long?): Long =
        maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1L)

    private fun writeSessions(file: File, sessions: List<TrackSession>) {
        writeAtomically(file, JsonCodec.sessionsToJson(sessions).toString())
    }

    private fun writeAtomically(file: File, content: String) {
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(content)
            if (file.exists()) file.delete()
            temp.renameTo(file)
        }.onFailure { Log.e(TAG, "写入轨迹失败", it) }
    }

    private companion object {
        const val TAG = "TrackRepository"
        const val SESSIONS_FILE = "track_sessions.json"
        const val ACTIVE_FILE = "track_active.json"
    }
}

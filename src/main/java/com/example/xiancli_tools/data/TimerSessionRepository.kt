package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class TimerSessionRepository(context: Context) {

    private val appContext = context.applicationContext
    private val sessionsFile = File(appContext.filesDir, SESSIONS_FILE)

    @Synchronized
    fun loadSessions(): List<TimerSession> {
        if (!sessionsFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.timerSessionsFromJson(JSONArray(sessionsFile.readText()))
                .sortedByDescending { it.startMillis }
        }.onFailure { Log.e(TAG, "读取专注记录失败", it) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun addSession(session: TimerSession) {
        val sessions = loadSessions().toMutableList()
        sessions.removeAll { it.id == session.id }
        sessions.add(session)
        write(sessions.sortedByDescending { it.startMillis })
    }

    @Synchronized
    fun deleteSession(id: Long) {
        write(loadSessions().filterNot { it.id == id })
    }

    @Synchronized
    fun replaceAll(sessions: List<TimerSession>) {
        write(sessions.sortedByDescending { it.startMillis })
    }

    private fun write(sessions: List<TimerSession>) {
        runCatching {
            val temp = File(sessionsFile.parentFile, "${sessionsFile.name}.tmp")
            temp.writeText(JsonCodec.timerSessionsToJson(sessions).toString())
            if (sessionsFile.exists()) sessionsFile.delete()
            temp.renameTo(sessionsFile)
        }.onFailure { Log.e(TAG, "写入专注记录失败", it) }
    }

    private companion object {
        const val TAG = "TimerSessionRepository"
        const val SESSIONS_FILE = "timer_sessions.json"
    }
}

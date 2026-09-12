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

    @Synchronized
    fun loadSessions(): List<TrackSession> = readSessions(sessionsFile)

    @Synchronized
    fun appendSession(session: TrackSession) {
        val sessions = readSessions(sessionsFile).toMutableList()
        sessions.removeAll { it.id == session.id }
        sessions.add(session)
        writeSessions(sessionsFile, sessions.sortedByDescending { it.startMillis })
    }

    @Synchronized
    fun deleteSession(id: Long) {
        val sessions = readSessions(sessionsFile).filterNot { it.id == id }
        writeSessions(sessionsFile, sessions)
    }

    @Synchronized
    fun activeSession(): TrackSession? {
        if (!activeFile.exists()) return null
        return runCatching {
            parseSession(JSONObject(activeFile.readText()))
        }.getOrNull()
    }

    @Synchronized
    fun saveActiveSession(session: TrackSession) {
        writeAtomically(activeFile, session.toJson().toString())
    }

    @Synchronized
    fun clearActiveSession() {
        runCatching { activeFile.delete() }
    }

    private fun readSessions(file: File): List<TrackSession> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    add(parseSession(array.getJSONObject(index)))
                }
            }
        }.onFailure { Log.e(TAG, "读取轨迹失败", it) }.getOrDefault(emptyList())
    }

    private fun writeSessions(file: File, sessions: List<TrackSession>) {
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        writeAtomically(file, array.toString())
    }

    private fun writeAtomically(file: File, content: String) {
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(content)
            if (file.exists()) file.delete()
            temp.renameTo(file)
        }.onFailure { Log.e(TAG, "写入轨迹失败", it) }
    }

    private fun TrackSession.toJson(): JSONObject {
        val pointsArray = JSONArray()
        points.forEach { point ->
            pointsArray.put(
                JSONArray()
                    .put(point.latitude)
                    .put(point.longitude)
                    .put(point.timeMillis)
            )
        }
        return JSONObject()
            .put("id", id)
            .put("mode", mode.name)
            .put("start", startMillis)
            .put("end", endMillis)
            .put("distance", distanceMeters)
            .put("points", pointsArray)
    }

    private fun parseSession(json: JSONObject): TrackSession {
        val mode = runCatching {
            TransportMode.valueOf(json.optString("mode", TransportMode.WALK.name))
        }.getOrDefault(TransportMode.WALK)
        val pointsArray = json.optJSONArray("points") ?: JSONArray()
        val points = buildList {
            for (index in 0 until pointsArray.length()) {
                val item = pointsArray.optJSONArray(index) ?: continue
                add(
                    TrackPoint(
                        latitude = item.optDouble(0),
                        longitude = item.optDouble(1),
                        timeMillis = item.optLong(2)
                    )
                )
            }
        }
        return TrackSession(
            id = json.optLong("id"),
            mode = mode,
            startMillis = json.optLong("start"),
            endMillis = json.optLong("end"),
            distanceMeters = json.optDouble("distance"),
            points = points
        )
    }

    private companion object {
        const val TAG = "TrackRepository"
        const val SESSIONS_FILE = "track_sessions.json"
        const val ACTIVE_FILE = "track_active.json"
    }
}

package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.Writer

class TrackRepository(context: Context) {

    private val appContext = context.applicationContext
    private val sessionsFile = File(appContext.filesDir, SESSIONS_FILE)
    private val activeFile = File(appContext.filesDir, ACTIVE_FILE)

    /**
     * 列表 / 明细 / 统计 / 回顾 / 出行都用这一份。
     *
     * 只读骨架、不读点阵：解析时整段跳过 `points`，所以几万条轨迹也只有几百 KB 内存、
     * 秒开。要画地图请走 [loadSessions]，那是唯一真正需要坐标的路径。
     */
    @Synchronized
    fun loadSessionIndex(): List<TrackBrief> =
        readSessions(sessionsFile, skipPoints = true)
            .filter { it.deletedAt == null }
            .map { it.toBrief() }

    /** 含墓碑的全量，备份与合并必须用它，否则删除传不到别的设备。 */
    @Synchronized
    fun loadAllSessions(): List<TrackSession> = readSessions(sessionsFile, skipPoints = false)

    /**
     * 按 id 取指定几条的完整点阵（打开地图时用）。
     *
     * 仍然是从头扫一遍文件，但逐条解析，内存只占一条轨迹；
     * 真要更快就得分文件存点阵（另一件事）。
     */
    @Synchronized
    fun loadSessions(ids: Collection<Long>): List<TrackSession> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.toHashSet()
        return readSessions(sessionsFile, skipPoints = false)
            .filter { it.id in wanted && it.deletedAt == null }
            .sortedByDescending { it.startMillis }
    }

    @Synchronized
    fun appendSession(session: TrackSession) {
        val sessions = readSessions(sessionsFile, skipPoints = false).toMutableList()
        val previous = sessions.firstOrNull { it.id == session.id }
        sessions.removeAll { it.id == session.id }
        sessions.add(session.copy(updatedAt = nextStamp(previous?.updatedAt), deletedAt = null))
        writeSessions(sessionsFile, sessions.sortedByDescending { it.startMillis })
    }

    @Synchronized
    fun deleteSession(id: Long) {
        val sessions = readSessions(sessionsFile, skipPoints = false)
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
        writeAtomically(activeFile) { it.write(JsonCodec.sessionToJson(session).toString()) }
    }

    @Synchronized
    fun clearActiveSession() {
        runCatching { activeFile.delete() }
    }

    /**
     * 流式读取：一条一条解析，内存只占一条轨迹。
     *
     * [skipPoints] 为 true 时原文里的 `points` 整段丢掉（不复制也不解析），
     * 这是列表页能秒开的原因。文件坏了就整份当空处理，不留半截数据。
     */
    private fun readSessions(file: File, skipPoints: Boolean): List<TrackSession> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val sessions = ArrayList<TrackSession>()
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val stream = JsonStream(reader)
                stream.beginArray()
                while (stream.nextElement()) {
                    val raw = stream.readRawValue(if (skipPoints) POINT_FIELDS else emptySet())
                    sessions.add(JsonCodec.sessionFromJson(JSONObject(raw)))
                }
            }
            sessions
        }.onFailure { Log.e(TAG, "读取轨迹失败", it) }.getOrDefault(emptyList())
    }

    /** 修改时间戳；同一毫秒内改两次时强制比上一版大 1，保证先后可判。 */
    private fun nextStamp(previous: Long?): Long =
        maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1L)

    /** 逐条写，避免把几十 MB 的数组先拼成一个 String。 */
    private fun writeSessions(file: File, sessions: List<TrackSession>) {
        writeAtomically(file) { writer ->
            writer.write("[")
            sessions.forEachIndexed { index, session ->
                if (index > 0) writer.write(",")
                writer.write(JsonCodec.sessionToJson(session).toString())
            }
            writer.write("]")
        }
    }

    private fun writeAtomically(file: File, block: (Writer) -> Unit) {
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.bufferedWriter(Charsets.UTF_8).use { block(it) }
            if (file.exists()) file.delete()
            temp.renameTo(file)
        }.onFailure { Log.e(TAG, "写入轨迹失败", it) }
    }

    private companion object {
        const val TAG = "TrackRepository"
        const val SESSIONS_FILE = "track_sessions.json"
        const val ACTIVE_FILE = "track_active.json"

        /** 读摘要时要整段丢掉的字段。 */
        val POINT_FIELDS = setOf(JsonCodec.FIELD_POINTS)
    }
}

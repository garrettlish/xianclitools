package com.example.xiancli_tools.data

import org.json.JSONArray
import org.json.JSONObject

/** 一次备份所包含的全部数据。 */
data class BackupPayload(
    val notes: List<Note> = emptyList(),
    val records: List<ExpenseRecord> = emptyList(),
    val sessions: List<TrackSession> = emptyList(),
    val timers: List<TimerSession> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val trips: List<Trip> = emptyList()
) {
    val totalCount: Int
        get() = notes.size + records.size + sessions.size +
            timers.size + habits.size + trips.size

    /**
     * 与现有数据合并：id 已存在的以现有数据为准，只补充新记录。
     * 同一次导入里 id 重复的记录也只保留第一条。
     */
    fun mergedWith(existing: BackupPayload): BackupPayload = BackupPayload(
        notes = mergeById(existing.notes, notes, Note::id),
        records = mergeById(existing.records, records, ExpenseRecord::id),
        sessions = mergeById(existing.sessions, sessions, TrackSession::id),
        timers = mergeById(existing.timers, timers, TimerSession::id),
        habits = mergeById(existing.habits, habits, Habit::id),
        trips = mergeById(existing.trips, trips, Trip::id)
    )

    /** 相对现有数据会新增的条数，用于提示「新增 N 条，跳过 M 条」。 */
    fun newCountAgainst(existing: BackupPayload): Int =
        newCountById(existing.notes, notes, Note::id) +
            newCountById(existing.records, records, ExpenseRecord::id) +
            newCountById(existing.sessions, sessions, TrackSession::id) +
            newCountById(existing.timers, timers, TimerSession::id) +
            newCountById(existing.habits, habits, Habit::id) +
            newCountById(existing.trips, trips, Trip::id)
}

private fun <T> mergeById(existing: List<T>, incoming: List<T>, id: (T) -> Long): List<T> {
    val seen = existing.mapTo(HashSet()) { id(it) }
    return existing + incoming.filter { seen.add(id(it)) }
}

private fun <T> newCountById(existing: List<T>, incoming: List<T>, id: (T) -> Long): Int {
    val seen = existing.mapTo(HashSet()) { id(it) }
    return incoming.count { seen.add(id(it)) }
}

class BackupFormatException(message: String) : Exception(message)

/**
 * 备份文件的读写。
 *
 * 文件是单个 JSON，顶层带 `schema` 版本号：导入时先校验版本，
 * 避免把别的 JSON 或未来版本的备份直接写进本地数据。
 *
 * schema 演进：
 * - 1：记事 / 账本 / 足迹三类。
 * - 2：记事增加提醒时间、待办完成态、心情等级；新增专注记录、习惯、出行。
 *      读取 1 时缺的字段走默认值、缺的数组视为空，所以旧备份仍然能导。
 */
object BackupCodec {

    const val SCHEMA_VERSION = 2

    /**
     * 备份文件里的来源标识。
     *
     * App 已从「方寸」改名为「朝夕」，但这个值**必须保持 `fangcun`**：
     * 它写进了历史备份文件，改动会让老备份和新备份在格式上分成两套。
     * 界面文案用「朝夕」，格式标识留在原处，两者不必一致。
     */
    private const val APP_TAG = "fangcun"

    fun encode(payload: BackupPayload, exportedAt: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
            .put("app", APP_TAG)
            .put("schema", SCHEMA_VERSION)
            .put("exportedAt", exportedAt)
            .put(
                "counts",
                JSONObject()
                    .put("notes", payload.notes.size)
                    .put("ledger", payload.records.size)
                    .put("tracks", payload.sessions.size)
                    .put("timers", payload.timers.size)
                    .put("habits", payload.habits.size)
                    .put("trips", payload.trips.size)
            )
            .put("notes", JsonCodec.notesToJson(payload.notes))
            .put("ledger", JsonCodec.recordsToJson(payload.records))
            .put("tracks", JsonCodec.sessionsToJson(payload.sessions))
            .put("timers", JsonCodec.timerSessionsToJson(payload.timers))
            .put("habits", JsonCodec.habitsToJson(payload.habits))
            .put("trips", JsonCodec.tripsToJson(payload.trips))
        return root.toString(2)
    }

    fun decode(text: String): BackupPayload {
        val root = runCatching { JSONObject(text) }.getOrElse {
            throw BackupFormatException("不是有效的 JSON 文件")
        }
        val schema = root.optInt("schema", -1)
        if (schema <= 0) throw BackupFormatException("不是「朝夕」的备份文件")
        if (schema > SCHEMA_VERSION) {
            throw BackupFormatException("备份文件来自更新版本的 App（schema=$schema）")
        }
        return BackupPayload(
            notes = JsonCodec.notesFromJson(root.optJSONArray("notes") ?: JSONArray()),
            records = JsonCodec.recordsFromJson(root.optJSONArray("ledger") ?: JSONArray()),
            sessions = JsonCodec.sessionsFromJson(root.optJSONArray("tracks") ?: JSONArray()),
            timers = JsonCodec.timerSessionsFromJson(root.optJSONArray("timers") ?: JSONArray()),
            habits = JsonCodec.habitsFromJson(root.optJSONArray("habits") ?: JSONArray()),
            trips = JsonCodec.tripsFromJson(root.optJSONArray("trips") ?: JSONArray())
        )
    }
}

package com.example.xiancli_tools.data

import org.json.JSONArray
import org.json.JSONObject

/** 一次备份所包含的全部数据。 */
data class BackupPayload(
    val notes: List<Note> = emptyList(),
    val records: List<ExpenseRecord> = emptyList(),
    val sessions: List<TrackSession> = emptyList()
) {
    val totalCount: Int
        get() = notes.size + records.size + sessions.size

    /**
     * 与现有数据合并：id 已存在的以现有数据为准，只补充新记录。
     * 同一次导入里 id 重复的记录也只保留第一条。
     */
    fun mergedWith(existing: BackupPayload): BackupPayload = BackupPayload(
        notes = mergeById(existing.notes, notes, Note::id),
        records = mergeById(existing.records, records, ExpenseRecord::id),
        sessions = mergeById(existing.sessions, sessions, TrackSession::id)
    )

    /** 相对现有数据会新增的条数，用于提示「新增 N 条，跳过 M 条」。 */
    fun newCountAgainst(existing: BackupPayload): Int =
        newCountById(existing.notes, notes, Note::id) +
            newCountById(existing.records, records, ExpenseRecord::id) +
            newCountById(existing.sessions, sessions, TrackSession::id)
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
 */
object BackupCodec {

    const val SCHEMA_VERSION = 1
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
            )
            .put("notes", JsonCodec.notesToJson(payload.notes))
            .put("ledger", JsonCodec.recordsToJson(payload.records))
            .put("tracks", JsonCodec.sessionsToJson(payload.sessions))
        return root.toString(2)
    }

    fun decode(text: String): BackupPayload {
        val root = runCatching { JSONObject(text) }.getOrElse {
            throw BackupFormatException("不是有效的 JSON 文件")
        }
        val schema = root.optInt("schema", -1)
        if (schema <= 0) throw BackupFormatException("不是「方寸」的备份文件")
        if (schema > SCHEMA_VERSION) {
            throw BackupFormatException("备份文件来自更新版本的 App（schema=$schema）")
        }
        return BackupPayload(
            notes = JsonCodec.notesFromJson(root.optJSONArray("notes") ?: JSONArray()),
            records = JsonCodec.recordsFromJson(root.optJSONArray("ledger") ?: JSONArray()),
            sessions = JsonCodec.sessionsFromJson(root.optJSONArray("tracks") ?: JSONArray())
        )
    }
}

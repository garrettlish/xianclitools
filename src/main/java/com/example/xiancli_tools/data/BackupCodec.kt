package com.example.xiancli_tools.data

import org.json.JSONArray
import org.json.JSONObject

/** 一次备份所包含的全部数据。 */
data class BackupPayload(
    val notes: List<Note> = emptyList(),
    val records: List<ExpenseRecord> = emptyList(),
    val sessions: List<TrackSession> = emptyList(),
    val timers: List<TimerSession> = emptyList(),
    val drinkCategories: List<DrinkCategory> = emptyList(),
    val drinkRecords: List<DrinkRecord> = emptyList(),
    val trips: List<Trip> = emptyList()
) {
    /** 含墓碑的总条数：备份文件里实际存了多少条。 */
    val totalCount: Int
        get() = notes.size + records.size + sessions.size + timers.size +
            drinkCategories.size + drinkRecords.size + trips.size

    // 墓碑是同步用的内部状态，界面上不该出现，所以展示一律走 visible* 这组。

    val visibleNotes: Int
        get() = notes.count { it.deletedAt == null }
    val visibleRecords: Int
        get() = records.count { it.deletedAt == null }
    val visibleSessions: Int
        get() = sessions.count { it.deletedAt == null }
    val visibleTimers: Int
        get() = timers.count { it.deletedAt == null }
    val visibleDrinkCategories: Int
        get() = drinkCategories.count { it.deletedAt == null }
    val visibleDrinkRecords: Int
        get() = drinkRecords.count { it.deletedAt == null }
    val visibleTrips: Int
        get() = trips.count { it.deletedAt == null }

    /** 用户看得见的条数。 */
    val visibleCount: Int
        get() = visibleNotes + visibleRecords + visibleSessions + visibleTimers +
            visibleDrinkCategories + visibleDrinkRecords + visibleTrips

    /**
     * 与现有数据合并：同 id 的记录比较 [updatedAt]，谁的更新用谁的。
     *
     * 这个语义比「只补新记录」多覆盖两种情况：
     * - 在另一台设备上改过的内容能覆盖过来，而不是被当成重复丢弃；
     * - 在另一台设备上删掉的记录带着更晚的墓碑，能压过本地还活着的那条。
     *
     * 时钟不同步时 LWW 会偏向「表快了」的那台设备，这是去中心化合并的固有代价；
     * 个人多设备场景里，比「改了什么永远同步不过去」可接受得多。
     */
    fun mergedWith(existing: BackupPayload): BackupPayload = BackupPayload(
        notes = mergeLww(existing.notes, notes, Note::id, Note::updatedAt, Note::deletedAt),
        records = mergeLww(
            existing.records, records, ExpenseRecord::id,
            ExpenseRecord::updatedAt, ExpenseRecord::deletedAt
        ),
        sessions = mergeLww(
            existing.sessions, sessions, TrackSession::id,
            TrackSession::updatedAt, TrackSession::deletedAt
        ),
        timers = mergeLww(
            existing.timers, timers, TimerSession::id,
            TimerSession::updatedAt, TimerSession::deletedAt
        ),
        drinkCategories = mergeLww(
            existing.drinkCategories, drinkCategories, DrinkCategory::id,
            DrinkCategory::updatedAt, DrinkCategory::deletedAt
        ),
        drinkRecords = mergeLww(
            existing.drinkRecords, drinkRecords, DrinkRecord::id,
            DrinkRecord::updatedAt, DrinkRecord::deletedAt
        ),
        trips = mergeLww(existing.trips, trips, Trip::id, Trip::updatedAt, Trip::deletedAt)
    )

    /** 相对现有数据会新增 / 更新 / 删除的条数，用于导入前后的提示。 */
    fun diffAgainst(existing: BackupPayload): MergeDiff {
        val a = diffLww(existing.notes, notes, Note::id, Note::updatedAt, Note::deletedAt)
        val b = diffLww(
            existing.records, records, ExpenseRecord::id,
            ExpenseRecord::updatedAt, ExpenseRecord::deletedAt
        )
        val c = diffLww(
            existing.sessions, sessions, TrackSession::id,
            TrackSession::updatedAt, TrackSession::deletedAt
        )
        val d = diffLww(
            existing.timers, timers, TimerSession::id,
            TimerSession::updatedAt, TimerSession::deletedAt
        )
        val e = diffLww(
            existing.drinkCategories, drinkCategories, DrinkCategory::id,
            DrinkCategory::updatedAt, DrinkCategory::deletedAt
        )
        val f = diffLww(
            existing.drinkRecords, drinkRecords, DrinkRecord::id,
            DrinkRecord::updatedAt, DrinkRecord::deletedAt
        )
        val g = diffLww(existing.trips, trips, Trip::id, Trip::updatedAt, Trip::deletedAt)
        return MergeDiff(
            added = a.added + b.added + c.added + d.added + e.added + f.added + g.added,
            updated = a.updated + b.updated + c.updated + d.updated + e.updated + f.updated + g.updated,
            removed = a.removed + b.removed + c.removed + d.removed + e.removed + f.removed + g.removed
        )
    }
}

/** 一次合并的改动量。 */
data class MergeDiff(val added: Int = 0, val updated: Int = 0, val removed: Int = 0) {
    val total: Int
        get() = added + updated + removed
}

/** 把合并结果说成一句人话，导入、局域网同步、覆盖导入都用它，措辞保持一致。 */
fun MergeDiff.describe(): String {
    if (total == 0) return "与对方内容一致，没有改动"
    val parts = buildList {
        if (added > 0) add("新增 $added 条")
        if (updated > 0) add("更新 $updated 条")
        if (removed > 0) add("删除 $removed 条")
    }
    return parts.joinToString("、")
}

private fun <T> mergeLww(
    existing: List<T>,
    incoming: List<T>,
    id: (T) -> Long,
    updatedAt: (T) -> Long,
    deletedAt: (T) -> Long?
): List<T> {
    val byId = LinkedHashMap<Long, T>()
    existing.forEach { byId[id(it)] = it }
    incoming.forEach { candidate ->
        val current = byId[id(candidate)]
        // 本地没见过 → 收下；对方版本更新 → 覆盖（含「对方删了」这种更晚的墓碑）。
        if (current == null || updatedAt(candidate) > updatedAt(current)) {
            byId[id(candidate)] = candidate
        }
    }
    return byId.values.toList()
}

private fun <T> diffLww(
    existing: List<T>,
    incoming: List<T>,
    id: (T) -> Long,
    updatedAt: (T) -> Long,
    deletedAt: (T) -> Long?
): MergeDiff {
    val byId = existing.associateBy(id)
    var added = 0
    var updated = 0
    var removed = 0
    incoming.forEach { candidate ->
        val current = byId[id(candidate)]
        when {
            current == null -> added++
            updatedAt(candidate) <= updatedAt(current) -> Unit
            deletedAt(candidate) != null -> removed++
            else -> updated++
        }
    }
    return MergeDiff(added, updated, removed)
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
 * - 3：每条记录增加 `updatedAt`（最后修改时间）与 `deletedAt`（删除墓碑），
 *      多设备之间按 updatedAt 取新、删除也能同步过去。读 1/2 时缺这两个字段，
 *      updatedAt 回退到记录自身的时间、deletedAt 视为未删除，所以旧备份仍然能导；
 *      但 schema 3 的文件不能被旧版 App 读取（旧版会明确拒绝，不会写坏数据）。
 * - 4：「习惯」下线，换成「饮记」——`habits` 数组不再写出也不再读取，
 *      改为 `drinkCategories` / `drinkRecords`。读 1–3 时习惯数据会被静默忽略，
 *      其余类型照常导入；schema 4 的文件不能被旧版 App 读取。
 */
object BackupCodec {

    const val SCHEMA_VERSION = 4

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
                    .put("drinkCategories", payload.drinkCategories.size)
                    .put("drinkRecords", payload.drinkRecords.size)
                    .put("trips", payload.trips.size)
            )
            .put("notes", JsonCodec.notesToJson(payload.notes))
            .put("ledger", JsonCodec.recordsToJson(payload.records))
            .put("tracks", JsonCodec.sessionsToJson(payload.sessions))
            .put("timers", JsonCodec.timerSessionsToJson(payload.timers))
            .put("drinkCategories", JsonCodec.drinkCategoriesToJson(payload.drinkCategories))
            .put("drinkRecords", JsonCodec.drinkRecordsToJson(payload.drinkRecords))
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
        // 旧版本里的 "habits" 数组不再读取：习惯工具已下线，读到也无处安放。
        return BackupPayload(
            notes = JsonCodec.notesFromJson(root.optJSONArray("notes") ?: JSONArray()),
            records = JsonCodec.recordsFromJson(root.optJSONArray("ledger") ?: JSONArray()),
            sessions = JsonCodec.sessionsFromJson(root.optJSONArray("tracks") ?: JSONArray()),
            timers = JsonCodec.timerSessionsFromJson(root.optJSONArray("timers") ?: JSONArray()),
            drinkCategories = JsonCodec.drinkCategoriesFromJson(
                root.optJSONArray("drinkCategories") ?: JSONArray()
            ),
            drinkRecords = JsonCodec.drinkRecordsFromJson(
                root.optJSONArray("drinkRecords") ?: JSONArray()
            ),
            trips = JsonCodec.tripsFromJson(root.optJSONArray("trips") ?: JSONArray())
        )
    }
}

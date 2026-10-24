package com.example.xiancli_tools.data

import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer

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
 *
 * 从哪一版起读写是流式的（**格式没变**，只是不再一次性把整份备份读进内存）：
 * - 写：一条一条序列化后直接写进 `Writer`，不再先拼出一个几十 MB 的 String；
 * - 读：用 [JsonStream] 按元素切分，同一时刻只有一条记录在内存里。
 * 3 年的足迹备份大约 20MB，整份读进内存时峰值要 60MB 以上，中低端机导入会 OOM。
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

    /** 整份备份的文本（局域网直传要算 Content-Length，必须拿到完整字节）。 */
    fun encode(payload: BackupPayload, exportedAt: Long = System.currentTimeMillis()): String {
        val writer = StringWriter()
        encodeTo(payload, writer, exportedAt)
        return writer.toString()
    }

    /**
     * 逐条写出。全程只持有一条记录的 JSON 文本，不再把整份备份拼进一个 String。
     *
     * 输出是紧凑 JSON（没有缩进）：缩进会让体积涨到 2.4 倍，而备份是给机器读的，
     * 要看内容用任何 JSON 工具格式化一下即可。
     */
    fun encodeTo(
        payload: BackupPayload,
        out: Writer,
        exportedAt: Long = System.currentTimeMillis()
    ) {
        out.write("{\"app\":\"")
        out.write(APP_TAG)
        out.write("\",\"schema\":")
        out.write(SCHEMA_VERSION.toString())
        out.write(",\"exportedAt\":")
        out.write(exportedAt.toString())
        out.write(",\"counts\":{\"notes\":")
        out.write(payload.notes.size.toString())
        out.write(",\"ledger\":")
        out.write(payload.records.size.toString())
        out.write(",\"tracks\":")
        out.write(payload.sessions.size.toString())
        out.write(",\"timers\":")
        out.write(payload.timers.size.toString())
        out.write(",\"drinkCategories\":")
        out.write(payload.drinkCategories.size.toString())
        out.write(",\"drinkRecords\":")
        out.write(payload.drinkRecords.size.toString())
        out.write(",\"trips\":")
        out.write(payload.trips.size.toString())
        out.write("}")
        writeArray(out, "notes", payload.notes) { JsonCodec.noteToJson(it) }
        writeArray(out, "ledger", payload.records) { JsonCodec.recordToJson(it) }
        writeArray(out, "tracks", payload.sessions) { JsonCodec.sessionToJson(it) }
        writeArray(out, "timers", payload.timers) { JsonCodec.timerSessionToJson(it) }
        writeArray(out, "drinkCategories", payload.drinkCategories) {
            JsonCodec.drinkCategoryToJson(it)
        }
        writeArray(out, "drinkRecords", payload.drinkRecords) { JsonCodec.drinkRecordToJson(it) }
        writeArray(out, "trips", payload.trips) { JsonCodec.tripToJson(it) }
        out.write("}")
    }

    private inline fun <T> writeArray(out: Writer, name: String, items: List<T>, toJson: (T) -> JSONObject) {
        out.write(",\"")
        out.write(name)
        out.write("\":[")
        items.forEachIndexed { index, item ->
            if (index > 0) out.write(",")
            out.write(toJson(item).toString())
        }
        out.write("]")
    }

    fun decode(text: String): BackupPayload = decodeFrom(StringReader(text))

    fun decodeFrom(input: InputStream): BackupPayload =
        decodeFrom(InputStreamReader(input, Charsets.UTF_8))

    /**
     * 流式读取：每读到一个元素就解析成对象，解析完立刻丢掉原文。
     *
     * 字段顺序无所谓，认不出的键（比如 schema 3 里的 `habits`）整段跳过，
     * 所以旧备份、被手工改过的备份都还能导。
     */
    fun decodeFrom(reader: Reader): BackupPayload {
        val stream = JsonStream(reader)
        try {
            stream.beginObject()
        } catch (e: JsonStreamException) {
            throw BackupFormatException("不是有效的 JSON 文件")
        }

        var schema = -1L
        var notes = emptyList<Note>()
        var records = emptyList<ExpenseRecord>()
        var sessions = emptyList<TrackSession>()
        var timers = emptyList<TimerSession>()
        var drinkCategories = emptyList<DrinkCategory>()
        var drinkRecords = emptyList<DrinkRecord>()
        var trips = emptyList<Trip>()
        try {
            while (true) {
                when (val key = stream.nextMember() ?: break) {
                    "schema" -> schema = stream.readLong()
                    "notes" -> notes = stream.readElements { JsonCodec.noteFromJson(it) }
                    "ledger" -> records = stream.readElements { JsonCodec.recordFromJson(it) }
                    "tracks" -> sessions = stream.readElements { JsonCodec.sessionFromJson(it) }
                    "timers" -> timers = stream.readElements { JsonCodec.timerSessionFromJson(it) }
                    "drinkCategories" -> drinkCategories =
                        stream.readElements { JsonCodec.drinkCategoryFromJson(it) }

                    "drinkRecords" -> drinkRecords =
                        stream.readElements { JsonCodec.drinkRecordFromJson(it) }

                    "trips" -> trips = stream.readElements { JsonCodec.tripFromJson(it) }
                    // app / exportedAt / counts 只是给人看的，认一下或者直接跳。
                    "app" -> stream.readString()
                    "exportedAt" -> stream.readLong()
                    else -> stream.skipValue()
                }
            }
        } catch (e: JsonStreamException) {
            throw BackupFormatException(describeDamage(e))
        } catch (e: JSONException) {
            throw BackupFormatException("备份里有字段格式不对：${e.message ?: "无法解析"}")
        }

        if (schema <= 0) throw BackupFormatException("不是「朝夕」的备份文件")
        if (schema > SCHEMA_VERSION) {
            throw BackupFormatException("备份文件来自更新版本的 App（schema=$schema）")
        }
        return BackupPayload(
            notes = notes,
            records = records,
            sessions = sessions,
            timers = timers,
            drinkCategories = drinkCategories,
            drinkRecords = drinkRecords,
            trips = trips
        )
    }

    private fun describeDamage(error: JsonStreamException): String =
        "备份文件不完整或已损坏：${error.message ?: "无法解析"}"
}

/** 读一个数组，逐个元素交给 [parse]；调用时当前位置必须是 `[`。 */
private inline fun <T> JsonStream.readElements(parse: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>()
    beginArray()
    while (nextElement()) {
        val raw = readRawValue()
        out.add(parse(JSONObject(raw)))
    }
    return out
}

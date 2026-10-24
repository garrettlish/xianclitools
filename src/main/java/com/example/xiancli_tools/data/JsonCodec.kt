package com.example.xiancli_tools.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 记事 / 记账 / 轨迹 / 专注 / 饮记 / 出行的统一 JSON 编解码。
 *
 * 本地存储文件与导出的备份文件共用同一套字段，避免两处格式各自演化：
 * 字段只在这里改一次，本地读写与导入导出同时生效。
 */
object JsonCodec {

    /** 足迹点阵的字段名。读摘要时要整段跳过它，所以单独提出来免得两处各写一遍。 */
    internal const val FIELD_POINTS = "points"

    /**
     * 坐标只保留 6 位小数。
     *
     * 6 位约 0.11 米，比 GPS 本身 3–10 米的精度细得多；但从 18 位数字缩到 9 位，
     * 单点省下约 20% 体积——点阵是备份体积的唯一大头，这一步比任何压缩都便宜。
     */
    private fun roundCoordinate(value: Double): Double =
        Math.round(value * COORDINATE_SCALE) / COORDINATE_SCALE

    private const val COORDINATE_SCALE = 1_000_000.0

    fun noteToJson(note: Note): JSONObject = JSONObject()
        .put("id", note.id)
        .put("category", note.category.name)
        .put("content", note.content)
        .put("time", note.timeMillis)
        .put("remindAt", note.remindAt ?: 0L)
        .put("done", note.done)
        .put("mood", note.moodLevel ?: 0)
        .put("updatedAt", note.updatedAt)
        .put("deletedAt", note.deletedAt ?: 0L)

    /**
     * 旧版类别名映射：提醒并入待办，灵感、其他（以及认不出的脏值）并入日志。
     * 本地旧文件和旧备份都不用迁移——读到哪条映射哪条，保存时自然写成新名。
     */
    private fun noteCategoryFrom(raw: String): NoteCategory = when (raw) {
        NoteCategory.TODO.name -> NoteCategory.TODO
        NoteCategory.DIARY.name -> NoteCategory.DIARY
        NoteCategory.MOOD.name -> NoteCategory.MOOD
        "REMINDER" -> NoteCategory.TODO
        else -> NoteCategory.DIARY
    }

    fun noteFromJson(json: JSONObject): Note {
        val category = noteCategoryFrom(json.optString("category", NoteCategory.DIARY.name))
        return Note(
            id = json.optLong("id"),
            category = category,
            content = json.optString("content"),
            timeMillis = json.optLong("time"),
            // schema 1 的备份文件没有这三个字段，opt 系列会给出默认值，正好兼容。
            remindAt = json.optLong("remindAt", 0L).takeIf { it > 0L },
            done = json.optBoolean("done", false),
            moodLevel = MoodLevels.sanitize(json.optInt("mood", 0)),
            // schema 3 之前没有这两个字段：修改时间回退到记录时间，删除态视为未删除。
            updatedAt = json.optLong("updatedAt", 0L).takeIf { it > 0L } ?: json.optLong("time"),
            deletedAt = json.optLong("deletedAt", 0L).takeIf { it > 0L }
        )
    }

    fun recordToJson(record: ExpenseRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("category", record.category.name)
        .put("amount", record.amountCents)
        .put("note", record.note)
        .put("time", record.timeMillis)
        .put("updatedAt", record.updatedAt)
        .put("deletedAt", record.deletedAt ?: 0L)

    fun recordFromJson(json: JSONObject): ExpenseRecord {
        val category = runCatching {
            ExpenseCategory.valueOf(json.optString("category", ExpenseCategory.OTHER.name))
        }.getOrDefault(ExpenseCategory.OTHER)
        return ExpenseRecord(
            id = json.optLong("id"),
            category = category,
            amountCents = json.optLong("amount"),
            note = json.optString("note"),
            timeMillis = json.optLong("time"),
            updatedAt = json.optLong("updatedAt", 0L).takeIf { it > 0L } ?: json.optLong("time"),
            deletedAt = json.optLong("deletedAt", 0L).takeIf { it > 0L }
        )
    }

    fun sessionToJson(session: TrackSession): JSONObject {
        val points = JSONArray()
        session.points.forEach { point ->
            points.put(
                JSONArray()
                    .put(roundCoordinate(point.latitude))
                    .put(roundCoordinate(point.longitude))
                    .put(point.timeMillis)
            )
        }
        return JSONObject()
            .put("id", session.id)
            .put("mode", session.mode.name)
            .put("start", session.startMillis)
            .put("end", session.endMillis)
            .put("distance", session.distanceMeters)
            .put(FIELD_POINTS, points)
            .put("updatedAt", session.updatedAt)
            .put("deletedAt", session.deletedAt ?: 0L)
    }

    fun sessionFromJson(json: JSONObject): TrackSession {
        val mode = runCatching {
            TransportMode.valueOf(json.optString("mode", TransportMode.WALK.name))
        }.getOrDefault(TransportMode.WALK)
        val pointsArray = json.optJSONArray(FIELD_POINTS) ?: JSONArray()
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
            points = points,
            updatedAt = json.optLong("updatedAt", 0L).takeIf { it > 0L } ?: json.optLong("end"),
            deletedAt = json.optLong("deletedAt", 0L).takeIf { it > 0L }
        )
    }

    fun notesToJson(notes: List<Note>): JSONArray {        val array = JSONArray()
        notes.forEach { array.put(noteToJson(it)) }
        return array
    }

    fun notesFromJson(array: JSONArray): List<Note> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(noteFromJson(it)) }
        }
    }

    fun recordsToJson(records: List<ExpenseRecord>): JSONArray {
        val array = JSONArray()
        records.forEach { array.put(recordToJson(it)) }
        return array
    }

    fun recordsFromJson(array: JSONArray): List<ExpenseRecord> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(recordFromJson(it)) }
        }
    }

    // ---- 专注记录 ----

    fun timerSessionToJson(session: TimerSession): JSONObject = JSONObject()
        .put("id", session.id)
        .put("label", session.label)
        .put("planned", session.plannedMillis)
        .put("start", session.startMillis)
        .put("end", session.endMillis)
        .put("completed", session.completed)
        .put("updatedAt", session.updatedAt)
        .put("deletedAt", session.deletedAt ?: 0L)

    fun timerSessionFromJson(json: JSONObject): TimerSession = TimerSession(
        id = json.optLong("id"),
        label = json.optString("label", "定时"),
        plannedMillis = json.optLong("planned"),
        startMillis = json.optLong("start"),
        endMillis = json.optLong("end"),
        completed = json.optBoolean("completed", false),
        updatedAt = json.optLong("updatedAt", 0L).takeIf { it > 0L } ?: json.optLong("end"),
        deletedAt = json.optLong("deletedAt", 0L).takeIf { it > 0L }
    )

    fun timerSessionsToJson(sessions: List<TimerSession>): JSONArray {
        val array = JSONArray()
        sessions.forEach { array.put(timerSessionToJson(it)) }
        return array
    }

    fun timerSessionsFromJson(array: JSONArray): List<TimerSession> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(timerSessionFromJson(it)) }
        }
    }

    // ---- 饮记 ----

    fun drinkCategoryToJson(category: DrinkCategory): JSONObject = JSONObject()
        .put("id", category.id)
        .put("name", category.name)
        .put("color", category.colorHex)
        .put("created", category.createdMillis)
        .put("updatedAt", category.updatedAt)
        .put("deletedAt", category.deletedAt ?: 0L)

    fun drinkCategoryFromJson(json: JSONObject): DrinkCategory =
        DrinkCategory(
            id = json.optLong("id"),
            name = json.optString("name"),
            colorHex = sanitizeColorHex(json.optString("color")),
            createdMillis = json.optLong("created"),
            updatedAt = json.optLong("updatedAt", 0L).takeIf { it > 0L }
                ?: json.optLong("created"),
            deletedAt = json.optLong("deletedAt", 0L).takeIf { it > 0L }
        )

    fun drinkCategoriesToJson(categories: List<DrinkCategory>): JSONArray {
        val array = JSONArray()
        categories.forEach { array.put(drinkCategoryToJson(it)) }
        return array
    }

    fun drinkCategoriesFromJson(array: JSONArray): List<DrinkCategory> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(drinkCategoryFromJson(it)) }
        }
    }

    fun drinkRecordToJson(record: DrinkRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("categoryId", record.categoryId)
        .put("time", record.timeMillis)
        .put("updatedAt", record.updatedAt)
        .put("deletedAt", record.deletedAt ?: 0L)

    fun drinkRecordFromJson(json: JSONObject): DrinkRecord =
        DrinkRecord(
            id = json.optLong("id"),
            categoryId = json.optLong("categoryId"),
            timeMillis = json.optLong("time"),
            updatedAt = json.optLong("updatedAt", 0L).takeIf { it > 0L } ?: json.optLong("time"),
            deletedAt = json.optLong("deletedAt", 0L).takeIf { it > 0L }
        )

    fun drinkRecordsToJson(records: List<DrinkRecord>): JSONArray {
        val array = JSONArray()
        records.forEach { array.put(drinkRecordToJson(it)) }
        return array
    }

    fun drinkRecordsFromJson(array: JSONArray): List<DrinkRecord> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(drinkRecordFromJson(it)) }
        }
    }

    /** 颜色只能是 #RRGGBB，坏值回退成暖灰，别让一条脏数据把整页刷成黑块。 */
    private fun sanitizeColorHex(raw: String): String =
        if (COLOR_PATTERN.matches(raw)) raw else FALLBACK_COLOR

    private val COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")
    private const val FALLBACK_COLOR = "#9C948A"

    // ---- 出行 ----

    fun tripToJson(trip: Trip): JSONObject = JSONObject()
        .put("id", trip.id)
        .put("name", trip.name)
        .put("start", trip.startMillis)
        .put("end", trip.endMillis)
        .put("note", trip.note)
        .put("updatedAt", trip.updatedAt)
        .put("deletedAt", trip.deletedAt ?: 0L)

    fun tripFromJson(json: JSONObject): Trip = Trip(
        id = json.optLong("id"),
        name = json.optString("name"),
        startMillis = json.optLong("start"),
        endMillis = json.optLong("end"),
        note = json.optString("note"),
        updatedAt = json.optLong("updatedAt", 0L).takeIf { it > 0L } ?: json.optLong("end"),
        deletedAt = json.optLong("deletedAt", 0L).takeIf { it > 0L }
    )

    fun tripsToJson(trips: List<Trip>): JSONArray {
        val array = JSONArray()
        trips.forEach { array.put(tripToJson(it)) }
        return array
    }

    fun tripsFromJson(array: JSONArray): List<Trip> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(tripFromJson(it)) }
        }
    }
}

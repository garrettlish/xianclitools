package com.example.xiancli_tools.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 记事 / 记账 / 轨迹的统一 JSON 编解码。
 *
 * 本地存储文件与导出的备份文件共用同一套字段，避免两处格式各自演化：
 * 字段只在这里改一次，本地读写与导入导出同时生效。
 */
object JsonCodec {

    fun noteToJson(note: Note): JSONObject = JSONObject()
        .put("id", note.id)
        .put("category", note.category.name)
        .put("content", note.content)
        .put("time", note.timeMillis)

    fun noteFromJson(json: JSONObject): Note {
        val category = runCatching {
            NoteCategory.valueOf(json.optString("category", NoteCategory.OTHER.name))
        }.getOrDefault(NoteCategory.OTHER)
        return Note(
            id = json.optLong("id"),
            category = category,
            content = json.optString("content"),
            timeMillis = json.optLong("time")
        )
    }

    fun recordToJson(record: ExpenseRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("category", record.category.name)
        .put("amount", record.amountCents)
        .put("note", record.note)
        .put("time", record.timeMillis)

    fun recordFromJson(json: JSONObject): ExpenseRecord {
        val category = runCatching {
            ExpenseCategory.valueOf(json.optString("category", ExpenseCategory.OTHER.name))
        }.getOrDefault(ExpenseCategory.OTHER)
        return ExpenseRecord(
            id = json.optLong("id"),
            category = category,
            amountCents = json.optLong("amount"),
            note = json.optString("note"),
            timeMillis = json.optLong("time")
        )
    }

    fun sessionToJson(session: TrackSession): JSONObject {
        val points = JSONArray()
        session.points.forEach { point ->
            points.put(
                JSONArray()
                    .put(point.latitude)
                    .put(point.longitude)
                    .put(point.timeMillis)
            )
        }
        return JSONObject()
            .put("id", session.id)
            .put("mode", session.mode.name)
            .put("start", session.startMillis)
            .put("end", session.endMillis)
            .put("distance", session.distanceMeters)
            .put("points", points)
    }

    fun sessionFromJson(json: JSONObject): TrackSession {
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

    fun notesToJson(notes: List<Note>): JSONArray {
        val array = JSONArray()
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

    fun sessionsToJson(sessions: List<TrackSession>): JSONArray {
        val array = JSONArray()
        sessions.forEach { array.put(sessionToJson(it)) }
        return array
    }

    fun sessionsFromJson(array: JSONArray): List<TrackSession> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(sessionFromJson(it)) }
        }
    }
}

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
        .put("remindAt", note.remindAt ?: 0L)
        .put("done", note.done)
        .put("mood", note.moodLevel ?: 0)

    fun noteFromJson(json: JSONObject): Note {
        val category = runCatching {
            NoteCategory.valueOf(json.optString("category", NoteCategory.OTHER.name))
        }.getOrDefault(NoteCategory.OTHER)
        return Note(
            id = json.optLong("id"),
            category = category,
            content = json.optString("content"),
            timeMillis = json.optLong("time"),
            // schema 1 的备份文件没有这三个字段，opt 系列会给出默认值，正好兼容。
            remindAt = json.optLong("remindAt", 0L).takeIf { it > 0L },
            done = json.optBoolean("done", false),
            moodLevel = MoodLevels.sanitize(json.optInt("mood", 0))
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

    // ---- 专注记录 ----

    fun timerSessionToJson(session: TimerSession): JSONObject = JSONObject()
        .put("id", session.id)
        .put("label", session.label)
        .put("planned", session.plannedMillis)
        .put("start", session.startMillis)
        .put("end", session.endMillis)
        .put("completed", session.completed)

    fun timerSessionFromJson(json: JSONObject): TimerSession = TimerSession(
        id = json.optLong("id"),
        label = json.optString("label", "定时"),
        plannedMillis = json.optLong("planned"),
        startMillis = json.optLong("start"),
        endMillis = json.optLong("end"),
        completed = json.optBoolean("completed", false)
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

    // ---- 习惯 ----

    fun habitToJson(habit: Habit): JSONObject {
        val days = JSONArray()
        habit.checkInDays.forEach { days.put(it) }
        return JSONObject()
            .put("id", habit.id)
            .put("name", habit.name)
            .put("icon", habit.iconKey)
            .put("created", habit.createdMillis)
            .put("days", days)
            .put("reminder", habit.reminderMinuteOfDay ?: -1)
            .put("archived", habit.archived)
    }

    fun habitFromJson(json: JSONObject): Habit {
        val daysArray = json.optJSONArray("days") ?: JSONArray()
        val days = buildList {
            for (index in 0 until daysArray.length()) {
                val value = daysArray.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
        return Habit(
            id = json.optLong("id"),
            name = json.optString("name"),
            iconKey = json.optString("icon", "custom"),
            createdMillis = json.optLong("created"),
            checkInDays = days.distinct().sortedDescending(),
            reminderMinuteOfDay = json.optInt("reminder", -1).takeIf { it in 0..1439 },
            archived = json.optBoolean("archived", false)
        )
    }

    fun habitsToJson(habits: List<Habit>): JSONArray {
        val array = JSONArray()
        habits.forEach { array.put(habitToJson(it)) }
        return array
    }

    fun habitsFromJson(array: JSONArray): List<Habit> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(habitFromJson(it)) }
        }
    }

    // ---- 出行 ----

    fun tripToJson(trip: Trip): JSONObject = JSONObject()
        .put("id", trip.id)
        .put("name", trip.name)
        .put("start", trip.startMillis)
        .put("end", trip.endMillis)
        .put("note", trip.note)

    fun tripFromJson(json: JSONObject): Trip = Trip(
        id = json.optLong("id"),
        name = json.optString("name"),
        startMillis = json.optLong("start"),
        endMillis = json.optLong("end"),
        note = json.optString("note")
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

package com.example.xiancli_tools

import com.example.xiancli_tools.data.BackupCodec
import com.example.xiancli_tools.data.BackupFormatException
import com.example.xiancli_tools.data.BackupPayload
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.Habit
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.TimerSession
import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.data.TransportMode
import com.example.xiancli_tools.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun samplePayload() = BackupPayload(
        notes = listOf(
            Note(
                id = 1000L,
                category = NoteCategory.TODO,
                content = "记一条灵感",
                timeMillis = 1_700_000_000_000L,
                remindAt = 1_700_000_500_000L,
                done = true,
                moodLevel = 4
            )
        ),
        records = listOf(
            ExpenseRecord(
                id = 2000L,
                category = ExpenseCategory.FOOD,
                amountCents = 3550L,
                note = "午饭",
                timeMillis = 1_700_000_100_000L
            )
        ),
        sessions = listOf(
            TrackSession(
                id = 3000L,
                mode = TransportMode.BIKE,
                startMillis = 1_700_000_200_000L,
                endMillis = 1_700_000_800_000L,
                distanceMeters = 1234.5,
                points = listOf(
                    TrackPoint(31.23, 121.47, 1_700_000_200_000L),
                    TrackPoint(31.24, 121.48, 1_700_000_800_000L)
                )
            )
        ),
        timers = listOf(
            TimerSession(
                id = 4000L,
                label = "25 分钟",
                plannedMillis = 1_500_000L,
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_001_500_000L,
                completed = true
            )
        ),
        habits = listOf(
            Habit(
                id = 5000L,
                name = "喝水",
                iconKey = "water",
                createdMillis = 1_700_000_000_000L,
                // 打卡日期按倒序存（解码时也会归一成倒序），这里用规范顺序以便往返比较。
                checkInDays = listOf("20231115", "20231114"),
                reminderMinuteOfDay = 8 * 60
            )
        ),
        trips = listOf(
            Trip(
                id = 6000L,
                name = "西安三天",
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_200_000_000L,
                note = "回民街"
            )
        )
    )

    @Test
    fun `导出再导入后三类数据保持不变`() {
        val original = samplePayload()

        val restored = BackupCodec.decode(BackupCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun `合并导入只补充 id 不存在的新记录`() {
        val existing = BackupPayload(notes = samplePayload().notes)
        val incoming = BackupPayload(
            notes = listOf(
                Note(id = 1000L, category = NoteCategory.OTHER, content = "同 id 的旧内容", timeMillis = 1L),
                Note(id = 1001L, category = NoteCategory.TODO, content = "新记录", timeMillis = 2L)
            )
        )

        val merged = incoming.mergedWith(existing)

        assertEquals(2, merged.notes.size)
        assertEquals("同 id 的记录以现有数据为准，避免导入把本地内容覆盖掉", "记一条灵感", merged.notes.first { it.id == 1000L }.content)
        assertEquals(1, incoming.newCountAgainst(existing))
    }

    @Test
    fun `同一次导入里 id 重复的记录只保留第一条`() {
        val incoming = BackupPayload(
            notes = listOf(
                Note(id = 7L, category = NoteCategory.DIARY, content = "第一条", timeMillis = 1L),
                Note(id = 7L, category = NoteCategory.DIARY, content = "第二条", timeMillis = 2L)
            )
        )

        val merged = incoming.mergedWith(BackupPayload())

        assertEquals(1, merged.notes.size)
        assertEquals("第一条", merged.notes.single().content)
    }

    @Test
    fun `不是备份文件的 JSON 会被拒绝而不是静默清空数据`() {
        val error = runCatching { BackupCodec.decode("""{"hello":"world"}""") }.exceptionOrNull()

        assertTrue(error is BackupFormatException)
    }

    @Test
    fun `更高版本的备份文件会被拒绝`() {
        val future = org.json.JSONObject(BackupCodec.encode(samplePayload()))
            .put("schema", BackupCodec.SCHEMA_VERSION + 1)
            .toString()

        val error = runCatching { BackupCodec.decode(future) }.exceptionOrNull()

        assertTrue(error is BackupFormatException)
    }

    @Test
    fun `schema 1 的旧备份仍能导入，新字段取默认值`() {
        val v1 = """
            {
              "app": "fangcun",
              "schema": 1,
              "exportedAt": 1,
              "notes": [{ "id": 1, "category": "TODO", "content": "旧待办", "time": 1000 }],
              "ledger": [],
              "tracks": []
            }
        """.trimIndent()

        val payload = BackupCodec.decode(v1)

        assertEquals(1, payload.notes.size)
        assertNull("旧文件没有提醒字段，应当视为不提醒", payload.notes.first().remindAt)
        assertFalse(payload.notes.first().done)
        assertNull(payload.notes.first().moodLevel)
        assertTrue(payload.timers.isEmpty())
        assertTrue(payload.habits.isEmpty())
        assertTrue(payload.trips.isEmpty())
    }
}

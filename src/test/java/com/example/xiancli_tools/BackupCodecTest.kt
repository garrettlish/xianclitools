package com.example.xiancli_tools

import com.example.xiancli_tools.data.BackupCodec
import com.example.xiancli_tools.data.BackupFormatException
import com.example.xiancli_tools.data.BackupPayload
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.data.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun samplePayload() = BackupPayload(
        notes = listOf(
            Note(id = 1000L, category = NoteCategory.IDEA, content = "记一条灵感", timeMillis = 1_700_000_000_000L)
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
}

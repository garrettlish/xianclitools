package com.example.xiancli_tools

import com.example.xiancli_tools.data.BackupCodec
import com.example.xiancli_tools.data.BackupContainer
import com.example.xiancli_tools.data.BackupFormatException
import com.example.xiancli_tools.data.BackupPayload
import com.example.xiancli_tools.data.DrinkCategory
import com.example.xiancli_tools.data.DrinkDefaults
import com.example.xiancli_tools.data.DrinkRecord
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.JsonCodec
import com.example.xiancli_tools.data.JsonStream
import com.example.xiancli_tools.data.MergeDiff
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.TimerSession
import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.data.TransportMode
import com.example.xiancli_tools.data.Trip
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader

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
        drinkCategories = listOf(
            DrinkCategory(
                id = 5000L,
                name = "水",
                colorHex = "#2F86D6",
                createdMillis = 1_700_000_000_000L
            )
        ),
        drinkRecords = listOf(
            DrinkRecord(
                id = 5100L,
                categoryId = 5000L,
                timeMillis = 1_700_000_300_000L
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
    fun `合并时对方更旧的版本不会覆盖本地`() {
        val existing = BackupPayload(notes = samplePayload().notes)
        val incoming = BackupPayload(
            notes = listOf(
                Note(
                    id = 1000L,
                    category = NoteCategory.OTHER,
                    content = "同 id 的旧内容",
                    timeMillis = 1L,
                    updatedAt = 1L
                ),
                Note(
                    id = 1001L,
                    category = NoteCategory.TODO,
                    content = "新记录",
                    timeMillis = 2L,
                    updatedAt = 2L
                )
            )
        )

        val merged = incoming.mergedWith(existing)

        assertEquals(2, merged.notes.size)
        assertEquals(
            "本地那条更新时间更晚，导入不能把它覆盖掉",
            "记一条灵感",
            merged.notes.first { it.id == 1000L }.content
        )
        assertEquals(MergeDiff(added = 1), incoming.diffAgainst(existing))
    }

    @Test
    fun `合并时对方更新的版本会覆盖本地`() {
        val existing = BackupPayload(
            notes = listOf(
                Note(
                    id = 1L,
                    category = NoteCategory.TODO,
                    content = "旧内容",
                    timeMillis = 10L,
                    updatedAt = 10L
                )
            )
        )
        val incoming = BackupPayload(
            notes = listOf(
                Note(
                    id = 1L,
                    category = NoteCategory.TODO,
                    content = "另一台设备上改过的内容",
                    timeMillis = 10L,
                    updatedAt = 20L
                )
            )
        )

        val merged = incoming.mergedWith(existing)

        assertEquals("另一台设备上改过的内容", merged.notes.single().content)
        assertEquals(MergeDiff(updated = 1), incoming.diffAgainst(existing))
    }

    @Test
    fun `删除墓碑能压过本地还活着的那条记录`() {
        val existing = BackupPayload(
            notes = listOf(
                Note(
                    id = 1L,
                    category = NoteCategory.TODO,
                    content = "本地还在",
                    timeMillis = 10L,
                    updatedAt = 10L
                )
            )
        )
        val incoming = BackupPayload(
            notes = listOf(
                Note(
                    id = 1L,
                    category = NoteCategory.TODO,
                    content = "本地还在",
                    timeMillis = 10L,
                    updatedAt = 20L,
                    deletedAt = 20L
                )
            )
        )

        val merged = incoming.mergedWith(existing)

        assertEquals(1, merged.notes.size)
        assertNotNull("墓碑要保留下来，否则下次导出删除又会丢失", merged.notes.single().deletedAt)
        assertEquals("墓碑不算可见数据", 0, merged.visibleCount)
        assertEquals(MergeDiff(removed = 1), incoming.diffAgainst(existing))
    }

    @Test
    fun `本地已删的记录不会被旧备份复活`() {
        val existing = BackupPayload(
            notes = listOf(
                Note(
                    id = 1L,
                    category = NoteCategory.TODO,
                    content = "已删",
                    timeMillis = 10L,
                    updatedAt = 30L,
                    deletedAt = 30L
                )
            )
        )
        val incoming = BackupPayload(
            notes = listOf(
                Note(
                    id = 1L,
                    category = NoteCategory.TODO,
                    content = "已删",
                    timeMillis = 10L,
                    updatedAt = 10L
                )
            )
        )

        val merged = incoming.mergedWith(existing)

        assertNotNull("旧备份的活记录比本地墓碑旧，不该把它复活", merged.notes.single().deletedAt)
    }

    @Test
    fun `同一次导入里同 id 的记录取更新的那条`() {
        val incoming = BackupPayload(
            notes = listOf(
                Note(
                    id = 7L,
                    category = NoteCategory.DIARY,
                    content = "第一条",
                    timeMillis = 1L,
                    updatedAt = 1L
                ),
                Note(
                    id = 7L,
                    category = NoteCategory.DIARY,
                    content = "第二条",
                    timeMillis = 2L,
                    updatedAt = 2L
                )
            )
        )

        val merged = incoming.mergedWith(BackupPayload())

        assertEquals(1, merged.notes.size)
        assertEquals("第二条", merged.notes.single().content)
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
        assertTrue(payload.drinkCategories.isEmpty())
        assertTrue(payload.drinkRecords.isEmpty())
        assertTrue(payload.trips.isEmpty())
    }

    @Test
    fun `schema 2 的旧备份把新版字段回退成安全默认值`() {
        val v2 = """
            {
              "app": "fangcun",
              "schema": 2,
              "exportedAt": 1,
              "notes": [{ "id": 1, "category": "TODO", "content": "旧待办", "time": 1000 }],
              "ledger": [{ "id": 2, "category": "FOOD", "amount": 100, "note": "", "time": 2000 }],
              "tracks": [],
              "timers": [],
              "habits": [],
              "trips": []
            }
        """.trimIndent()

        val payload = BackupCodec.decode(v2)

        // 没有 updatedAt 时回退到记录自身时间，这样旧数据不会「显得比谁都新」。
        assertEquals(1000L, payload.notes.single().updatedAt)
        assertEquals(2000L, payload.records.single().updatedAt)
        // 没有 deletedAt 时视为未删除。
        assertNull(payload.notes.single().deletedAt)
        assertEquals(2, payload.visibleCount)
    }

    @Test
    fun `schema 3 备份里的习惯数据被忽略，其余数据照常导入`() {
        val v3 = """
            {
              "app": "fangcun",
              "schema": 3,
              "exportedAt": 1,
              "notes": [{ "id": 1, "category": "TODO", "content": "旧待办", "time": 1000, "updatedAt": 1000 }],
              "habits": [{ "id": 9, "name": "喝水", "days": ["20261024"] }],
              "drinkCategories": [],
              "drinkRecords": []
            }
        """.trimIndent()

        val payload = BackupCodec.decode(v3)

        assertEquals(1, payload.notes.size)
        // 习惯工具已下线，它的数据不能被当成饮记读进来。
        assertTrue(payload.drinkCategories.isEmpty())
        assertTrue(payload.drinkRecords.isEmpty())
    }

    @Test
    fun `饮记的墓碑也能通过合并传过去`() {
        val category = DrinkCategory(
            id = 7L,
            name = "水",
            colorHex = "#2F86D6",
            createdMillis = 10L,
            updatedAt = 10L
        )
        val existing = BackupPayload(drinkCategories = listOf(category))
        val incoming = BackupPayload(
            drinkCategories = listOf(
                category.copy(updatedAt = 20L, deletedAt = 20L)
            )
        )

        val merged = incoming.mergedWith(existing)

        assertEquals(1, merged.drinkCategories.size)
        assertNotNull(merged.drinkCategories.single().deletedAt)
        assertEquals(0, merged.visibleDrinkCategories)
        assertEquals(MergeDiff(removed = 1), incoming.diffAgainst(existing))
    }

    @Test
    fun `默认类别不会把用户改过的名字合并回去`() {
        val seeded = DrinkDefaults.categories()
        val renamed = seeded.map { category ->
            if (category.id == DrinkDefaults.stableId("water")) {
                category.copy(name = "白水", updatedAt = 500L)
            } else {
                category
            }
        }

        // 后装的设备带着一份新的默认类别合并进来，用户改的名字必须留着。
        val merged = BackupPayload(drinkCategories = seeded)
            .mergedWith(BackupPayload(drinkCategories = renamed))

        val water = merged.drinkCategories.first { it.id == DrinkDefaults.stableId("water") }
        assertEquals("白水", water.name)
    }

    // ---- 容器：zip 与流式读写 ----

    @Test
    fun `导出的备份是 zip，读回来数据一致`() {
        val original = samplePayload()
        val bytes = ByteArrayOutputStream()
            .also { BackupContainer.write(original, it) }
            .toByteArray()

        assertEquals("zip 本地文件头是 PK\\003\\004", 'P'.code.toByte(), bytes[0])
        assertEquals(original, BackupContainer.read(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `压缩后体积明显小于裸 JSON`() {
        val payload = BackupPayload(sessions = listOf(longSession(pointCount = 1200)))
        val zipped = ByteArrayOutputStream()
            .also { BackupContainer.write(payload, it) }
            .size()
        val raw = BackupCodec.encode(payload).toByteArray(Charsets.UTF_8).size

        assertTrue("压缩包 ${zipped}B 应当明显小于裸 JSON ${raw}B", zipped * 2 <= raw)
    }

    @Test
    fun `老版本导出的裸 JSON 仍然能导入`() {
        val original = samplePayload()
        val plain = BackupCodec.encode(original).toByteArray(Charsets.UTF_8)

        assertEquals(original, BackupContainer.read(ByteArrayInputStream(plain)))
    }

    @Test
    fun `带缩进的备份也能读`() {
        // 老版本的导出是两空格缩进的，流式读取要能穿过换行和空白。
        val pretty = JSONObject(BackupCodec.encode(samplePayload())).toString(2)

        val restored = BackupCodec.decode(pretty)

        assertEquals(1, restored.notes.size)
        assertEquals(2, restored.sessions.single().points.size)
    }

    @Test
    fun `备忘正文里的括号引号和换行不会打乱分条`() {
        val tricky = "他说 \"别记了\" ] } { },\n第二行 \\ 反斜杠，还有 emoji 🚀"
        val payload = BackupPayload(
            notes = listOf(
                Note(1L, NoteCategory.DIARY, tricky, 10L),
                Note(2L, NoteCategory.DIARY, "下一条", 20L)
            )
        )

        val restored = BackupCodec.decode(BackupCodec.encode(payload))

        assertEquals(tricky, restored.notes.first { it.id == 1L }.content)
        assertEquals("后面那条不能被前一条的引号带跑", 2, restored.notes.size)
    }

    @Test
    fun `读摘要时整段跳过点阵`() {
        val encoded = BackupCodec.encode(
            BackupPayload(sessions = listOf(longSession(pointCount = 3)))
        )
        val stream = JsonStream(StringReader(encoded))
        stream.beginObject()
        var raw = ""
        while (true) {
            val key = stream.nextMember() ?: break
            if (key == "tracks") {
                stream.beginArray()
                while (stream.nextElement()) {
                    raw = stream.readRawValue(skipFields = setOf(JsonCodec.FIELD_POINTS))
                }
            } else {
                stream.skipValue()
            }
        }

        assertFalse("点阵不该被复制下来", raw.contains("31.23"))
        assertTrue("字段位置要留个 null，JSON 才合法", raw.contains("\"points\":null"))
        val session = JsonCodec.sessionFromJson(JSONObject(raw))
        assertEquals(3000L, session.id)
        assertTrue(session.points.isEmpty())
    }

    @Test
    fun `坐标只保留六位小数`() {
        // 6 位约 0.11 米，比 GPS 精度细得多；从 18 位数字缩到 9 位，点阵能省两成体积。
        val payload = BackupPayload(
            sessions = listOf(
                TrackSession(
                    id = 1L,
                    mode = TransportMode.WALK,
                    startMillis = 1L,
                    endMillis = 2L,
                    distanceMeters = 1.0,
                    points = listOf(
                        TrackPoint(31.234567890123456, 121.476543298765432, 5L)
                    )
                )
            )
        )

        val point = BackupCodec.decode(BackupCodec.encode(payload)).sessions.single().points.single()

        assertEquals(31.234568, point.latitude, 0.0)
        assertEquals(121.476543, point.longitude, 0.0)
    }

    @Test
    fun `压缩包里没有备份文件时报错而不是静默清空`() {
        val emptyZip = ByteArrayOutputStream()
            .also { out ->
                java.util.zip.ZipOutputStream(out).use { it.putNextEntry(java.util.zip.ZipEntry("x")) }
            }
            .toByteArray()

        val error = runCatching {
            BackupContainer.read(ByteArrayInputStream(emptyZip))
        }.exceptionOrNull()

        assertTrue(error is BackupFormatException)
    }

    /** 一条「一小时的轨迹」：3 秒一个点、坐标在小数点后第 4 位起变化。 */
    private fun longSession(pointCount: Int, id: Long = 3000L): TrackSession {
        val start = 1_700_000_200_000L
        return TrackSession(
            id = id,
            mode = TransportMode.WALK,
            startMillis = start,
            endMillis = start + pointCount * 3_000L,
            distanceMeters = pointCount * 3.0,
            points = (0 until pointCount).map { index ->
                TrackPoint(
                    latitude = 31.2345 + index * 0.00004,
                    longitude = 121.4765 + index * 0.00005,
                    timeMillis = start + index * 3_000L
                )
            }
        )
    }
}

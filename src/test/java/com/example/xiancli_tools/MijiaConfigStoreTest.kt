package com.example.xiancli_tools

import com.example.xiancli_tools.data.mijia.MijiaConfigStore
import com.example.xiancli_tools.data.mijia.MijiaDeviceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 改名 / 分组 / 分类只落在本机，`null`（跟随米家）与 `""`（未分组）必须能区分开——
 * 这两个值一旦混了，界面上「设为未分组」和「恢复默认」就变成同一个动作了。
 */
class MijiaConfigStoreTest {

    private fun tempFile(): File =
        File.createTempFile("mijia-config", ".json").also { it.delete() }

    @Test
    fun `没有文件时是空配置`() {
        val store = MijiaConfigStore(tempFile())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `改名会落盘，重新打开还在`() {
        val file = tempFile()
        MijiaConfigStore(file).rename("did-1", "  客厅大灯  ")
        assertEquals("客厅大灯", MijiaConfigStore(file).load()["did-1"]?.name)
    }

    @Test
    fun `名字留空就是恢复默认，条目会被删掉`() {
        val store = MijiaConfigStore(tempFile())
        store.rename("did-1", "客厅大灯")
        store.rename("did-1", "   ")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `空串是未分组，null 是跟随米家`() {
        val store = MijiaConfigStore(tempFile())
        store.setRoom("did-1", "")
        store.setRoom("did-2", null)

        assertEquals("", store.load()["did-1"]?.room)
        // did-2 上什么都没留下：跟随米家就是「没改过」。
        assertFalse(store.load().containsKey("did-2"))
    }

    @Test
    fun `从跟随米家改成未分组要能生效`() {
        val store = MijiaConfigStore(tempFile())
        store.rename("did-1", "客厅大灯")
        store.setRoom("did-1", null)
        assertEquals(null, store.load()["did-1"]?.room)

        store.setRoom("did-1", "   ")
        assertEquals("", store.load()["did-1"]?.room)
    }

    @Test
    fun `分类只认已知的八类`() {
        val store = MijiaConfigStore(tempFile())
        store.setKind("did-1", "light")
        assertEquals("light", store.load()["did-1"]?.kind)

        store.setKind("did-1", "不存在的分类")
        assertFalse(store.load().containsKey("did-1"))
    }

    @Test
    fun `恢复默认会清掉这一台的全部覆盖`() {
        val file = tempFile()
        val store = MijiaConfigStore(file)
        store.rename("did-1", "客厅大灯")
        store.setRoom("did-1", "客厅")
        store.setKind("did-1", "socket")
        store.reset("did-1")

        assertTrue(MijiaConfigStore(file).load().isEmpty())
    }

    @Test
    fun `文件坏了按空配置处理，不崩`() {
        val file = tempFile()
        file.writeText("{ 这不是 json")
        val store = MijiaConfigStore(file)
        assertTrue(store.load().isEmpty())
        // 坏文件不该挡住后面的写入。
        store.rename("did-1", "客厅大灯")
        assertEquals("客厅大灯", MijiaConfigStore(file).load()["did-1"]?.name)
    }

    @Test
    fun `只改了名字的设备不会带出空的分组`() {
        val file = tempFile()
        MijiaConfigStore(file).rename("did-1", "客厅大灯")
        val entry = MijiaConfigStore(file).load()["did-1"]
        assertEquals(MijiaDeviceConfig(name = "客厅大灯"), entry)
    }
}

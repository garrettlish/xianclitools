package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class NoteRepository(context: Context) {

    private val appContext = context.applicationContext
    private val notesFile = File(appContext.filesDir, NOTES_FILE)

    /** 界面用的备忘：墓碑不参与展示。 */
    @Synchronized
    fun loadNotes(): List<Note> = readRaw()
        .filter { it.deletedAt == null }
        .sortedByDescending { it.timeMillis }

    /** 含墓碑的全量，备份与合并必须用它，否则删除传不到别的设备。 */
    @Synchronized
    fun loadAllNotes(): List<Note> = readRaw().sortedByDescending { it.timeMillis }

    @Synchronized
    fun saveNote(note: Note) {
        val all = readRaw().toMutableList()
        val previous = all.firstOrNull { it.id == note.id }
        all.removeAll { it.id == note.id }
        // 保存等于「取消删除」，所以墓碑一并清掉。
        all.add(note.copy(updatedAt = nextStamp(previous?.updatedAt), deletedAt = null))
        write(all)
    }

    @Synchronized
    fun deleteNote(id: Long) {
        val all = readRaw()
        val stamp = nextStamp(all.firstOrNull { it.id == id }?.updatedAt)
        write(all.map { if (it.id == id) it.copy(deletedAt = stamp, updatedAt = stamp) else it })
    }

    /** 用给定列表整体替换本地笔记（导入备份时使用）。墓碑要一并写入，否则删除会丢。 */
    @Synchronized
    fun replaceAll(notes: List<Note>) {
        write(notes)
    }

    private fun readRaw(): List<Note> {
        if (!notesFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.notesFromJson(JSONArray(notesFile.readText()))
        }.onFailure { Log.e(TAG, "读取笔记失败", it) }.getOrDefault(emptyList())
    }

    /**
     * 修改时间戳。同一条记录在同一毫秒内被改两次时，光看时间分不出先后，
     * 所以强制比上一版大 1，保证「谁更新」的判断永远有确定答案。
     */
    private fun nextStamp(previous: Long?): Long =
        maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1L)

    private fun write(notes: List<Note>) {
        writeAtomically(JsonCodec.notesToJson(notes.sortedByDescending { it.timeMillis }).toString())
    }

    private fun writeAtomically(content: String) {
        runCatching {
            val temp = File(notesFile.parentFile, "${notesFile.name}.tmp")
            temp.writeText(content)
            if (notesFile.exists()) notesFile.delete()
            temp.renameTo(notesFile)
        }.onFailure { Log.e(TAG, "写入笔记失败", it) }
    }

    private companion object {
        const val TAG = "NoteRepository"
        const val NOTES_FILE = "notes.json"
    }
}

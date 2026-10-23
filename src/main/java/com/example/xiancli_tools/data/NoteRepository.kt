package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class NoteRepository(context: Context) {

    private val appContext = context.applicationContext
    private val notesFile = File(appContext.filesDir, NOTES_FILE)

    @Synchronized
    fun loadNotes(): List<Note> {
        if (!notesFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.notesFromJson(JSONArray(notesFile.readText()))
                .sortedByDescending { it.timeMillis }
        }.onFailure { Log.e(TAG, "读取笔记失败", it) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveNote(note: Note) {
        val notes = loadNotes().toMutableList()
        notes.removeAll { it.id == note.id }
        notes.add(note)
        write(notes)
    }

    @Synchronized
    fun deleteNote(id: Long) {
        write(loadNotes().filterNot { it.id == id })
    }

    /** 用给定列表整体替换本地笔记（导入备份时使用）。 */
    @Synchronized
    fun replaceAll(notes: List<Note>) {
        write(notes.sortedByDescending { it.timeMillis })
    }

    private fun write(notes: List<Note>) {
        writeAtomically(JsonCodec.notesToJson(notes).toString())
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

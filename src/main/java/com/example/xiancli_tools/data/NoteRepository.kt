package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NoteRepository(context: Context) {

    private val appContext = context.applicationContext
    private val notesFile = File(appContext.filesDir, NOTES_FILE)

    @Synchronized
    fun loadNotes(): List<Note> {
        if (!notesFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(notesFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    add(parseNote(array.getJSONObject(index)))
                }
            }.sortedByDescending { it.timeMillis }
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

    private fun write(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { array.put(it.toJson()) }
        writeAtomically(array.toString())
    }

    private fun writeAtomically(content: String) {
        runCatching {
            val temp = File(notesFile.parentFile, "${notesFile.name}.tmp")
            temp.writeText(content)
            if (notesFile.exists()) notesFile.delete()
            temp.renameTo(notesFile)
        }.onFailure { Log.e(TAG, "写入笔记失败", it) }
    }

    private fun Note.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("category", category.name)
        .put("content", content)
        .put("time", timeMillis)

    private fun parseNote(json: JSONObject): Note {
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

    private companion object {
        const val TAG = "NoteRepository"
        const val NOTES_FILE = "notes.json"
    }
}

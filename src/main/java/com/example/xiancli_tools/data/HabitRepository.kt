package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class HabitRepository(context: Context) {

    private val appContext = context.applicationContext
    private val habitsFile = File(appContext.filesDir, HABITS_FILE)

    @Synchronized
    fun loadHabits(): List<Habit> {
        if (!habitsFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.habitsFromJson(JSONArray(habitsFile.readText()))
                .sortedBy { it.createdMillis }
        }.onFailure { Log.e(TAG, "读取习惯失败", it) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveHabit(habit: Habit) {
        val habits = loadHabits().toMutableList()
        habits.removeAll { it.id == habit.id }
        habits.add(habit)
        write(habits.sortedBy { it.createdMillis })
    }

    @Synchronized
    fun deleteHabit(id: Long) {
        write(loadHabits().filterNot { it.id == id })
    }

    @Synchronized
    fun replaceAll(habits: List<Habit>) {
        write(habits.sortedBy { it.createdMillis })
    }

    private fun write(habits: List<Habit>) {
        runCatching {
            val temp = File(habitsFile.parentFile, "${habitsFile.name}.tmp")
            temp.writeText(JsonCodec.habitsToJson(habits).toString())
            if (habitsFile.exists()) habitsFile.delete()
            temp.renameTo(habitsFile)
        }.onFailure { Log.e(TAG, "写入习惯失败", it) }
    }

    private companion object {
        const val TAG = "HabitRepository"
        const val HABITS_FILE = "habits.json"
    }
}

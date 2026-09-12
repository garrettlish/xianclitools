package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LedgerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val recordsFile = File(appContext.filesDir, RECORDS_FILE)

    @Synchronized
    fun loadRecords(): List<ExpenseRecord> {
        if (!recordsFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(recordsFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    add(parseRecord(array.getJSONObject(index)))
                }
            }
        }.onFailure { Log.e(TAG, "读取记账记录失败", it) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun addRecord(record: ExpenseRecord) {
        val records = loadRecords().toMutableList()
        records.removeAll { it.id == record.id }
        records.add(record)
        write(records.sortedByDescending { it.timeMillis })
    }

    @Synchronized
    fun deleteRecord(id: Long) {
        write(loadRecords().filterNot { it.id == id })
    }

    private fun write(records: List<ExpenseRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        writeAtomically(array.toString())
    }

    private fun writeAtomically(content: String) {
        runCatching {
            val temp = File(recordsFile.parentFile, "${recordsFile.name}.tmp")
            temp.writeText(content)
            if (recordsFile.exists()) recordsFile.delete()
            temp.renameTo(recordsFile)
        }.onFailure { Log.e(TAG, "写入记账记录失败", it) }
    }

    private fun ExpenseRecord.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("category", category.name)
        .put("amount", amountCents)
        .put("note", note)
        .put("time", timeMillis)

    private fun parseRecord(json: JSONObject): ExpenseRecord {
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

    private companion object {
        const val TAG = "LedgerRepository"
        const val RECORDS_FILE = "ledger_records.json"
    }
}

package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class LedgerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val recordsFile = File(appContext.filesDir, RECORDS_FILE)

    @Synchronized
    fun loadRecords(): List<ExpenseRecord> {
        if (!recordsFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.recordsFromJson(JSONArray(recordsFile.readText()))
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

    /** 用给定列表整体替换本地记账记录（导入备份时使用）。 */
    @Synchronized
    fun replaceAll(records: List<ExpenseRecord>) {
        write(records.sortedByDescending { it.timeMillis })
    }

    private fun write(records: List<ExpenseRecord>) {
        writeAtomically(JsonCodec.recordsToJson(records).toString())
    }

    private fun writeAtomically(content: String) {
        runCatching {
            val temp = File(recordsFile.parentFile, "${recordsFile.name}.tmp")
            temp.writeText(content)
            if (recordsFile.exists()) recordsFile.delete()
            temp.renameTo(recordsFile)
        }.onFailure { Log.e(TAG, "写入记账记录失败", it) }
    }

    private companion object {
        const val TAG = "LedgerRepository"
        const val RECORDS_FILE = "ledger_records.json"
    }
}

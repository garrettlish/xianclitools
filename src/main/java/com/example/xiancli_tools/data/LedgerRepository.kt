package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class LedgerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val recordsFile = File(appContext.filesDir, RECORDS_FILE)

    /** 界面用的账目：墓碑不参与展示。 */
    @Synchronized
    fun loadRecords(): List<ExpenseRecord> = readRaw().filter { it.deletedAt == null }

    /** 含墓碑的全量，备份与合并必须用它，否则删除传不到别的设备。 */
    @Synchronized
    fun loadAllRecords(): List<ExpenseRecord> = readRaw()

    @Synchronized
    fun addRecord(record: ExpenseRecord) {
        val all = readRaw().toMutableList()
        val previous = all.firstOrNull { it.id == record.id }
        all.removeAll { it.id == record.id }
        all.add(record.copy(updatedAt = nextStamp(previous?.updatedAt), deletedAt = null))
        write(all)
    }

    @Synchronized
    fun deleteRecord(id: Long) {
        val all = readRaw()
        val stamp = nextStamp(all.firstOrNull { it.id == id }?.updatedAt)
        write(all.map { if (it.id == id) it.copy(deletedAt = stamp, updatedAt = stamp) else it })
    }

    /** 用给定列表整体替换本地记账记录（导入备份时使用）。墓碑要一并写入。 */
    @Synchronized
    fun replaceAll(records: List<ExpenseRecord>) {
        write(records)
    }

    private fun readRaw(): List<ExpenseRecord> {
        if (!recordsFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.recordsFromJson(JSONArray(recordsFile.readText()))
        }.onFailure { Log.e(TAG, "读取记账记录失败", it) }.getOrDefault(emptyList())
    }

    /** 修改时间戳；同一毫秒内改两次时强制比上一版大 1，保证先后可判。 */
    private fun nextStamp(previous: Long?): Long =
        maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1L)

    private fun write(records: List<ExpenseRecord>) {
        writeAtomically(
            JsonCodec.recordsToJson(records.sortedByDescending { it.timeMillis }).toString()
        )
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

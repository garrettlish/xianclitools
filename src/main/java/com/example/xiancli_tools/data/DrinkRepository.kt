package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

/**
 * 饮记的本地存储：类别一个文件、记录一个文件。
 *
 * 两个文件而不是一个，是因为类别很少改、记录每次点击都写，
 * 分开写只重写小的那份；合并（备份）时也各自按 LWW 处理，互不牵连。
 */
class DrinkRepository(context: Context) {

    private val appContext = context.applicationContext
    private val categoriesFile = File(appContext.filesDir, CATEGORIES_FILE)
    private val recordsFile = File(appContext.filesDir, RECORDS_FILE)

    // ---- 类别 ----

    /** 界面用的类别：墓碑不参与展示，按创建时间排序（默认五类的顺序由此固定）。 */
    @Synchronized
    fun loadCategories(): List<DrinkCategory> = readCategoriesWithDefaults()
        .filter { it.deletedAt == null }
        .sortedBy { it.createdMillis }

    /** 含墓碑的全量，备份与合并必须用它，否则删除传不到别的设备。 */
    @Synchronized
    fun loadAllCategories(): List<DrinkCategory> = readCategoriesWithDefaults()

    @Synchronized
    fun saveCategory(category: DrinkCategory) {
        val all = readCategories().toMutableList()
        val previous = all.firstOrNull { it.id == category.id }
        all.removeAll { it.id == category.id }
        all.add(category.copy(updatedAt = nextStamp(previous?.updatedAt), deletedAt = null))
        writeCategories(all)
    }

    /**
     * 删除类别时连同它下面的记录一起写墓碑。
     *
     * 只删类别的话，别的设备合并回来会把类别复活；只删本机记录而留墓碑不写，
     * 那些记录也会从对方那边再传回来一次。两边都写墓碑，合并才收敛。
     */
    @Synchronized
    fun deleteCategory(id: Long) {
        val categories = readCategories()
        val stamp = nextStamp(categories.firstOrNull { it.id == id }?.updatedAt)
        writeCategories(
            categories.map {
                if (it.id == id) it.copy(deletedAt = stamp, updatedAt = stamp) else it
            }
        )
        val records = readRecords()
        if (records.any { it.categoryId == id && it.deletedAt == null }) {
            writeRecords(
                records.map {
                    if (it.categoryId == id && it.deletedAt == null) {
                        it.copy(deletedAt = stamp, updatedAt = maxOf(stamp, it.updatedAt + 1))
                    } else {
                        it
                    }
                }
            )
        }
    }

    @Synchronized
    fun replaceAllCategories(categories: List<DrinkCategory>) {
        writeCategories(categories.sortedBy { it.createdMillis })
    }

    // ---- 记录 ----

    /** 界面用的记录：墓碑不参与展示。 */
    @Synchronized
    fun loadRecords(): List<DrinkRecord> = readRecords().filter { it.deletedAt == null }

    /** 含墓碑的全量，备份与合并必须用它。 */
    @Synchronized
    fun loadAllRecords(): List<DrinkRecord> = readRecords()

    @Synchronized
    fun addRecord(record: DrinkRecord) {
        val all = readRecords().toMutableList()
        val previous = all.firstOrNull { it.id == record.id }
        all.removeAll { it.id == record.id }
        all.add(record.copy(updatedAt = nextStamp(previous?.updatedAt), deletedAt = null))
        writeRecords(all)
    }

    @Synchronized
    fun deleteRecord(id: Long) {
        val all = readRecords()
        val stamp = nextStamp(all.firstOrNull { it.id == id }?.updatedAt)
        writeRecords(all.map { if (it.id == id) it.copy(deletedAt = stamp, updatedAt = stamp) else it })
    }

    @Synchronized
    fun replaceAllRecords(records: List<DrinkRecord>) {
        writeRecords(records)
    }

    // ---- 落盘 ----

    /**
     * 读类别；本地一条都没有（首次打开，或刚导入过一份没有饮记数据的旧备份）时铺上默认五类。
     *
     * 判断依据是「读出来是不是空的」而不是「文件在不在」：用户把五类全删掉后，
     * 文件里留着五条墓碑，读出来非空，默认类别就不会自己长回来。
     *
     * 已有的数据顺带做一次「默认类别改名」（见 [DrinkDefaults.renamed]）：
     * 种子只在空文件时铺，否则旧设备上永远留着改名前的那个类别。
     */
    private fun readCategoriesWithDefaults(): List<DrinkCategory> {
        val existing = readCategories()
        if (existing.isEmpty()) {
            val defaults = DrinkDefaults.categories()
            writeCategories(defaults)
            return defaults
        }
        val renamed = existing.map { DrinkDefaults.renamed(it) ?: it }
        if (renamed != existing) writeCategories(renamed)
        return renamed
    }

    private fun readCategories(): List<DrinkCategory> {
        if (!categoriesFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.drinkCategoriesFromJson(JSONArray(categoriesFile.readText()))
        }.onFailure { Log.e(TAG, "读取饮品类别失败", it) }.getOrDefault(emptyList())
    }

    private fun readRecords(): List<DrinkRecord> {
        if (!recordsFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.drinkRecordsFromJson(JSONArray(recordsFile.readText()))
        }.onFailure { Log.e(TAG, "读取饮记记录失败", it) }.getOrDefault(emptyList())
    }

    /** 修改时间戳；同一毫秒内改两次时强制比上一版大 1，保证先后可判。 */
    private fun nextStamp(previous: Long?): Long =
        maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1L)

    private fun writeCategories(categories: List<DrinkCategory>) {
        writeAtomically(
            categoriesFile,
            JsonCodec.drinkCategoriesToJson(categories.sortedBy { it.createdMillis }).toString()
        )
    }

    private fun writeRecords(records: List<DrinkRecord>) {
        writeAtomically(
            recordsFile,
            JsonCodec.drinkRecordsToJson(records.sortedByDescending { it.timeMillis }).toString()
        )
    }

    /** 原子写：先写临时文件再改名，中途被杀进程也不会留下半个 JSON。 */
    private fun writeAtomically(target: File, content: String) {
        runCatching {
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(content)
            if (target.exists()) target.delete()
            temp.renameTo(target)
        }.onFailure { Log.e(TAG, "写入 ${target.name} 失败", it) }
    }

    private companion object {
        const val TAG = "DrinkRepository"
        const val CATEGORIES_FILE = "drink_categories.json"
        const val RECORDS_FILE = "drink_records.json"
    }
}

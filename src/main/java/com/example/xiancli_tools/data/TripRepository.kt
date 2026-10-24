package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class TripRepository(context: Context) {

    private val appContext = context.applicationContext
    private val tripsFile = File(appContext.filesDir, TRIPS_FILE)

    /** 界面用的出行：墓碑不参与展示。 */
    @Synchronized
    fun loadTrips(): List<Trip> = readRaw()
        .filter { it.deletedAt == null }
        .sortedByDescending { it.startMillis }

    /** 含墓碑的全量，备份与合并必须用它，否则删除传不到别的设备。 */
    @Synchronized
    fun loadAllTrips(): List<Trip> = readRaw().sortedByDescending { it.startMillis }

    @Synchronized
    fun saveTrip(trip: Trip) {
        val all = readRaw().toMutableList()
        val previous = all.firstOrNull { it.id == trip.id }
        all.removeAll { it.id == trip.id }
        all.add(trip.copy(updatedAt = nextStamp(previous?.updatedAt), deletedAt = null))
        write(all)
    }

    @Synchronized
    fun deleteTrip(id: Long) {
        val all = readRaw()
        val stamp = nextStamp(all.firstOrNull { it.id == id }?.updatedAt)
        write(all.map { if (it.id == id) it.copy(deletedAt = stamp, updatedAt = stamp) else it })
    }

    @Synchronized
    fun replaceAll(trips: List<Trip>) {
        write(trips)
    }

    private fun readRaw(): List<Trip> {
        if (!tripsFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.tripsFromJson(JSONArray(tripsFile.readText()))
        }.onFailure { Log.e(TAG, "读取出行失败", it) }.getOrDefault(emptyList())
    }

    /** 修改时间戳；同一毫秒内改两次时强制比上一版大 1，保证先后可判。 */
    private fun nextStamp(previous: Long?): Long =
        maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1L)

    private fun write(trips: List<Trip>) {
        runCatching {
            val temp = File(tripsFile.parentFile, "${tripsFile.name}.tmp")
            temp.writeText(JsonCodec.tripsToJson(trips.sortedByDescending { it.startMillis }).toString())
            if (tripsFile.exists()) tripsFile.delete()
            temp.renameTo(tripsFile)
        }.onFailure { Log.e(TAG, "写入出行失败", it) }
    }

    private companion object {
        const val TAG = "TripRepository"
        const val TRIPS_FILE = "trips.json"
    }
}

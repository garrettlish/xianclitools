package com.example.xiancli_tools.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

class TripRepository(context: Context) {

    private val appContext = context.applicationContext
    private val tripsFile = File(appContext.filesDir, TRIPS_FILE)

    @Synchronized
    fun loadTrips(): List<Trip> {
        if (!tripsFile.exists()) return emptyList()
        return runCatching {
            JsonCodec.tripsFromJson(JSONArray(tripsFile.readText()))
                .sortedByDescending { it.startMillis }
        }.onFailure { Log.e(TAG, "读取出行失败", it) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveTrip(trip: Trip) {
        val trips = loadTrips().toMutableList()
        trips.removeAll { it.id == trip.id }
        trips.add(trip)
        write(trips.sortedByDescending { it.startMillis })
    }

    @Synchronized
    fun deleteTrip(id: Long) {
        write(loadTrips().filterNot { it.id == id })
    }

    @Synchronized
    fun replaceAll(trips: List<Trip>) {
        write(trips.sortedByDescending { it.startMillis })
    }

    private fun write(trips: List<Trip>) {
        runCatching {
            val temp = File(tripsFile.parentFile, "${tripsFile.name}.tmp")
            temp.writeText(JsonCodec.tripsToJson(trips).toString())
            if (tripsFile.exists()) tripsFile.delete()
            temp.renameTo(tripsFile)
        }.onFailure { Log.e(TAG, "写入出行失败", it) }
    }

    private companion object {
        const val TAG = "TripRepository"
        const val TRIPS_FILE = "trips.json"
    }
}

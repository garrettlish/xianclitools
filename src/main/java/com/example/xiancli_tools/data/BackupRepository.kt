package com.example.xiancli_tools.data

import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException

/**
 * 备份的落盘与恢复：把记事、记账、轨迹、专注记录、习惯、出行打包成单个 JSON 文件，
 * 或者从这样一个文件里把数据写回本地。
 *
 * 文件读写走 SAF（系统文件选择器）给出的 Uri，因此不需要任何存储权限。
 */
class BackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val noteRepository = NoteRepository(appContext)
    private val ledgerRepository = LedgerRepository(appContext)
    private val trackRepository = TrackRepository(appContext)
    private val timerRepository = TimerSessionRepository(appContext)
    private val habitRepository = HabitRepository(appContext)
    private val tripRepository = TripRepository(appContext)

    fun snapshot(): BackupPayload = BackupPayload(
        notes = noteRepository.loadNotes(),
        records = ledgerRepository.loadRecords(),
        sessions = trackRepository.loadSessions(),
        timers = timerRepository.loadSessions(),
        habits = habitRepository.loadHabits(),
        trips = tripRepository.loadTrips()
    )

    /** 把当前全部数据写入指定 Uri，返回写出的内容。 */
    fun export(uri: Uri): BackupPayload {
        val payload = snapshot()
        val stream = appContext.contentResolver.openOutputStream(uri)
            ?: throw FileNotFoundException("无法写入所选文件")
        stream.use { it.write(BackupCodec.encode(payload).toByteArray(Charsets.UTF_8)) }
        return payload
    }

    /** 从指定 Uri 读取并解析备份文件。 */
    fun read(uri: Uri): BackupPayload {
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("无法读取所选文件")
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        return BackupCodec.decode(text)
    }

    /**
     * 应用备份。[replaceExisting] 为 true 时清空现有数据后导入，
     * 否则按 id 合并、只补充新记录。
     */
    fun apply(payload: BackupPayload, replaceExisting: Boolean) {
        val target = if (replaceExisting) payload else payload.mergedWith(snapshot())
        noteRepository.replaceAll(target.notes)
        ledgerRepository.replaceAll(target.records)
        trackRepository.replaceAll(target.sessions)
        timerRepository.replaceAll(target.timers)
        habitRepository.replaceAll(target.habits)
        tripRepository.replaceAll(target.trips)
    }
}

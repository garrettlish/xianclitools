package com.example.xiancli_tools.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份的落盘与恢复：把记事、记账、轨迹、专注记录、饮记、出行打包成单个文件，
 * 或者从这样一个文件里把数据写回本地。
 *
 * 文件读写走 SAF（系统文件选择器）给出的 Uri，因此不需要任何存储权限。
 * 格式是 [BackupContainer] 定义的 zip（内含 `backup.json`），老的裸 JSON 备份也能导入。
 */
class BackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val noteRepository = NoteRepository(appContext)
    private val ledgerRepository = LedgerRepository(appContext)
    private val trackRepository = TrackRepository(appContext)
    private val timerRepository = TimerSessionRepository(appContext)
    private val drinkRepository = DrinkRepository(appContext)
    private val tripRepository = TripRepository(appContext)

    /**
     * 备份取的是**含墓碑**的全量数据。
     * 只读「可见数据」会让删除传不到别的设备——对方那条记录会一直留着。
     */
    fun snapshot(): BackupPayload = BackupPayload(
        notes = noteRepository.loadAllNotes(),
        records = ledgerRepository.loadAllRecords(),
        sessions = trackRepository.loadAllSessions(),
        timers = timerRepository.loadAllSessions(),
        drinkCategories = drinkRepository.loadAllCategories(),
        drinkRecords = drinkRepository.loadAllRecords(),
        trips = tripRepository.loadAllTrips()
    )

    /** 把当前全部数据写入指定 Uri，返回写出的内容。 */
    fun export(uri: Uri): BackupPayload {
        val payload = snapshot()
        val stream = appContext.contentResolver.openOutputStream(uri)
            ?: throw FileNotFoundException("无法写入所选文件")
        stream.use { BackupContainer.write(payload, it) }
        return payload
    }

    /** 从指定 Uri 读取并解析备份文件（zip 或老的裸 JSON 都认）。 */
    fun read(uri: Uri): BackupPayload {
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("无法读取所选文件")
        return stream.use { BackupContainer.read(it) }
    }

    /**
     * 把当前数据写进缓存目录，返回可以交给系统分享面板的 Uri。
     *
     * 走 FileProvider，接收方拿到的只是这一次的临时读权限；
     * 文件留在应用缓存里（`cache/backup/`），系统空间紧张时会自己清掉。
     */
    fun writeShareFile(now: Long = System.currentTimeMillis()): Uri {
        val dir = File(appContext.cacheDir, "backup").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date(now))
        val file = File(dir, "朝夕备份-$stamp.zip")
        file.outputStream().use { BackupContainer.write(snapshot(), it, now) }
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
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
        drinkRepository.replaceAllCategories(target.drinkCategories)
        drinkRepository.replaceAllRecords(target.drinkRecords)
        tripRepository.replaceAll(target.trips)
    }
}

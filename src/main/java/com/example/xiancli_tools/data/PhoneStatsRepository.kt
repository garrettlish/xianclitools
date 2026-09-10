package com.example.xiancli_tools.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.os.StatFs

enum class AppCategory(val label: String) {
    GAME("游戏"),
    AUDIO("音频"),
    VIDEO("视频"),
    IMAGE("图片"),
    SOCIAL("社交"),
    NEWS("新闻"),
    MAPS("地图"),
    PRODUCTIVITY("效率"),
    ACCESSIBILITY("无障碍"),
    UNDEFINED("未分类")
}

data class CategoryCount(val category: AppCategory, val count: Int)

data class PhoneStats(
    val totalApps: Int,
    val userApps: Int,
    val systemApps: Int,
    val launchableApps: Int,
    val categories: List<CategoryCount>,
    val storageTotalBytes: Long,
    val storageUsedBytes: Long,
    val memoryTotalBytes: Long,
    val memoryUsedBytes: Long
)

object PhoneStatsRepository {

    fun load(context: Context): PhoneStats {
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(0)

        var userApps = 0
        var systemApps = 0
        var launchableApps = 0
        val categoryCounts = HashMap<AppCategory, Int>()

        apps.forEach { info ->
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            if (isSystem) systemApps++ else userApps++

            if (packageManager.getLaunchIntentForPackage(info.packageName) != null) {
                launchableApps++
            }

            val category = info.toCategory()
            categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
        }

        val categories = categoryCounts.entries
            .map { CategoryCount(it.key, it.value) }
            .sortedByDescending { it.count }

        val (storageTotal, storageUsed) = internalStorage()
        val (memoryTotal, memoryUsed) = memory(context)

        return PhoneStats(
            totalApps = apps.size,
            userApps = userApps,
            systemApps = systemApps,
            launchableApps = launchableApps,
            categories = categories,
            storageTotalBytes = storageTotal,
            storageUsedBytes = storageUsed,
            memoryTotalBytes = memoryTotal,
            memoryUsedBytes = memoryUsed
        )
    }

    private fun ApplicationInfo.toCategory(): AppCategory {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return AppCategory.UNDEFINED
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.AUDIO
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.IMAGE
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.MAPS
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.ACCESSIBILITY
            else -> AppCategory.UNDEFINED
        }
    }

    private fun internalStorage(): Pair<Long, Long> = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        total to (total - free)
    }.getOrDefault(0L to 0L)

    private fun memory(context: Context): Pair<Long, Long> = runCatching {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        info.totalMem to (info.totalMem - info.availMem)
    }.getOrDefault(0L to 0L)
}

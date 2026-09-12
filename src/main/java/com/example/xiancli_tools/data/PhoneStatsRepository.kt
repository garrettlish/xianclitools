package com.example.xiancli_tools.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.util.Locale

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
    SHOPPING("购物"),
    FINANCE("金融"),
    FOOD("美食"),
    TRAVEL("出行"),
    EDUCATION("教育"),
    TOOLS("工具"),
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

    private val categoryKeywords: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.GAME to listOf(
            "game", "游戏", "手游", "tmgp", "sgame", "mihoyo", "hypergryph",
            "arknights", "genshin", "puzzle", "arcade", "消消乐", "欢乐", "棋牌",
            "麻将", "斗地主", "王者", "荣耀", "吃鸡", "三国", "传奇", "梦幻", "大话",
            "迷你世界", "minecraft", "roblox", "使命召唤", "穿越火线", "地下城",
            "英雄联盟", "蛋仔", "光遇", "云顶", "金铲铲"
        ),
        AppCategory.FOOD to listOf(
            "外卖", "美食", "美团", "meituan", "饿了么", "eleme", "肯德基", "kfc",
            "麦当劳", "mcdonald", "星巴克", "starbucks", "下厨房", "买菜", "生鲜",
            "叮咚", "盒马", "瑞幸", "luckin", "喜茶", "奈雪", "蜜雪", "food",
            "restaurant", "菜谱"
        ),
        AppCategory.SHOPPING to listOf(
            "shop", "shopping", "mall", "商城", "购物", "taobao", "淘宝", "tmall",
            "天猫", "jingdong", "京东", "pinduoduo", "拼多多", "闲鱼", "goofish",
            "amazon", "ebay", "唯品会", "vipshop", "suning", "苏宁", "xiaohongshu",
            "xhs", "得物", "poizon"
        ),
        AppCategory.FINANCE to listOf(
            "bank", "银行", "pay", "支付", "wallet", "钱包", "alipay", "支付宝",
            "云闪付", "unionpay", "finance", "证券", "股票", "stock", "基金", "fund",
            "理财", "保险", "insur", "记账", "loan", "借贷", "信用卡", "平安", "众安"
        ),
        AppCategory.TRAVEL to listOf(
            "出行", "travel", "trip", "12306", "铁路", "机票", "flight", "航旅",
            "携程", "ctrip", "去哪儿", "qunar", "飞猪", "fliggy", "didi", "滴滴",
            "打车", "出租", "共享单车", "公交", "地铁", "酒店", "hotel", "booking",
            "airbnb", "民宿", "曹操"
        ),
        AppCategory.EDUCATION to listOf(
            "edu", "learn", "study", "教育", "学习", "课堂", "网课", "词典",
            "dictionary", "单词", "有道", "youdao", "作业", "题库", "course", "课程",
            "classin", "学习通", "慕课", "mooc", "编程", "极客"
        ),
        AppCategory.SOCIAL to listOf(
            "wechat", "weixin", "微信", "mobileqq", "weibo", "微博", "facebook",
            "instagram", "twitter", "telegram", "whatsapp", "messenger", "discord",
            "snapchat", "linkedin", "tiktok", "soul", "陌陌", "探探", "脉脉", "社交",
            "交友", "聊天"
        ),
        AppCategory.VIDEO to listOf(
            "video", "视频", "movie", "影视", "电影", "bilibili", "哔哩", "youku",
            "优酷", "iqiyi", "爱奇艺", "qqlive", "腾讯视频", "douyin", "抖音",
            "aweme", "kuaishou", "快手", "youtube", "netflix", "mgtv", "芒果",
            "直播", "斗鱼", "douyu", "虎牙", "huya", "剪映", "capcut"
        ),
        AppCategory.AUDIO to listOf(
            "music", "音乐", "audio", "podcast", "sound", "spotify", "kugou",
            "kuwo", "cloudmusic", "喜马拉雅", "ximalaya", "蜻蜓", "qingting",
            "radio", "电台", "唱吧", "karaoke", "soundcloud", "audible"
        ),
        AppCategory.IMAGE to listOf(
            "photo", "camera", "gallery", "image", "picture", "相机", "图库", "相册",
            "美图", "meitu", "beauty", "美颜", "picsart", "snapseed", "photoshop",
            "lightroom"
        ),
        AppCategory.NEWS to listOf(
            "news", "新闻", "头条", "资讯", "澎湃", "zhihu", "知乎", "reddit",
            "medium", "flipboard", "rss", "reader", "阅读", "novel", "小说",
            "掌阅", "ireader", "起点", "qidian", "番茄小说", "书旗", "weread",
            "kindle"
        ),
        AppCategory.MAPS to listOf(
            "map", "地图", "导航", "navigation", "navi", "gps", "amap", "高德",
            "腾讯地图", "waze", "定位", "北斗"
        ),
        AppCategory.PRODUCTIVITY to listOf(
            "office", "docs", "文档", "note", "笔记", "notion", "evernote", "印象笔记",
            "mail", "邮箱", "email", "outlook", "gmail", "办公", "wps", "slack",
            "teams", "zoom", "会议", "钉钉", "dingtalk", "todo", "待办", "calendar",
            "日历", "效率", "企业微信", "飞书", "feishu", "lark", "石墨"
        ),
        AppCategory.TOOLS to listOf(
            "tool", "工具", "clean", "清理", "管家", "大师", "助手", "file", "文件",
            "管理", "compass", "指南针", "weather", "天气", "calculator", "计算器",
            "browser", "浏览器", "chrome", "firefox", "quark", "夸克", "ucmobile",
            "mtt", "输入法", "input", "keyboard", "搜狗", "sogou", "下载", "download",
            "加速", "vpn", "录屏", "screenshot", "截屏", "手电筒", "flashlight",
            "闹钟", "clock", "时钟", "录音", "recorder", "扫描", "scanner", "二维码",
            "qr", "投屏", "cast", "遥控", "remote", "shareit", "快传", "蓝牙",
            "bluetooth", "wifi", "settings", "设置", "launcher", "桌面", "主题",
            "壁纸", "备份", "backup", "存储", "storage", "省电", "battery", "电池",
            "speedtest"
        ),
        AppCategory.ACCESSIBILITY to listOf(
            "accessibility", "无障碍", "talkback"
        )
    )

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

            if (packageManager.getLaunchIntentForPackage(info.packageName) == null) {
                return@forEach
            }
            launchableApps++

            val category = info.toCategory(packageManager)
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

    private fun ApplicationInfo.toCategory(packageManager: PackageManager): AppCategory {
        declaredCategory()?.let { return it }
        val label = runCatching { loadLabel(packageManager).toString() }.getOrDefault("")
        val haystack = "$packageName $label".lowercase(Locale.ROOT)
        return categoryKeywords.firstOrNull { (_, keywords) ->
            keywords.any { haystack.contains(it) }
        }?.first ?: AppCategory.UNDEFINED
    }

    private fun ApplicationInfo.declaredCategory(): AppCategory? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
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
            else -> null
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

package com.example.xiancli_tools.data

import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * 饮品类别：名字 + 颜色，用户可增删改。
 *
 * 和账本的固定枚举不同，饮记的类别是数据而不是代码——「奶茶」「气泡水」这种
 * 每个人都不一样，写死在枚举里迟早不够用。
 */
data class DrinkCategory(
    val id: Long,
    val name: String,
    val colorHex: String,
    val createdMillis: Long,
    /** 最后修改时间，多设备合并时比新旧（大者胜）。 */
    val updatedAt: Long = createdMillis,
    /** 删除墓碑：非空表示已删。 */
    val deletedAt: Long? = null
)

/**
 * 一次「喝」的记录。[timeMillis] 是业务时间（允许补记任意时刻），
 * 一次点击就是一条，不合并成「今天喝了 3 杯」这种计数——计数可以由记录加出来，
 * 反过来则还原不出「几点喝的第几杯」。
 */
data class DrinkRecord(
    val id: Long,
    val categoryId: Long,
    val timeMillis: Long,
    val updatedAt: Long = timeMillis,
    val deletedAt: Long? = null
)

enum class DrinkPeriod(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    CUSTOM("自定义")
}

/** 堆叠柱图的一根柱子：label 是轴标签，segments 是 categoryId -> 次数。 */
data class DrinkBar(val label: String, val segments: Map<Long, Int>) {
    val total: Int
        get() = segments.values.sum()
}

data class DrinkSummary(
    val period: DrinkPeriod,
    val range: DateRange,
    /** 区间内的记录，时间倒序。 */
    val records: List<DrinkRecord>
) {
    /** 报表标题用词：本日 / 本周 / 本月 / 本年 / 所选区间。 */
    val rangeName: String
        get() = if (period == DrinkPeriod.CUSTOM) "所选区间" else "本${period.label}"

    val totalCount: Int
        get() = records.size

    /** 有记录的天数，用来算「日均」时排除空白日之外的另一种口径。 */
    val activeDays: Int
        get() = records.map { DateRanges.dayKey(it.timeMillis) }.distinct().size

    val countsByCategory: Map<Long, Int>
        get() = records.groupingBy { it.categoryId }.eachCount()

    /** 次数最多的类别 id；并列时取先出现的（记录已按时间倒序，即最近喝的那个）。 */
    val topCategoryId: Long?
        get() = countsByCategory.maxByOrNull { it.value }?.key
}

/**
 * 饮记的聚合计算。纯函数、无副作用：输入记录与区间，输出图表和分组所需的数据，
 * 统计页的每个数字都能和明细页逐条对上。
 */
object DrinkAnalytics {

    fun rangeOf(
        period: DrinkPeriod,
        now: Long = System.currentTimeMillis(),
        customRange: DateRange? = null
    ): DateRange = when (period) {
        DrinkPeriod.DAY -> DateRanges.day(now)
        DrinkPeriod.WEEK -> DateRanges.week(now)
        DrinkPeriod.MONTH -> DateRanges.month(now)
        DrinkPeriod.YEAR -> DateRanges.year(now)
        DrinkPeriod.CUSTOM -> customRange ?: DateRanges.lastDays(now)
    }

    fun summarize(
        records: List<DrinkRecord>,
        period: DrinkPeriod,
        now: Long = System.currentTimeMillis(),
        customRange: DateRange? = null
    ): DrinkSummary {
        val range = rangeOf(period, now, customRange)
        val inRange = records
            .filter { it.timeMillis in range.startMillis until range.endMillis }
            .sortedByDescending { it.timeMillis }
        return DrinkSummary(period, range, inRange)
    }

    /** 类别筛选：空集合表示「全部」。 */
    fun filterByCategory(records: List<DrinkRecord>, selected: Set<Long>): List<DrinkRecord> =
        if (selected.isEmpty()) records else records.filter { it.categoryId in selected }

    fun todayCounts(records: List<DrinkRecord>, now: Long = System.currentTimeMillis()): Map<Long, Int> {
        val range = DateRanges.day(now)
        return records
            .filter { it.timeMillis in range.startMillis until range.endMillis }
            .groupingBy { it.categoryId }
            .eachCount()
    }

    /**
     * 柱图分桶：日看时段、周看星期、月看日期、年看月份、自定义按跨度自动选粒度。
     * 桶位标签由这里统一给出，屏幕层不再自己拼一套。
     */
    fun bars(
        period: DrinkPeriod,
        summary: DrinkSummary,
        now: Long = System.currentTimeMillis()
    ): List<DrinkBar> {
        val records = summary.records
        return when (period) {
            DrinkPeriod.DAY -> accumulate(
                records = records,
                labels = listOf("凌晨", "上午", "下午", "晚上"),
                bucketOf = { hourOf(it) / 6 }
            )

            DrinkPeriod.WEEK -> accumulate(
                records = records,
                labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                bucketOf = { dayFromMonday(it) }
            )

            DrinkPeriod.MONTH -> accumulate(
                records = records,
                labels = (1..daysInMonth(summary.range.startMillis)).map { it.toString() },
                bucketOf = { dayOfMonth(it) - 1 }
            )

            DrinkPeriod.YEAR -> accumulate(
                records = records,
                labels = (1..12).map { "${it}月" },
                bucketOf = { monthOf(it) - 1 }
            )

            DrinkPeriod.CUSTOM -> {
                val plan = TrendBuckets.plan(summary.range)
                val buckets = List(plan.size) { HashMap<Long, Int>() }
                records.forEach { record ->
                    val index = plan.indexOf(record.timeMillis)
                    if (index in buckets.indices) {
                        val bucket = buckets[index]
                        bucket[record.categoryId] = (bucket[record.categoryId] ?: 0) + 1
                    }
                }
                plan.labels.mapIndexed { index, label -> DrinkBar(label, buckets[index]) }
            }
        }
    }

    private fun accumulate(
        records: List<DrinkRecord>,
        labels: List<String>,
        bucketOf: (Long) -> Int
    ): List<DrinkBar> {
        val buckets = List(labels.size) { HashMap<Long, Int>() }
        records.forEach { record ->
            val index = bucketOf(record.timeMillis)
            if (index in buckets.indices) {
                val bucket = buckets[index]
                bucket[record.categoryId] = (bucket[record.categoryId] ?: 0) + 1
            }
        }
        return labels.mapIndexed { index, label -> DrinkBar(label, buckets[index]) }
    }

    /** 逐小时次数，日热力图用（记录应已裁到当天）。 */
    fun hourlyCounts(records: List<DrinkRecord>): Map<Int, Int> =
        records.groupingBy { hourOf(it.timeMillis) }.eachCount()

    /** 每天次数，键为 `yyyyMMdd`（和明细分组同一套键）。 */
    fun dailyCounts(records: List<DrinkRecord>): Map<String, Int> =
        records.groupingBy { DateRanges.dayKey(it.timeMillis) }.eachCount()

    /** 每月次数，年热力图用。 */
    fun monthlyCounts(records: List<DrinkRecord>): Map<Int, Int> =
        records.groupingBy { monthOf(it.timeMillis) }.eachCount()

    /** 按天分组（时间倒序），只返回有记录的天；第一个值是当天零点。 */
    fun groupByDay(records: List<DrinkRecord>): List<Pair<Long, List<DrinkRecord>>> =
        records.groupBy { DateRanges.dayKey(it.timeMillis) }
            .toList()
            .sortedByDescending { it.first }
            .map { (_, list) ->
                DateRanges.startOfDay(list.first().timeMillis) to
                    list.sortedByDescending { it.timeMillis }
            }

    /** 年月视图的月份索引（月倒序）。 */
    fun groupByMonth(records: List<DrinkRecord>): List<Pair<Int, Int>> =
        records.groupingBy { monthOf(it.timeMillis) }
            .eachCount()
            .toList()
            .sortedByDescending { it.first }

    /**
     * 柱图右上角的补充指标：日视图说集中在哪个时段，其余周期给日均。
     * 分母用「已经过去的天/月」，而不是整个区间——本周三说「日均」不该除以 7。
     */
    fun averageLabel(
        summary: DrinkSummary,
        bars: List<DrinkBar>,
        now: Long = System.currentTimeMillis()
    ): String? {
        if (summary.period == DrinkPeriod.DAY) {
            val peak = bars.maxByOrNull { it.total } ?: return null
            return if (peak.total == 0) null else "集中在${peak.label}"
        }
        val units = elapsedUnits(summary, now)
        if (units <= 0) return null
        val unit = if (summary.period == DrinkPeriod.YEAR) "月均" else "日均"
        return String.format(Locale.US, "%s %.1f 次", unit, summary.totalCount.toFloat() / units)
    }

    /** 区间里已经过去的「天」数（年视图是「月」数），至少 1。 */
    fun elapsedUnits(summary: DrinkSummary, now: Long = System.currentTimeMillis()): Int {
        val range = summary.range
        if (summary.period == DrinkPeriod.YEAR) {
            if (now < range.startMillis) return 1
            if (now >= range.endMillis) return 12
            return monthOf(now)
        }
        val end = minOf(now + 1, range.endMillis)
        return DateRanges.daysBetween(range.startMillis, end).coerceAtLeast(1)
    }

    /** 日视图高亮「现在」所在的柱位；周/月/年同样能定位。 */
    fun highlightIndex(
        period: DrinkPeriod,
        range: DateRange,
        now: Long = System.currentTimeMillis()
    ): Int? = when (period) {
        DrinkPeriod.DAY -> if (isSameDay(now, range.startMillis)) hourOf(now) / 6 else null
        DrinkPeriod.WEEK -> if (isSameWeek(now, range.startMillis)) dayFromMonday(now) else null
        DrinkPeriod.MONTH -> if (isSameMonth(now, range.startMillis)) dayOfMonth(now) - 1 else null
        DrinkPeriod.YEAR -> if (isSameYear(now, range.startMillis)) monthOf(now) - 1 else null
        DrinkPeriod.CUSTOM -> null
    }

    /** 从这个下标开始的柱位属于「还没到」，画成留空而不是 0 次。 */
    fun futureStartIndex(
        period: DrinkPeriod,
        range: DateRange,
        now: Long = System.currentTimeMillis()
    ): Int? = when (period) {
        DrinkPeriod.DAY -> null
        DrinkPeriod.WEEK -> if (isSameWeek(now, range.startMillis)) dayFromMonday(now) + 1 else null
        DrinkPeriod.MONTH -> if (isSameMonth(now, range.startMillis)) dayOfMonth(now) else null
        DrinkPeriod.YEAR -> if (isSameYear(now, range.startMillis)) monthOf(now) else null
        DrinkPeriod.CUSTOM -> null
    }

    private fun isSameDay(first: Long, second: Long): Boolean =
        DateRanges.dayKey(first) == DateRanges.dayKey(second)

    private fun isSameWeek(first: Long, second: Long): Boolean =
        DateRanges.week(first).startMillis == DateRanges.week(second).startMillis

    private fun isSameMonth(first: Long, second: Long): Boolean =
        DateRanges.startOfMonth(first) == DateRanges.startOfMonth(second)

    private fun isSameYear(first: Long, second: Long): Boolean =
        DateRanges.startOfYear(first) == DateRanges.startOfYear(second)

    private fun calendarOf(millis: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = millis }

    private fun hourOf(millis: Long): Int = calendarOf(millis).get(Calendar.HOUR_OF_DAY)

    private fun dayOfMonth(millis: Long): Int = calendarOf(millis).get(Calendar.DAY_OF_MONTH)

    private fun monthOf(millis: Long): Int = calendarOf(millis).get(Calendar.MONTH) + 1

    /** 周一为 0。 */
    private fun dayFromMonday(millis: Long): Int =
        (calendarOf(millis).get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun daysInMonth(millis: Long): Int =
        calendarOf(millis).getActualMaximum(Calendar.DAY_OF_MONTH)
}

/**
 * 初始五类。
 *
 * id 由固定 key 派生而不是随机生成：两台设备各自首次启动后，默认类别拥有相同的 id，
 * 局域网直传 / 备份合并时不会出现「水」两份。用户自建的类别仍走 [Identity.newId]。
 */
object DrinkDefaults {

    private data class Default(val key: String, val name: String, val colorHex: String)

    /**
     * key 参与派生 id，**不能跟着名字改**：改了等于换了类别 id，
     * 升级过的设备和全新安装的设备会各有一份同名类别，合并后就出现两个「维C」。
     * 所以「酒」换成「维C」时 key 仍是 `alcohol`，靠 [renamed] 就地改名字和颜色。
     */
    private val defaults = listOf(
        Default("water", "水", "#2F86D6"),
        Default("coffee", "咖啡", "#8D6748"),
        Default("tea", "茶", "#5AA469"),
        Default("soft", "饮料", "#9B6DB5"),
        Default("alcohol", "维C", "#E8963A")
    )

    /** 曾经铺出去过、后来被替换掉的默认类别：id 不变，只换名字和颜色。 */
    private data class Renamed(val key: String, val oldName: String)

    private val renames = listOf(
        Renamed("alcohol", "酒")
    )

    fun stableId(key: String): Long =
        UUID.nameUUIDFromBytes("zhaoxi.drink.$key".toByteArray())
            .mostSignificantBits and Long.MAX_VALUE

    /**
     * 默认类别的修改时间固定成 [SEED_STAMP]，而不是各自的安装时间。
     *
     * 否则「后装的设备」会用更新的时间戳把「先装的设备上改过的名字」合并掉——
     * 种子内容不该有资格覆盖用户的编辑。用户改名后 updatedAt 是当下时间，天然更大。
     */
    private const val SEED_STAMP = 1L

    fun categories(now: Long = System.currentTimeMillis()): List<DrinkCategory> =
        defaults.map { default ->
            DrinkCategory(
                id = stableId(default.key),
                name = default.name,
                colorHex = default.colorHex,
                createdMillis = now,
                updatedAt = SEED_STAMP
            )
        }

    /**
     * 把改名前的默认类别就地换个名字和颜色，返回 null 表示不需要动。
     *
     * 只改种子是不够的：已经装过的设备文件里躺着的还是旧五类，不会重新铺种子。
     * 这里只动「用户没碰过」（`updatedAt` 仍是 [SEED_STAMP]）且名字还是旧默认值的那一条，
     * 用户自己起的名字、自己配的颜色不会被盖掉。时间戳保持 [SEED_STAMP] 不变，
     * 这样它仍然没有资格压过任何一次真实编辑。
     */
    fun renamed(category: DrinkCategory): DrinkCategory? {
        if (category.updatedAt != SEED_STAMP) return null
        val old = renames.firstOrNull { stableId(it.key) == category.id } ?: return null
        if (category.name != old.oldName) return null
        val current = defaults.firstOrNull { stableId(it.key) == category.id } ?: return null
        return category.copy(name = current.name, colorHex = current.colorHex)
    }
}

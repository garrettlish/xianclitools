package com.example.xiancli_tools

import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.DrinkAnalytics
import com.example.xiancli_tools.data.DrinkDefaults
import com.example.xiancli_tools.data.DrinkPeriod
import com.example.xiancli_tools.data.DrinkRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DrinkAnalyticsTest {

    private fun at(month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(2026, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // 2026-10-24 是周六，所在自然周为 10-19（周一）到 10-25（周日）。
    private val now = at(10, 24)

    private val records = listOf(
        DrinkRecord(id = 1L, categoryId = 100L, timeMillis = at(10, 23, hour = 21)),
        DrinkRecord(id = 2L, categoryId = 100L, timeMillis = at(10, 24, hour = 8)),
        DrinkRecord(id = 3L, categoryId = 200L, timeMillis = at(10, 24, hour = 9, minute = 30)),
        DrinkRecord(id = 4L, categoryId = 100L, timeMillis = at(10, 24, hour = 21))
    )

    @Test
    fun `一天的次数就是当天记录的条数，统计和明细对得上`() {
        val summary = DrinkAnalytics.summarize(records, DrinkPeriod.DAY, now)

        assertEquals("前一天的记录不算今天", 3, summary.totalCount)
        assertEquals(2, summary.countsByCategory[100L])
        assertEquals(1, summary.countsByCategory[200L])
        assertEquals(1, summary.activeDays)

        val hourly = DrinkAnalytics.hourlyCounts(summary.records)
        assertEquals(1, hourly[8])
        assertEquals(1, hourly[9])
        assertEquals(1, hourly[21])
    }

    @Test
    fun `柱图按周期分桶：日看时段、周看星期`() {
        val day = DrinkAnalytics.summarize(records, DrinkPeriod.DAY, now)
        val dayBars = DrinkAnalytics.bars(DrinkPeriod.DAY, day, now)

        assertEquals(listOf("凌晨", "上午", "下午", "晚上"), dayBars.map { it.label })
        assertEquals(2, dayBars[1].total)
        assertEquals(1, dayBars[3].total)

        val week = DrinkAnalytics.summarize(records, DrinkPeriod.WEEK, now)
        val weekBars = DrinkAnalytics.bars(DrinkPeriod.WEEK, week, now)

        assertEquals(4, week.totalCount)
        assertEquals("周六 3 次：8 点、9 点半、21 点", 3, weekBars[5].total)
        assertEquals("周五 1 次", 1, weekBars[4].total)
        assertEquals(0, weekBars[0].total)
    }

    @Test
    fun `今天的柱位被标出，之后的天留空`() {
        val week = DrinkAnalytics.summarize(records, DrinkPeriod.WEEK, now)

        assertEquals(5, DrinkAnalytics.highlightIndex(DrinkPeriod.WEEK, week.range, now))
        assertEquals(6, DrinkAnalytics.futureStartIndex(DrinkPeriod.WEEK, week.range, now))
    }

    @Test
    fun `日均的分母是已经过去的天数，不拿整周吓人`() {
        val week = DrinkAnalytics.summarize(records, DrinkPeriod.WEEK, now)
        val bars = DrinkAnalytics.bars(DrinkPeriod.WEEK, week, now)

        // 周一到周六共 6 天，4 次 → 0.7 次/天。
        assertEquals(6, DrinkAnalytics.elapsedUnits(week, now))
        assertEquals("日均 0.7 次", DrinkAnalytics.averageLabel(week, bars, now))
    }

    @Test
    fun `类别筛选只影响统计，不改原始记录`() {
        val filtered = DrinkAnalytics.filterByCategory(records, setOf(200L))
        val summary = DrinkAnalytics.summarize(filtered, DrinkPeriod.WEEK, now)

        assertEquals(1, summary.totalCount)
        assertEquals(200L, summary.topCategoryId)
        assertEquals("筛选是纯函数，原列表不能被改", 4, records.size)
    }

    @Test
    fun `明细按天分组且时间倒序，跨天不会串在一起`() {
        val summary = DrinkAnalytics.summarize(records, DrinkPeriod.WEEK, now)
        val grouped = DrinkAnalytics.groupByDay(summary.records)

        assertEquals(2, grouped.size)
        val (today, todayRecords) = grouped.first()
        assertEquals(DateRanges.startOfDay(at(10, 24)), today)
        assertEquals(listOf(21, 9, 8), todayRecords.map { hourOf(it.timeMillis) })
        assertEquals(1, grouped[1].second.size)
    }

    @Test
    fun `自定义区间按跨度选粒度，短区间不画 90 根柱子`() {
        val range = DateRanges.ofDays(at(10, 1), at(10, 24))
        val summary = DrinkAnalytics.summarize(
            records = records,
            period = DrinkPeriod.CUSTOM,
            now = now,
            customRange = range
        )
        val bars = DrinkAnalytics.bars(DrinkPeriod.CUSTOM, summary, now)

        assertEquals(4, summary.totalCount)
        assertTrue("24 天不该退化成一根柱子", bars.size > 1)
        assertTrue("所有次数都要落在某个桶里", bars.sumOf { it.total } == 4)
    }

    @Test
    fun `默认类别的 id 是确定的，两台设备合并后不会出现两份水`() {
        val first = DrinkDefaults.categories()
        val second = DrinkDefaults.categories()

        assertEquals(5, first.size)
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals("每类的 id 必须各不相同", 5, first.map { it.id }.distinct().size)
        assertEquals(listOf("水", "咖啡", "茶", "饮料", "维C"), first.map { it.name })
        assertEquals(listOf("#2F86D6", "#8D6748", "#5AA469", "#9B6DB5", "#E8963A"), first.map { it.colorHex })
    }

    @Test
    fun `旧设备上的酒会就地改名成维C，用户改过的名字不动`() {
        val seeded = DrinkDefaults.categories().first { it.name == "维C" }
        val legacy = seeded.copy(name = "酒", colorHex = "#C05A45")

        val renamed = DrinkDefaults.renamed(legacy)

        assertEquals("维C", renamed?.name)
        assertEquals("#E8963A", renamed?.colorHex)
        assertEquals("id 必须保持不变，否则已有记录会变成「已删除类别」", legacy.id, renamed?.id)

        // 用户自己起过名字（updatedAt 不再等于种子时间戳）时不该被改回去。
        assertNull(DrinkDefaults.renamed(legacy.copy(updatedAt = 1_700_000_000_000L)))
        // 已经是新名字的类别不需要再动。
        assertNull(DrinkDefaults.renamed(seeded))
    }

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
}

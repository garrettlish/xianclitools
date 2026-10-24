package com.example.xiancli_tools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiancli_tools.data.DateRange
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.DrinkAnalytics
import com.example.xiancli_tools.data.DrinkBar
import com.example.xiancli_tools.data.DrinkCategory
import com.example.xiancli_tools.data.DrinkPeriod
import com.example.xiancli_tools.data.DrinkSummary
import com.example.xiancli_tools.ui.theme.WarmGray
import java.util.Calendar
import kotlin.math.min

/** #RRGGBB 解析，失败时退回暖灰——坏数据不应该让界面崩。 */
fun parseHexColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(WarmGray)

/** 同色相压暗：浅色类别色直接当正文色太淡（橙 #E8963A 在白底只有 2.4:1）。 */
fun Color.shade(factor: Float = 0.82f): Color =
    Color(red * factor, green * factor, blue * factor, alpha)

/** 同色相提亮：深色主题下类别色直接压在暗底上会发闷，往白里带一点才够亮。 */
fun Color.lighten(factor: Float = 0.25f): Color =
    Color(
        red = red + (1f - red) * factor,
        green = green + (1f - green) * factor,
        blue = blue + (1f - blue) * factor,
        alpha = alpha
    )

/** 一行最多平铺几张类别卡；超过这个数才需要横向滚动。 */
private const val VisibleSlots = 5
private val countCardGap = 8.dp

/**
 * 类别色卡 + 该区间内的次数大数字。
 * 卡宽按「一行 5 张」算出来，默认五类正好铺满整行不滚动；类别再多就横向滚动。
 */
@Composable
fun DrinkCountCards(
    categories: List<DrinkCategory>,
    counts: Map<Long, Int>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = ((maxWidth - countCardGap * (VisibleSlots - 1)) / VisibleSlots)
            .coerceAtLeast(48.dp)
        val dark = isSystemInDarkTheme()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(countCardGap)
        ) {
            categories.forEach { category ->
                val color = parseHexColor(category.colorHex)
                val nameColor = if (dark) color.lighten(0.32f) else color.shade(0.68f)
                val valueColor = if (dark) color.lighten(0.18f) else color.shade(0.82f)
                val count = (counts[category.id] ?: 0).toString()
                val shape = RoundedCornerShape(14.dp)
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .clip(shape)
                        .background(color.copy(alpha = if (dark) 0.22f else 0.13f))
                        .border(1.dp, color.copy(alpha = 0.2f), shape)
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = category.name,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = count,
                        // 切到周/月/年后数字会到三四位，卡宽只有 5 等分，字号要跟着收。
                        fontSize = countFontSize(count.length),
                        lineHeight = (countFontSize(count.length).value * 1.2f).sp,
                        fontWeight = FontWeight.Bold,
                        color = valueColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private fun countFontSize(digits: Int) = when {
    digits <= 2 -> 23.sp
    digits == 3 -> 18.sp
    else -> 15.sp
}

/**
 * 堆叠柱图：按 categories 的顺序堆叠，保证同一类别在各周期里颜色与层次一致。
 *
 * 三个可读性关键点：
 * 1. 纵轴有刻度（4 等分 + 整数尺标），不必靠猜柱高；
 * 2. 轴标签在 Canvas 里排版，柱子多时按固定步长抽稀，避免「16 日」被裁成「1 6」；
 * 3. 柱子少时在柱顶标总数，今天的柱子加一块底色定位。
 */
@Composable
fun DrinkStackedChart(
    bars: List<DrinkBar>,
    categories: List<DrinkCategory>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    futureStartIndex: Int? = null,
    chartHeight: Dp = 168.dp
) {
    val colorById = remember(categories) {
        categories.associate { it.id to parseHexColor(it.colorHex) }
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) { TextStyle(fontSize = 10.sp, color = labelColor) }
    val dimLabelStyle = remember(labelColor) {
        TextStyle(fontSize = 10.sp, color = labelColor.copy(alpha = 0.38f))
    }
    val valueStyle = remember(labelColor) {
        TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = labelColor)
    }
    val emptyStyle = remember(labelColor) { TextStyle(fontSize = 12.sp, color = labelColor) }

    Canvas(modifier.fillMaxWidth().height(chartHeight)) {
        if (bars.isEmpty()) return@Canvas

        val maxTotal = bars.maxOfOrNull { it.total } ?: 0
        val step = niceStep(maxTotal)
        val axisMax = (step * 4).coerceAtLeast(1)

        if (maxTotal == 0) {
            val text = measurer.measure("这段时间还没有记录", emptyStyle)
            drawText(
                text,
                topLeft = Offset(
                    (size.width - text.size.width) / 2f,
                    (size.height - text.size.height) / 2f
                )
            )
            return@Canvas
        }

        // 轴标签先量后排版：左侧留白由最宽的数字决定，三位数的次数也不会被切掉。
        val yLabelWidth = (0..4).maxOf {
            measurer.measure((it * step).toString(), labelStyle).size.width
        }
        val plotLeft = yLabelWidth + 8.dp.toPx()
        val plotRight = size.width - 4.dp.toPx()
        val showValues = bars.size <= 12
        val plotTop = (if (showValues) 20.dp else 10.dp).toPx()
        val plotBottom = size.height - 18.dp.toPx()
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val stroke = 1.dp.toPx()

        for (i in 0..4) {
            val y = plotBottom - plotHeight * i / 4f
            drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = stroke)
            val text = measurer.measure((i * step).toString(), labelStyle)
            drawText(
                text,
                topLeft = Offset(plotLeft - 8.dp.toPx() - text.size.width, y - text.size.height / 2f)
            )
        }

        val slot = (plotRight - plotLeft) / bars.size
        val gap = min(slot * 0.3f, 10.dp.toPx())
        val maxBarWidth = (if (bars.size <= 7) 40.dp else 24.dp).toPx()
        val barWidth = (slot - gap).coerceAtMost(maxBarWidth).coerceAtLeast(2.dp.toPx())

        bars.forEachIndexed { index, bar ->
            val center = plotLeft + slot * (index + 0.5f)
            val left = center - barWidth / 2f
            // 还没到来的天/月不画柱子也不画基线：画了会被误读成「当天 0 次」。
            val future = futureStartIndex != null && index >= futureStartIndex

            if (!future && highlightIndex == index && bars.size <= 16) {
                val pad = 5.dp.toPx()
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(left - pad, plotTop),
                    size = Size(barWidth + pad * 2, plotHeight),
                    cornerRadius = CornerRadius(9.dp.toPx())
                )
            }

            if (future) {
                // 留空
            } else if (bar.total == 0) {
                // 空柱留一条基线，避免整根消失让读者以为图表缺了一格
                drawLine(
                    color = gridColor,
                    start = Offset(left, plotBottom),
                    end = Offset(left + barWidth, plotBottom),
                    strokeWidth = 2.dp.toPx()
                )
            } else {
                val barTop = plotBottom - plotHeight * bar.total / axisMax
                val radius = min(4.dp.toPx(), barWidth / 2f)
                val clip = Path().apply {
                    addRoundRect(
                        RoundRect(left, barTop, left + barWidth, plotBottom + radius, radius, radius)
                    )
                }
                clipPath(clip) {
                    var bottom = plotBottom
                    categories.forEach { category ->
                        val count = bar.segments[category.id] ?: 0
                        if (count <= 0) return@forEach
                        val segmentHeight = plotHeight * count / axisMax
                        drawRect(
                            color = colorById[category.id] ?: gridColor,
                            topLeft = Offset(left, bottom - segmentHeight),
                            size = Size(barWidth, segmentHeight)
                        )
                        bottom -= segmentHeight
                    }
                }

                if (showValues) {
                    val text = measurer.measure(bar.total.toString(), valueStyle)
                    drawText(
                        text,
                        topLeft = Offset(
                            center - text.size.width / 2f,
                            barTop - text.size.height - 3.dp.toPx()
                        )
                    )
                }
            }
        }

        axisTicks(bars.size).forEach { index ->
            val future = futureStartIndex != null && index >= futureStartIndex
            val text = measurer.measure(bars[index].label, if (future) dimLabelStyle else labelStyle)
            val center = plotLeft + slot * (index + 0.5f)
            val x = (center - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(x, plotBottom + 5.dp.toPx()))
        }
    }
}

/** 纵轴步长取 1/2/5 的整十倍，4 格刚好盖住最大值——刻度永远是整数。 */
private fun niceStep(maxTotal: Int): Int {
    val candidates = intArrayOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000)
    candidates.forEach { candidate ->
        if (candidate * 4 >= maxTotal) return candidate
    }
    return maxTotal / 4 + 1
}

/** 柱子多时抽稀轴标签：日/周/年全显，月视图每 5 天标一次，末尾必定标出。 */
private fun axisTicks(count: Int): List<Int> {
    if (count <= 12) return (0 until count).toList()
    val step = if (count <= 16) 2 else 5
    val ticks = (0 until count step step).toMutableList()
    if (ticks.last() != count - 1) ticks.add(count - 1)
    return ticks
}

// ---- 热力图 ----

private val heatCellSize: Dp = 12.dp

/** 4 档：无记录 / 低（1-2）/ 中（3-4）/ 高（5+）。 */
fun drinkHeatLevel(count: Int): Int = when {
    count <= 0 -> 0
    count <= 2 -> 1
    count <= 4 -> 2
    else -> 3
}

/** 年视图按整月聚合，数值跨度远大于单日，用当年最大值做相对分档，否则每月都顶到最高档。 */
private fun relativeLevel(count: Int, max: Int): Int = when {
    count <= 0 || max <= 0 -> 0
    count * 3 <= max -> 1
    count * 3 <= max * 2 -> 2
    else -> 3
}

@Composable
private fun heatColor(level: Int): Color {
    val primary = MaterialTheme.colorScheme.primary
    return when {
        level <= 0 -> MaterialTheme.colorScheme.surfaceVariant
        level == 1 -> primary.copy(alpha = 0.28f)
        level == 2 -> primary.copy(alpha = 0.55f)
        else -> primary.copy(alpha = 0.95f)
    }
}

/** 色阶图例：无 → 多，四种档位。 */
@Composable
private fun HeatLegend(modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("无", fontSize = 10.sp, color = labelColor)
        Spacer(Modifier.width(4.dp))
        (0..3).forEach { level ->
            Box(
                Modifier
                    .size(heatCellSize)
                    .clip(RoundedCornerShape(3.dp))
                    .background(heatColor(level))
            )
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(2.dp))
        Text("多", fontSize = 10.sp, color = labelColor)
    }
}

/** 热力图卡片标题，随周期变化。 */
fun heatTitle(period: DrinkPeriod): String = when (period) {
    DrinkPeriod.DAY -> "今天 · 逐小时"
    DrinkPeriod.WEEK -> "本周记录"
    DrinkPeriod.MONTH -> "本月记录"
    DrinkPeriod.YEAR -> "今年记录"
    DrinkPeriod.CUSTOM -> "所选区间 · 逐日"
}

/**
 * 周期热力图：按日/周/月/年/自定义切换同一套口径。
 * 统计页与记录页共用，避免两处各算一遍导致数字对不上。
 */
@Composable
fun DrinkHeatmap(
    period: DrinkPeriod,
    summary: DrinkSummary,
    now: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    when (period) {
        DrinkPeriod.DAY -> HourHeatmap(summary = summary, now = now, modifier = modifier)
        DrinkPeriod.WEEK -> WeekHeatmap(summary = summary, now = now, modifier = modifier)
        DrinkPeriod.MONTH -> DayGridHeatmap(summary = summary, now = now, modifier = modifier)
        DrinkPeriod.YEAR -> YearHeatmap(summary = summary, now = now, modifier = modifier)
        DrinkPeriod.CUSTOM -> {
            // 自定义区间可以是好几年，几千个格子既画不动也读不出结论。
            // 超过 10 周就不画热力图，把「按天分布」交给上方的柱图。
            if (summary.range.dayCount > MAX_CUSTOM_HEAT_DAYS) {
                Text(
                    text = "区间超过 ${MAX_CUSTOM_HEAT_DAYS} 天，热力图从简；按天的分布看上方柱图。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = modifier.fillMaxWidth()
                )
            } else {
                DayGridHeatmap(summary = summary, now = now, modifier = modifier)
            }
        }
    }
}

/** 带数字的格子：周视图 7 格、年视图 12 格，格子少而宽，塞得下「标签 + 次数」。 */
@Composable
private fun CountTile(
    label: String,
    count: Int,
    level: Int,
    future: Boolean,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    val shape = RoundedCornerShape(9.dp)
    val textColor = when {
        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        level >= 3 -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (future) Color.Transparent else heatColor(level))
            .then(
                if (outlined) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), shape)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 必须显式给 lineHeight：默认样式的行高 24sp 会把两行文字撑破固定高度的格子。
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = "$count",
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** 只有日号的小格子：月视图与自定义区间的日历格共用。 */
@Composable
private fun HeatTile(
    label: String,
    level: Int,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    future: Boolean = false
) {
    val shape = RoundedCornerShape(7.dp)
    val textColor = when {
        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        level >= 3 -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (future) Color.Transparent else heatColor(level))
            .then(
                if (outlined) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), shape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, color = textColor, maxLines = 1, softWrap = false)
    }
}

/** 日视图：两行十二列共 24 格，格内 = 小时，颜色 = 该小时的次数。 */
@Composable
private fun HourHeatmap(summary: DrinkSummary, now: Long, modifier: Modifier = Modifier) {
    val hourlyTotals = remember(summary.records) { DrinkAnalytics.hourlyCounts(summary.records) }
    // 不 remember：跨过整点后重新组合时，未来小时的留空状态要跟着变。
    val currentHour = hourOf(now)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until 2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until 12).forEach { column ->
                    val hour = row * 12 + column
                    HeatTile(
                        label = "$hour",
                        level = drinkHeatLevel(hourlyTotals[hour] ?: 0),
                        future = hour > currentHour,
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        HeatLegend()
    }
}

/** 周视图：一行七天，列头 = 周几，格内 = 日号 + 当天次数，颜色深浅同步。 */
@Composable
private fun WeekHeatmap(summary: DrinkSummary, now: Long, modifier: Modifier = Modifier) {
    val totals = remember(summary.records) { DrinkAnalytics.dailyCounts(summary.records) }
    val days = remember(summary.range) { daysOf(summary.range) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val todayKey = DateRanges.dayKey(now)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                Text(
                    text = weekdayLabel(day),
                    fontSize = 10.sp,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                val count = totals[DateRanges.dayKey(day)] ?: 0
                CountTile(
                    label = "${dayOfMonth(day)}日",
                    count = count,
                    level = drinkHeatLevel(count),
                    outlined = DateRanges.dayKey(day) == todayKey,
                    future = day > now,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        HeatLegend()
    }
}

/**
 * 月视图 / 自定义区间：按周排列的日历格（周一在左），格内 = 日号，颜色 = 当天次数。
 * 自定义区间跨月时不再标月份，标题里已经有起止日期，格子里只留日号才不挤。
 */
@Composable
private fun DayGridHeatmap(summary: DrinkSummary, now: Long, modifier: Modifier = Modifier) {
    val totals = remember(summary.records) { DrinkAnalytics.dailyCounts(summary.records) }
    val days = remember(summary.range) { daysOf(summary.range) }
    val weeks = remember(days) { weekdayCells(days) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val todayKey = DateRanges.dayKey(now)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                Text(
                    text = weekday,
                    fontSize = 10.sp,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .height(30.dp)
                        )
                    } else {
                        HeatTile(
                            label = "${dayOfMonth(day)}",
                            level = drinkHeatLevel(totals[DateRanges.dayKey(day)] ?: 0),
                            outlined = DateRanges.dayKey(day) == todayKey,
                            future = day > now,
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        HeatLegend()
    }
}

/**
 * 年视图：12 个月铺成 4 列 3 行，格内 = 月份 + 当月次数。
 * 一年 365 格在手机上过密且读不出结论，按月聚合才看得出「哪几个月喝得多」。
 */
@Composable
private fun YearHeatmap(summary: DrinkSummary, now: Long, modifier: Modifier = Modifier) {
    val totals = remember(summary.records) { DrinkAnalytics.monthlyCounts(summary.records) }
    val maxTotal = totals.values.maxOrNull() ?: 0
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentMonth = monthOf(now)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until 3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 4).forEach { column ->
                    val month = row * 4 + column + 1
                    val count = totals[month] ?: 0
                    CountTile(
                        label = "${month}月",
                        count = count,
                        level = relativeLevel(count, maxTotal),
                        outlined = month == currentMonth,
                        future = month > currentMonth,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("按今年最高月分档", fontSize = 10.sp, color = labelColor)
            HeatLegend(modifier = Modifier.weight(1f))
        }
    }
}

/** 区间覆盖的每一天（零点），含首尾。 */
private fun daysOf(range: DateRange): List<Long> {
    val days = mutableListOf<Long>()
    var cursor = DateRanges.startOfDay(range.startMillis)
    while (cursor < range.endMillis && days.size < MAX_DAYS) {
        days += cursor
        cursor = DateRanges.plusDays(cursor, 1)
    }
    return days
}

/** 日历网格：补齐首周空位与末行，保证每行 7 格、周一在左。 */
private fun weekdayCells(days: List<Long>): List<List<Long?>> {
    if (days.isEmpty()) return emptyList()
    val leading = (weekdayIndexMonFirst(days.first())).coerceIn(0, 6)
    val cells = List(leading) { null } + days
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    return padded.chunked(7)
}

private fun weekdayIndexMonFirst(millis: Long): Int =
    (Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK) + 5) % 7

private fun weekdayLabel(millis: Long): String {
    val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return names[weekdayIndexMonFirst(millis)]
}

private fun dayOfMonth(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

private fun monthOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MONTH) + 1

private fun hourOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

private const val MAX_DAYS = 20_000

/** 自定义区间画日格的上限：70 天正好十周，再多就该看柱图了。 */
private const val MAX_CUSTOM_HEAT_DAYS = 70

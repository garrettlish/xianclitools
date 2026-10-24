package com.example.xiancli_tools.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.BucketUnit
import com.example.xiancli_tools.data.DateRange
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.DrinkAnalytics
import com.example.xiancli_tools.data.DrinkBar
import com.example.xiancli_tools.data.DrinkCategory
import com.example.xiancli_tools.data.DrinkPeriod
import com.example.xiancli_tools.data.DrinkRecord
import com.example.xiancli_tools.data.DrinkRepository
import com.example.xiancli_tools.data.DrinkSummary
import com.example.xiancli_tools.data.Identity
import com.example.xiancli_tools.data.TrendBuckets
import com.example.xiancli_tools.ui.components.ConfirmDeleteDialog
import com.example.xiancli_tools.ui.components.CustomRangeBar
import com.example.xiancli_tools.ui.components.DrinkCountCards
import com.example.xiancli_tools.ui.components.DrinkHeatmap
import com.example.xiancli_tools.ui.components.DrinkStackedChart
import com.example.xiancli_tools.ui.components.EmptyReportCard
import com.example.xiancli_tools.ui.components.MetricTile
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.components.SegmentedTabs
import com.example.xiancli_tools.ui.components.heatTitle
import com.example.xiancli_tools.ui.components.parseHexColor
import com.example.xiancli_tools.ui.components.shade
import com.example.xiancli_tools.ui.components.showDatePicker
import com.example.xiancli_tools.ui.theme.DrinkAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 饮记页的三块内容：记录、明细与统计。 */
private enum class DrinkTab(val label: String) {
    RECORD("记录"),
    DETAIL("明细"),
    REPORT("统计")
}

private val cellHeight = 96.dp
private val cellShape = RoundedCornerShape(20.dp)

/**
 * 饮记：点一下 = 喝了一次，一天可以点很多次。
 *
 * 记录页的网格是第一优先级（三秒内点完），明细负责补记和纠错，
 * 统计负责看「这周喝了多少、哪一类占大头」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrinkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { DrinkRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(DrinkTab.RECORD) }
    var categories by remember { mutableStateOf<List<DrinkCategory>>(emptyList()) }
    var records by remember { mutableStateOf<List<DrinkRecord>>(emptyList()) }
    // 明细与统计共用同一份周期：默认当月，进门就能看到整月喝了什么。
    var period by remember { mutableStateOf(DrinkPeriod.MONTH) }
    var customRange by remember { mutableStateOf(DateRanges.lastDays()) }
    var heatPeriod by remember { mutableStateOf(DrinkPeriod.DAY) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var drilledMonth by remember { mutableStateOf<Int?>(null) }
    var editingCategory by remember { mutableStateOf<DrinkCategory?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    var showBackfill by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DrinkRecord?>(null) }

    val recordScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val reportScroll = rememberScrollState()

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                categories = repository.loadCategories()
                records = repository.loadRecords()
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    // 类别被删后，筛选里残留的 id 会让列表变空，这里静默收敛到现存类别。
    LaunchedEffect(categories) {
        val ids = categories.map { it.id }.toSet()
        if (!ids.containsAll(selected)) selected = selected.filter { it in ids }.toSet()
    }

    val now = System.currentTimeMillis()
    val todayCounts = remember(records) { DrinkAnalytics.todayCounts(records) }
    val filtered = remember(records, selected) { DrinkAnalytics.filterByCategory(records, selected) }
    val summary = remember(filtered, period, customRange) {
        DrinkAnalytics.summarize(filtered, period, now, customRange)
    }
    val bars = remember(summary) { DrinkAnalytics.bars(period, summary, now) }
    val heatSummary = remember(records, heatPeriod) {
        DrinkAnalytics.summarize(records, heatPeriod, now)
    }
    val counts = remember(summary) { summary.countsByCategory }
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    fun record(category: DrinkCategory) {
        val stamp = System.currentTimeMillis()
        val entry = DrinkRecord(id = Identity.newId(), categoryId = category.id, timeMillis = stamp)
        scope.launch {
            withContext(Dispatchers.IO) { repository.addRecord(entry) }
            categories = withContext(Dispatchers.IO) { repository.loadCategories() }
            records = withContext(Dispatchers.IO) { repository.loadRecords() }
            // 连点几下时只留最新一条提示：排队会让「刚记的那杯」迟迟看不到。
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "已记录 ${category.name} · ${clockLabel(stamp)}",
                actionLabel = "撤销"
            )
            if (result == SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) { repository.deleteRecord(entry.id) }
                records = withContext(Dispatchers.IO) { repository.loadRecords() }
            }
        }
    }

    fun deleteRecord(record: DrinkRecord) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.deleteRecord(record.id) }
            records = withContext(Dispatchers.IO) { repository.loadRecords() }
            snackbarHostState.showSnackbar("已删除")
        }
    }

    fun backfill(categoryId: Long, timeMillis: Long) {
        val entry = DrinkRecord(
            id = Identity.newId(),
            categoryId = categoryId,
            timeMillis = timeMillis
        )
        scope.launch {
            withContext(Dispatchers.IO) { repository.addRecord(entry) }
            records = withContext(Dispatchers.IO) { repository.loadRecords() }
            snackbarHostState.showSnackbar(
                "已补记 ${categoryById[categoryId]?.name ?: "记录"} · ${clockLabel(timeMillis)}"
            )
        }
    }

    fun saveCategory(name: String, colorHex: String) {
        val existing = editingCategory
        val category = existing?.copy(name = name, colorHex = colorHex)
            ?: DrinkCategory(
                id = Identity.newId(),
                name = name,
                colorHex = colorHex,
                createdMillis = System.currentTimeMillis()
            )
        scope.launch {
            withContext(Dispatchers.IO) { repository.saveCategory(category) }
            categories = withContext(Dispatchers.IO) { repository.loadCategories() }
            creatingCategory = false
            editingCategory = null
            snackbarHostState.showSnackbar(if (existing == null) "已添加「$name」" else "已更新")
        }
    }

    fun deleteCategory(category: DrinkCategory) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.deleteCategory(category.id) }
            categories = withContext(Dispatchers.IO) { repository.loadCategories() }
            records = withContext(Dispatchers.IO) { repository.loadRecords() }
            editingCategory = null
            snackbarHostState.showSnackbar("已删除「${category.name}」及其记录")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("饮记", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { creatingCategory = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = "添加类别"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SegmentedTabs(
                labels = DrinkTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { tab = DrinkTab.entries[it] },
                accent = DrinkAccent,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
            )

            when (tab) {
                DrinkTab.RECORD -> RecordTab(
                    categories = categories,
                    todayCounts = todayCounts,
                    heatPeriod = heatPeriod,
                    heatSummary = heatSummary,
                    now = now,
                    onSelectHeatPeriod = { heatPeriod = it },
                    onRecord = { record(it) },
                    onEditCategory = { editingCategory = it },
                    onAddCategory = { creatingCategory = true }
                )

                DrinkTab.DETAIL -> DetailTab(
                    scroll = detailScroll,
                    categories = categories,
                    summary = summary,
                    selected = selected,
                    period = period,
                    customRange = customRange,
                    drilledMonth = drilledMonth,
                    onSelectPeriod = {
                        period = it
                        drilledMonth = null
                    },
                    onPickRangeStart = {
                        showDatePicker(context, customRange.startMillis) { picked ->
                            customRange = DateRanges.ofDays(
                                picked,
                                maxOf(picked, customRange.lastDayMillis)
                            )
                        }
                    },
                    onPickRangeEnd = {
                        showDatePicker(context, customRange.lastDayMillis) { picked ->
                            customRange = DateRanges.ofDays(
                                minOf(picked, customRange.startMillis),
                                picked
                            )
                        }
                    },
                    onDrillMonth = { drilledMonth = it },
                    onBackToYear = { drilledMonth = null },
                    onBackfill = { showBackfill = true },
                    onDelete = { pendingDelete = it },
                    onSelectAll = { selected = emptySet() },
                    onToggleCategory = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    }
                )

                DrinkTab.REPORT -> ReportTab(
                    scroll = reportScroll,
                    categories = categories,
                    summary = summary,
                    bars = bars,
                    counts = counts,
                    selected = selected,
                    period = period,
                    customRange = customRange,
                    now = now,
                    onSelectPeriod = { period = it },
                    onPickRangeStart = {
                        showDatePicker(context, customRange.startMillis) { picked ->
                            customRange = DateRanges.ofDays(
                                picked,
                                maxOf(picked, customRange.lastDayMillis)
                            )
                        }
                    },
                    onPickRangeEnd = {
                        showDatePicker(context, customRange.lastDayMillis) { picked ->
                            customRange = DateRanges.ofDays(
                                minOf(picked, customRange.startMillis),
                                picked
                            )
                        }
                    },
                    onSelectAll = { selected = emptySet() },
                    onToggleCategory = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    }
                )
            }
        }
    }

    if (creatingCategory || editingCategory != null) {
        val initial = editingCategory
        CategoryEditorDialog(
            initial = initial,
            affectedCount = initial?.let { category ->
                records.count { it.categoryId == category.id }
            } ?: 0,
            onDismiss = {
                creatingCategory = false
                editingCategory = null
            },
            onSave = { name, colorHex -> saveCategory(name, colorHex) },
            onDelete = initial?.let { category -> { deleteCategory(category) } }
        )
    }

    if (showBackfill) {
        BackfillDialog(
            categories = categories,
            onDismiss = { showBackfill = false },
            onSave = { categoryId, timeMillis ->
                backfill(categoryId, timeMillis)
                showBackfill = false
            }
        )
    }

    pendingDelete?.let { record ->
        val name = categoryById[record.categoryId]?.name ?: "已删除类别"
        ConfirmDeleteDialog(
            title = "删除这条记录",
            message = "确定删除「$name · ${clockLabel(record.timeMillis)}」吗？删除后无法恢复。",
            onConfirm = {
                deleteRecord(record)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

// ---- 记录页 ----

@Composable
private fun RecordTab(
    categories: List<DrinkCategory>,
    todayCounts: Map<Long, Int>,
    heatPeriod: DrinkPeriod,
    heatSummary: DrinkSummary,
    now: Long,
    onSelectHeatPeriod: (DrinkPeriod) -> Unit,
    onRecord: (DrinkCategory) -> Unit,
    onEditCategory: (DrinkCategory) -> Unit,
    onAddCategory: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            RecordCell(
                category = category,
                count = todayCounts[category.id] ?: 0,
                onClick = { onRecord(category) },
                onLongClick = { onEditCategory(category) }
            )
        }
        item(key = "__add_category__") {
            AddCategoryCell(onClick = onAddCategory)
        }
        item(key = "__hint__", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "长按类别可改名、改色或删除",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
        item(key = "__heatmap__", span = { GridItemSpan(maxLineSpan) }) {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = heatTitle(heatPeriod),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CompactPeriodTabs(selected = heatPeriod, onSelect = onSelectHeatPeriod)
                }
                Spacer(Modifier.height(12.dp))
                DrinkHeatmap(period = heatPeriod, summary = heatSummary, now = now)
            }
        }
    }
}

/**
 * 一次点击 = 一次记录的主按钮。
 * 实色块比浅色卡片更像「按下去会有事发生」；底色由浅到深的渐变负责把白字压出对比度。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordCell(
    category: DrinkCategory,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val base = parseHexColor(category.colorHex)
    val deep = base.shade(0.84f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "drinkCellScale"
    )

    Column(
        modifier = Modifier
            .height(cellHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(cellShape)
            .background(Brush.verticalGradient(listOf(base, deep)))
            .border(1.dp, deep.copy(alpha = 0.55f), cellShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "记录一次${category.name}",
                onLongClickLabel = "编辑${category.name}类别",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                onLongClick = onLongClick
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = category.name,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun AddCategoryCell(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .height(cellHeight)
            .clip(cellShape)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(
                        width = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 5.dp.toPx())
                        )
                    ),
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "新类别",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 热力图卡片右上角的小切换器：这一块是次要内容，控件比顶部的模式切换小一号。 */
@Composable
private fun CompactPeriodTabs(
    selected: DrinkPeriod,
    onSelect: (DrinkPeriod) -> Unit
) {
    val periods = remember { DrinkPeriod.entries.filter { it != DrinkPeriod.CUSTOM } }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        periods.forEach { period ->
            val active = period == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.label,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---- 明细页 ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailTab(
    scroll: ScrollState,
    categories: List<DrinkCategory>,
    summary: DrinkSummary,
    selected: Set<Long>,
    period: DrinkPeriod,
    customRange: DateRange,
    drilledMonth: Int?,
    onSelectPeriod: (DrinkPeriod) -> Unit,
    onPickRangeStart: () -> Unit,
    onPickRangeEnd: () -> Unit,
    onDrillMonth: (Int) -> Unit,
    onBackToYear: () -> Unit,
    onBackfill: () -> Unit,
    onDelete: (DrinkRecord) -> Unit,
    onSelectAll: () -> Unit,
    onToggleCategory: (Long) -> Unit
) {
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val month = drilledMonth
    val shown = remember(summary.records, period, month) {
        if (period == DrinkPeriod.YEAR && month != null) {
            summary.records.filter { monthOf(it.timeMillis) == month }
        } else {
            summary.records
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        DrinkPeriodBar(
            period = period,
            onSelect = onSelectPeriod,
            customRange = customRange,
            onPickStart = onPickRangeStart,
            onPickEnd = onPickRangeEnd
        )
        Spacer(Modifier.height(10.dp))
        DrinkCategoryChips(
            categories = categories,
            selected = selected,
            onSelectAll = onSelectAll,
            onToggle = onToggleCategory
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBackfill,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("补记一条")
        }
        Spacer(Modifier.height(12.dp))

        if (shown.isEmpty()) {
            EmptyReportCard(
                icon = R.drawable.ic_tool_drink,
                text = if (selected.isEmpty()) {
                    "${summary.rangeName}还没有记录，回记录页点一下就行"
                } else {
                    "${summary.rangeName}筛选的类别还没有记录"
                },
                accent = DrinkAccent
            )
            return@Column
        }

        if (period == DrinkPeriod.YEAR && month == null) {
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DrinkAnalytics.groupByMonth(shown).forEach { (monthValue, count) ->
                    MonthIndexRow(
                        month = monthValue,
                        count = count,
                        onClick = { onDrillMonth(monthValue) }
                    )
                }
            }
        } else {
            if (month != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onBackToYear,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("← 全年", fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${month}月",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DrinkAnalytics.groupByDay(shown).forEach { (dayStart, dayRecords) ->
                    DayGroupCard(
                        dayStart = dayStart,
                        records = dayRecords,
                        categoryById = categoryById,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

/** 同一天的记录收进一张卡片，标题右侧给当天次数，行间用发丝线分隔。 */
@Composable
private fun DayGroupCard(
    dayStart: Long,
    records: List<DrinkRecord>,
    categoryById: Map<Long, DrinkCategory>,
    onDelete: (DrinkRecord) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateRanges.formatDayHeading(dayStart),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${records.size} 次",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        records.forEachIndexed { index, record ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
            }
            val category = categoryById[record.categoryId]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(category?.colorHex ?: "#9C948A"))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = category?.name ?: "已删除类别",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = clockLabel(record.timeMillis),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { onDelete(record) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** 年视图的月份索引行：月份 + 次数 + 箭头，整行可点。 */
@Composable
private fun MonthIndexRow(month: Int, count: Int, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${month}月",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$count 次",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ---- 统计页 ----

@Composable
private fun ReportTab(
    scroll: ScrollState,
    categories: List<DrinkCategory>,
    summary: DrinkSummary,
    bars: List<DrinkBar>,
    counts: Map<Long, Int>,
    selected: Set<Long>,
    period: DrinkPeriod,
    customRange: DateRange,
    now: Long,
    onSelectPeriod: (DrinkPeriod) -> Unit,
    onPickRangeStart: () -> Unit,
    onPickRangeEnd: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleCategory: (Long) -> Unit
) {
    val visibleCategories = remember(categories, selected) {
        if (selected.isEmpty()) categories else categories.filter { it.id in selected }
    }
    val topCategory = summary.topCategoryId?.let { id ->
        categories.firstOrNull { it.id == id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DrinkPeriodBar(
            period = period,
            onSelect = onSelectPeriod,
            customRange = customRange,
            onPickStart = onPickRangeStart,
            onPickEnd = onPickRangeEnd
        )
        Text(
            text = DateRanges.formatHeading(summary.range),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        SectionCard(title = "${summary.rangeName}汇总") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${summary.totalCount}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DrinkAccent
                )
                Text(
                    text = " 次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "有记录",
                    value = "${summary.activeDays} 天",
                    accent = DrinkAccent,
                    centered = true
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = if (period == DrinkPeriod.YEAR) "月均" else "日均",
                    value = averagePerUnitText(summary, now),
                    accent = DrinkAccent,
                    centered = true
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "最多",
                    value = topCategory?.name ?: "—",
                    accent = topCategory?.let { parseHexColor(it.colorHex) } ?: DrinkAccent,
                    centered = true
                )
            }
        }

        SectionCard(title = "各类次数") {
            if (visibleCategories.isEmpty()) {
                Text(
                    text = "没有可显示的类别",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                DrinkCountCards(categories = visibleCategories, counts = counts)
            }
            Spacer(Modifier.height(12.dp))
            DrinkCategoryChips(
                categories = categories,
                selected = selected,
                onSelectAll = onSelectAll,
                onToggle = onToggleCategory
            )
        }

        SectionCard(title = chartTitle(period, summary)) {
            val average = DrinkAnalytics.averageLabel(summary, bars, now)
            if (average != null) {
                Text(
                    text = average,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            DrinkStackedChart(
                bars = bars,
                categories = categories,
                highlightIndex = DrinkAnalytics.highlightIndex(period, summary.range, now),
                futureStartIndex = DrinkAnalytics.futureStartIndex(period, summary.range, now)
            )
        }

        SectionCard(title = heatTitle(period)) {
            DrinkHeatmap(period = period, summary = summary, now = now)
        }
    }
}

/** 筛选 chips：单行横向滚动不换行，类别多了也不会把下面的图表推低。 */
@Composable
private fun DrinkCategoryChips(
    categories: List<DrinkCategory>,
    selected: Set<Long>,
    onSelectAll: () -> Unit,
    onToggle: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DrinkChip(label = "全部", color = null, active = selected.isEmpty(), onClick = onSelectAll)
        categories.forEach { category ->
            DrinkChip(
                label = category.name,
                color = parseHexColor(category.colorHex),
                active = category.id in selected,
                onClick = { onToggle(category.id) }
            )
        }
    }
}

@Composable
private fun DrinkChip(
    label: String,
    color: Color?,
    active: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(17.dp)
    val accent = color ?: DrinkAccent
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) accent.copy(alpha = 0.42f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (color != null) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = label,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun chartTitle(period: DrinkPeriod, summary: DrinkSummary): String = when (period) {
    DrinkPeriod.DAY -> "今天 · 按时段"
    DrinkPeriod.WEEK -> "本周 · 每天"
    DrinkPeriod.MONTH -> "本月 · 每天"
    DrinkPeriod.YEAR -> "今年 · 每月"
    DrinkPeriod.CUSTOM -> {
        val unit = when (TrendBuckets.plan(summary.range).unit) {
            BucketUnit.HOUR -> "小时"
            BucketUnit.DAY -> "每天"
            BucketUnit.WEEK -> "每周"
            BucketUnit.MONTH -> "每月"
        }
        "所选区间 · $unit"
    }
}

private fun averagePerUnitText(summary: DrinkSummary, now: Long): String {
    val units = DrinkAnalytics.elapsedUnits(summary, now).coerceAtLeast(1)
    val value = summary.totalCount.toFloat() / units
    return String.format(Locale.US, "%.1f", value)
}

// ---- 周期切换 ----

/** 「日 / 周 / 月 / 年 / 自定义」：明细与统计两个标签共用同一份选择。 */
@Composable
private fun DrinkPeriodBar(
    period: DrinkPeriod,
    onSelect: (DrinkPeriod) -> Unit,
    customRange: DateRange,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    SegmentedTabs(
        labels = DrinkPeriod.entries.map { it.label },
        selectedIndex = period.ordinal,
        onSelect = { onSelect(DrinkPeriod.entries[it]) }
    )
    if (period == DrinkPeriod.CUSTOM) {
        Spacer(Modifier.height(8.dp))
        CustomRangeBar(
            range = customRange,
            accent = DrinkAccent,
            onPickStart = onPickStart,
            onPickEnd = onPickEnd
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "共 ${customRange.dayCount} 天",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---- 弹窗 ----

/** 新建 / 编辑类别时可选的颜色。前五个是默认类别色，其余为扩展备选。 */
private val categoryPalette = listOf(
    "#2F86D6", "#8D6748", "#5AA469", "#9B6DB5", "#E8963A",
    "#C05A45", "#3FA9A0", "#C2A83E", "#6C7A89", "#B0563F"
)

/** 每行 5 个正好排满两行，10 个颜色不会出现「第二行剩 4 个」的参差。 */
private const val SwatchesPerRow = 5

@Composable
private fun CategoryEditorDialog(
    initial: DrinkCategory?,
    affectedCount: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var colorHex by remember(initial?.id) {
        mutableStateOf(initial?.colorHex ?: categoryPalette.first())
    }
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete && initial != null && onDelete != null) {
        ConfirmDeleteDialog(
            title = "删除「${initial.name}」？",
            message = if (affectedCount > 0) {
                "该类别下的 $affectedCount 条记录会一并删除，无法撤销。"
            } else {
                "该类别下还没有记录。"
            },
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = if (initial == null) "新建类别" else "编辑类别",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(8) },
                    label = { Text("类别名") },
                    placeholder = { Text("例如：奶茶", fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "颜色",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                categoryPalette.chunked(SwatchesPerRow).forEachIndexed { rowIndex, row ->
                    if (rowIndex > 0) Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { hex ->
                            ColorSwatch(
                                hex = hex,
                                selected = hex.equals(colorHex, ignoreCase = true),
                                onClick = { colorHex = hex }
                            )
                        }
                    }
                }
                if (initial != null && onDelete != null) {
                    Spacer(Modifier.height(14.dp))
                    TextButton(
                        onClick = { confirmingDelete = true },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    ) {
                        Text("删除类别", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), colorHex) },
                enabled = name.isNotBlank()
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 选中态用「外圈 + 留白 + 实心色点」表达，单贴一圈黑边会和色点糊在一起。 */
@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 26.dp else 30.dp)
                .clip(CircleShape)
                .background(parseHexColor(hex))
        )
    }
}

/** 补记：选类别 → 日期 / 时间（默认现在）→ 保存。 */
@Composable
private fun BackfillDialog(
    categories: List<DrinkCategory>,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long, timeMillis: Long) -> Unit
) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var timeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun pickTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        timeMillis = calendar.timeInMillis
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补记一条", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        DrinkChip(
                            label = category.name,
                            color = parseHexColor(category.colorHex),
                            active = category.id == selectedId,
                            onClick = { selectedId = category.id }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { pickTime() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_time),
                        contentDescription = null,
                        tint = DrinkAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stampLabel(timeMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "改时间",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedId?.let { onSave(it, timeMillis) } },
                enabled = selectedId != null
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun clockLabel(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))

private fun stampLabel(millis: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))

private fun monthOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MONTH) + 1

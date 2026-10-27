package com.example.xiancli_tools.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.DateRange
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.Identity
import com.example.xiancli_tools.data.LedgerAnalytics
import com.example.xiancli_tools.data.LedgerPeriod
import com.example.xiancli_tools.data.LedgerRepository
import com.example.xiancli_tools.data.LedgerSummary
import com.example.xiancli_tools.data.TrendBucket
import com.example.xiancli_tools.ui.components.BarChart
import com.example.xiancli_tools.ui.components.BarDatum
import com.example.xiancli_tools.ui.components.ChartSlice
import com.example.xiancli_tools.ui.components.ConfirmDeleteDialog
import com.example.xiancli_tools.ui.components.CustomRangeBar
import com.example.xiancli_tools.ui.components.DonutChart
import com.example.xiancli_tools.ui.components.EmptyReportCard
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.MetricTile
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.components.SegmentedTabs
import com.example.xiancli_tools.ui.components.showDatePicker
import com.example.xiancli_tools.ui.theme.LedgerAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 账本页的三块内容：记账、明细与统计。 */
private enum class LedgerTab(val label: String) {
    QUICK("记账"),
    DETAIL("明细"),
    REPORT("统计")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onBack: () -> Unit,
    /** 标签由外层记住：回首页转一圈再进来，还停在原来那一块。 */
    initialTabOrdinal: Int = LedgerTab.QUICK.ordinal,
    onTabChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { LedgerRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember {
        mutableStateOf(LedgerTab.entries.getOrElse(initialTabOrdinal) { LedgerTab.QUICK })
    }
    // 切标签统一走这里，顺手把序号报给外层，避免内外两份状态对不上。
    val selectTab: (LedgerTab) -> Unit = {
        tab = it
        onTabChanged(it.ordinal)
    }
    var records by remember { mutableStateOf<List<ExpenseRecord>>(emptyList()) }
    // 统计默认落在当月（而不是今天），进门就能看到整月的账目。
    var period by remember { mutableStateOf(LedgerPeriod.MONTH) }
    var customRange by remember { mutableStateOf(DateRanges.lastDays()) }
    var amountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var note by remember { mutableStateOf("") }
    var entryTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingDelete by remember { mutableStateOf<ExpenseRecord?>(null) }

    val quickScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val reportScroll = rememberScrollState()

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) { repository.loadRecords() }
    }

    val summary = remember(records, period, customRange) {
        LedgerAnalytics.summarize(records, period, customRange = customRange)
    }
    val trend = remember(summary) { LedgerAnalytics.trend(summary) }

    fun save() {
        val cents = LedgerAnalytics.parseAmountToCents(amountInput)
        if (cents == null || cents <= 0L) {
            scope.launch { snackbarHostState.showSnackbar("请输入有效金额") }
            return
        }
        val record = ExpenseRecord(
            id = Identity.newId(),
            category = selectedCategory,
            amountCents = cents,
            note = note.trim(),
            timeMillis = entryTime
        )
        scope.launch {
            withContext(Dispatchers.IO) { repository.addRecord(record) }
            records = withContext(Dispatchers.IO) { repository.loadRecords() }
            amountInput = ""
            note = ""
            entryTime = System.currentTimeMillis()
            snackbarHostState.showSnackbar("已记账 ${LedgerAnalytics.formatYuan(cents)}")
        }
    }

    fun delete(record: ExpenseRecord) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.deleteRecord(record.id) }
            records = withContext(Dispatchers.IO) { repository.loadRecords() }
            snackbarHostState.showSnackbar("已删除")
        }
    }

    fun pickTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = entryTime }
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
                        entryTime = calendar.timeInMillis
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

    fun pickRangeStart() {
        showDatePicker(context, customRange.startMillis) { picked ->
            customRange = DateRanges.ofDays(picked, maxOf(picked, customRange.lastDayMillis))
        }
    }

    fun pickRangeEnd() {
        showDatePicker(context, customRange.lastDayMillis) { picked ->
            customRange = DateRanges.ofDays(minOf(picked, customRange.startMillis), picked)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账本", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
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
                labels = LedgerTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { selectTab(LedgerTab.entries[it]) },
                accent = LedgerAccent,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
            )

            when (tab) {
                LedgerTab.QUICK -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(quickScroll)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    QuickAddCard(
                        amount = amountInput,
                        onAmountChange = { amountInput = it },
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        note = note,
                        onNoteChange = { note = it },
                        entryTime = entryTime,
                        onPickTime = { pickTime() },
                        onSave = { save() }
                    )
                }

                LedgerTab.DETAIL -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(detailScroll)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    PeriodSelector(
                        period = period,
                        onSelect = { period = it },
                        customRange = customRange,
                        onPickStart = { pickRangeStart() },
                        onPickEnd = { pickRangeEnd() }
                    )
                    Spacer(Modifier.height(12.dp))
                    LedgerDetailList(
                        summary = summary,
                        onDelete = { pendingDelete = it }
                    )
                }

                LedgerTab.REPORT -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(reportScroll)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    PeriodSelector(
                        period = period,
                        onSelect = { period = it },
                        customRange = customRange,
                        onPickStart = { pickRangeStart() },
                        onPickEnd = { pickRangeEnd() }
                    )
                    Spacer(Modifier.height(12.dp))
                    ReportContent(summary = summary, trend = trend)
                }
            }
        }
    }

    pendingDelete?.let { record ->
        val label = record.category.label +
            if (record.note.isNotEmpty()) " · ${record.note.take(20)}" else ""
        val amount = LedgerAnalytics.formatYuan(record.amountCents)
        ConfirmDeleteDialog(
            title = "删除账目",
            message = "确定删除「$label $amount」吗？删除后无法恢复。",
            onConfirm = {
                delete(record)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun QuickAddCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    selectedCategory: ExpenseCategory,
    onSelectCategory: (ExpenseCategory) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    entryTime: Long,
    onPickTime: () -> Unit,
    onSave: () -> Unit
) {
    SectionCard {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("金额") },
            prefix = { Text("¥") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(12.dp))
        CategoryGrid(selected = selectedCategory, onSelect = onSelectCategory)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注（可选）") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onPickTime)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_time),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatEntryTime(entryTime),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = LedgerAccent)
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    selected: ExpenseCategory,
    onSelect: (ExpenseCategory) -> Unit
) {
    val columns = 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseCategory.entries.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = category == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(category) }
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: ExpenseCategory,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = category.accent
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 「日 / 周 / 月 / 年 / 自定义」周期切换：明细与统计两个标签共用同一份选择。 */
@Composable
private fun PeriodSelector(
    period: LedgerPeriod,
    onSelect: (LedgerPeriod) -> Unit,
    customRange: DateRange,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    SegmentedTabs(
        labels = LedgerPeriod.entries.map { it.label },
        selectedIndex = period.ordinal,
        onSelect = { onSelect(LedgerPeriod.entries[it]) }
    )
    if (period == LedgerPeriod.CUSTOM) {
        Spacer(Modifier.height(8.dp))
        CustomRangeBar(
            range = customRange,
            accent = LedgerAccent,
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

@Composable
private fun ReportContent(
    summary: LedgerSummary,
    trend: List<TrendBucket>
) {
    if (summary.records.isEmpty()) {
        EmptyReportCard(
            icon = R.drawable.ic_tool_ledger,
            text = "${summary.rangeName}还没有记账记录",
            accent = LedgerAccent
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalsCard(summary)
        CategoryBreakdownCard(summary)
        TrendCard(summary, trend)
    }
}

@Composable
private fun TotalsCard(summary: LedgerSummary) {
    SectionCard(title = "${summary.rangeName}汇总") {
        Text(
            text = LedgerAnalytics.formatDateHeading(summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = LedgerAnalytics.formatYuan(summary.totalCents),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = LedgerAccent
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "笔数",
                value = "${summary.recordCount} 笔",
                accent = LedgerAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "单笔均",
                value = LedgerAnalytics.formatYuan(summary.averageCents),
                accent = LedgerAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "最大单笔",
                value = LedgerAnalytics.formatYuan(summary.maxRecordCents),
                accent = LedgerAccent
            )
        }
    }
}

@Composable
private fun TrendCard(
    summary: LedgerSummary,
    trend: List<TrendBucket>
) {
    SectionCard(title = "支出趋势") {
        BarChart(
            bars = trend.map { BarDatum(it.label, it.amountCents.toFloat()) },
            color = LedgerAccent
        )
        Spacer(Modifier.height(8.dp))
        val peak = summary.records.maxByOrNull { it.amountCents }
        if (peak != null) {
            Text(
                text = "最大支出：${peak.category.label} " +
                    "${LedgerAnalytics.formatYuan(peak.amountCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryBreakdownCard(summary: LedgerSummary) {
    val categories = summary.categorySummaries
    val maxAmount = categories.maxOfOrNull { it.amountCents }?.coerceAtLeast(1L) ?: 1L
    val total = summary.totalCents.coerceAtLeast(1L)

    SectionCard(title = "分类占比") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = categories.map {
                    ChartSlice(it.amountCents.toFloat(), it.category.accent)
                },
                modifier = Modifier.size(120.dp),
                strokeWidth = 20.dp,
                center = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = LedgerAnalytics.formatYuan(summary.totalCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LedgerAccent
                        )
                        Text(
                            text = "总支出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                categories.forEach { entry ->
                    val share = (entry.amountCents.toFloat() / total * 100).roundToInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(entry.category.accent, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$share%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        categories.forEach { entry ->
            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(entry.category.iconRes),
                        contentDescription = null,
                        tint = entry.category.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.category.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = LedgerAnalytics.formatYuan(entry.amountCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = entry.category.accent
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                (entry.amountCents.toFloat() / maxAmount).coerceIn(0.02f, 1f)
                            )
                            .height(6.dp)
                            .background(entry.category.accent, RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${entry.count} 笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LedgerDetailList(
    summary: LedgerSummary,
    onDelete: (ExpenseRecord) -> Unit
) {
    if (summary.records.isEmpty()) {
        EmptyReportCard(
            icon = R.drawable.ic_tool_ledger,
            text = "${summary.rangeName}还没有记账记录",
            accent = LedgerAccent
        )
        return
    }

    // 明细按自然日分组，标题右侧给出当天的合计，不用自己一条条加。
    val grouped = summary.records.groupBy { DateRanges.dayKey(it.timeMillis) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (_, dayRecords) ->
            val heading = DateRanges.formatDayHeading(dayRecords.first().timeMillis)
            val dayTotal = dayRecords.sumOf { it.amountCents }
            SectionCard(title = "$heading · ${LedgerAnalytics.formatYuan(dayTotal)}") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayRecords.forEach { record ->
                        RecordRow(
                            record = record,
                            timeText = formatClock(record.timeMillis),
                            onDelete = { onDelete(record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(
    record: ExpenseRecord,
    timeText: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            icon = record.category.iconRes,
            tint = record.category.accent,
            container = record.category.accent.copy(alpha = 0.14f),
            size = 40.dp,
            iconSize = 22.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.category.label +
                    if (record.note.isNotEmpty()) " · ${record.note}" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = LedgerAnalytics.formatYuan(record.amountCents),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatEntryTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))

/** 明细按天分组后，行内只需要时分——日期已经写在分组标题上了。 */
private fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))

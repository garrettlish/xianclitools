package com.example.xiancli_tools.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.LedgerAnalytics
import com.example.xiancli_tools.data.LedgerPeriod
import com.example.xiancli_tools.data.LedgerRepository
import com.example.xiancli_tools.data.LedgerSummary
import com.example.xiancli_tools.data.TrendBucket
import com.example.xiancli_tools.ui.components.BarChart
import com.example.xiancli_tools.ui.components.BarDatum
import com.example.xiancli_tools.ui.components.ChartSlice
import com.example.xiancli_tools.ui.components.DonutChart
import com.example.xiancli_tools.ui.components.IconBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val LedgerAccent = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { LedgerRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var records by remember { mutableStateOf<List<ExpenseRecord>>(emptyList()) }
    var period by remember { mutableStateOf(LedgerPeriod.DAY) }
    var amountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var note by remember { mutableStateOf("") }
    var entryTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) { repository.loadRecords() }
    }

    val summary = remember(records, period) { LedgerAnalytics.summarize(records, period) }
    val trend = remember(summary) { LedgerAnalytics.trend(summary) }

    fun save() {
        val cents = LedgerAnalytics.parseAmountToCents(amountInput)
        if (cents == null || cents <= 0L) {
            scope.launch { snackbarHostState.showSnackbar("请输入有效金额") }
            return
        }
        val record = ExpenseRecord(
            id = System.currentTimeMillis(),
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记账本", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(20.dp))
            Text(
                text = "统计报告",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            PeriodSelector(selected = period, onSelect = { period = it })
            Spacer(Modifier.height(12.dp))
            ReportContent(
                summary = summary,
                trend = trend,
                onDelete = { delete(it) }
            )
        }
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
    SectionCard(title = "快速记账") {
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
                        MaterialTheme.colorScheme.surface,
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
            else MaterialTheme.colorScheme.surface
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

@Composable
private fun PeriodSelector(
    selected: LedgerPeriod,
    onSelect: (LedgerPeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(14.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LedgerPeriod.entries.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReportContent(
    summary: LedgerSummary,
    trend: List<TrendBucket>,
    onDelete: (ExpenseRecord) -> Unit
) {
    if (summary.records.isEmpty()) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconBadge(
                    icon = R.drawable.ic_tool_ledger,
                    tint = LedgerAccent,
                    container = LedgerAccent.copy(alpha = 0.14f),
                    size = 56.dp,
                    iconSize = 28.dp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "本${summary.period.label}还没有记账记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalsCard(summary)
        TrendCard(summary, trend)
        CategoryBreakdownCard(summary)
        RecordsCard(summary, onDelete)
    }
}

@Composable
private fun TotalsCard(summary: LedgerSummary) {
    SectionCard(title = "本${summary.period.label}汇总") {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportMetric(
                modifier = Modifier.weight(1f),
                label = "笔数",
                value = "${summary.recordCount} 笔"
            )
            ReportMetric(
                modifier = Modifier.weight(1f),
                label = "单笔均",
                value = LedgerAnalytics.formatYuan(summary.averageCents)
            )
            ReportMetric(
                modifier = Modifier.weight(1f),
                label = "最大单笔",
                value = LedgerAnalytics.formatYuan(summary.maxRecordCents)
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
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
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
private fun RecordsCard(
    summary: LedgerSummary,
    onDelete: (ExpenseRecord) -> Unit
) {
    SectionCard(title = "账目明细") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            summary.records.forEach { record ->
                RecordRow(
                    record = record,
                    period = summary.period,
                    onDelete = { onDelete(record) }
                )
            }
        }
    }
}

@Composable
private fun RecordRow(
    record: ExpenseRecord,
    period: LedgerPeriod,
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
                text = formatRecordTime(record.timeMillis, period),
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

@Composable
private fun ReportMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = LedgerAccent
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun formatEntryTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))

private fun formatRecordTime(millis: Long, period: LedgerPeriod): String {
    val pattern = when (period) {
        LedgerPeriod.DAY -> "HH:mm"
        LedgerPeriod.WEEK -> "E HH:mm"
        LedgerPeriod.MONTH -> "MM-dd HH:mm"
        LedgerPeriod.YEAR -> "MM-dd HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

package com.example.xiancli_tools.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.AppCategory
import com.example.xiancli_tools.data.CategoryCount
import com.example.xiancli_tools.data.PhoneStats
import com.example.xiancli_tools.data.PhoneStatsRepository
import com.example.xiancli_tools.ui.components.ChartSlice
import com.example.xiancli_tools.ui.components.DonutChart
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.components.StackedBar
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.StatsAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val categoryColors = listOf(
    Color(0xFF2F86D6),
    Color(0xFF9B6DB5),
    Color(0xFF5AA469),
    Color(0xFFE8963A),
    Color(0xFFC05A45),
    Color(0xFF4E9BA0),
    Color(0xFFC4677F),
    Color(0xFF7E9B5C),
    Color(0xFF6C7BB0),
    Color(0xFF9C948A)
)

private val undefinedColor = Color(0xFF9C948A)

private fun colorFor(category: AppCategory): Color =
    if (category == AppCategory.UNDEFINED) {
        undefinedColor
    } else {
        categoryColors[category.ordinal % categoryColors.size]
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<PhoneStats?>(null) }

    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) { PhoneStatsRepository.load(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本机", fontWeight = FontWeight.Bold) },
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
        }
    ) { padding ->
        val current = stats
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                OverviewGrid(current)
                CategorySection(current.categories, current.launchableApps)
                StorageSection(current)
                MemorySection(current)
            }
        }
    }
}

@Composable
private fun OverviewGrid(stats: PhoneStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_apps,
                label = "应用总数",
                value = "${stats.totalApps}",
                color = StatsAccent
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_phone_android,
                label = "第三方应用",
                value = "${stats.userApps}",
                color = TrackAccent
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_settings,
                label = "系统应用",
                value = "${stats.systemApps}",
                color = TimerAccent
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_pie_chart,
                label = "可启动应用",
                value = "${stats.launchableApps}",
                color = NoteAccent
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: Int,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = icon,
                tint = color,
                container = color.copy(alpha = 0.14f),
                size = 44.dp,
                iconSize = 22.dp
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategorySection(categories: List<CategoryCount>, totalApps: Int) {
    SectionCard(title = "应用分类") {
        Row(verticalAlignment = Alignment.Top) {
            DonutChart(
                slices = categories.map {
                    ChartSlice(it.count.toFloat(), colorFor(it.category))
                },
                modifier = Modifier.size(132.dp),
                strokeWidth = 22.dp,
                center = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalApps",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "个可启动应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(colorFor(entry.category), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${entry.count}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSection(stats: PhoneStats) {
    val total = stats.storageTotalBytes
    val used = stats.storageUsedBytes
    val free = (total - used).coerceAtLeast(0L)
    val usedColor = MaterialTheme.colorScheme.primary
    val freeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val ratio = if (total > 0) used.toFloat() / total else 0f

    SectionCard(title = "存储使用") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = listOf(ChartSlice(used.toFloat(), usedColor)),
                trackColor = freeColor,
                modifier = Modifier.size(132.dp),
                strokeWidth = 22.dp,
                center = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(ratio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = usedColor
                        )
                        Text(
                            text = "已使用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StorageRow("已用", formatBytes(used))
                StorageRow("可用", formatBytes(free))
                StorageRow("总容量", formatBytes(total))
            }
        }
        Spacer(Modifier.height(14.dp))
        StackedBar(
            segments = listOf(
                ChartSlice(used.toFloat(), usedColor),
                ChartSlice(free.toFloat(), freeColor)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MemorySection(stats: PhoneStats) {
    val total = stats.memoryTotalBytes
    val used = stats.memoryUsedBytes
    val free = (total - used).coerceAtLeast(0L)
    val usedColor = NoteAccent
    val freeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val ratio = if (total > 0) used.toFloat() / total else 0f

    SectionCard(title = "运行内存") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = R.drawable.ic_memory,
                tint = usedColor,
                container = usedColor.copy(alpha = 0.14f),
                size = 44.dp,
                iconSize = 22.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${formatBytes(used)} / ${formatBytes(total)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "可用 ${formatBytes(free)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${(ratio * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = usedColor
            )
        }
        Spacer(Modifier.height(14.dp))
        StackedBar(
            segments = listOf(
                ChartSlice(used.toFloat(), usedColor),
                ChartSlice(free.toFloat(), freeColor)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) {
        "$bytes ${units[index]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

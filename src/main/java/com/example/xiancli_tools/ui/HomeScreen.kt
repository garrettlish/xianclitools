package com.example.xiancli_tools.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.ui.components.IconBadge

private val TimerAccent = Color(0xFF4C6FFF)
private val LedgerAccent = Color(0xFFF59E0B)
private val TrackAccent = Color(0xFF00897B)
private val StatsAccent = Color(0xFF34A853)

@Composable
fun HomeScreen(
    onOpenTimer: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenTrack: () -> Unit,
    onOpenPhoneStats: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "工具列表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                title = "定时器",
                subtitle = "10 分钟 · 25 分钟 · 50 分钟 · 自定义",
                icon = R.drawable.ic_tool_timer,
                accent = TimerAccent,
                onClick = onOpenTimer
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                title = "记账本",
                subtitle = "按类型快速记账，日月周年支出图表报告",
                icon = R.drawable.ic_tool_ledger,
                accent = LedgerAccent,
                onClick = onOpenLedger
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                title = "轨迹记录",
                subtitle = "步行 · 骑车 · 开车，日月周年统计与轨迹地图",
                icon = R.drawable.ic_tool_route,
                accent = TrackAccent,
                onClick = onOpenTrack
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                title = "手机统计",
                subtitle = "应用数量 · 分类 · 存储与内存占用",
                icon = R.drawable.ic_pie_chart,
                accent = StatsAccent,
                onClick = onOpenPhoneStats
            )
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: Int,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconBadge(
                icon = icon,
                tint = accent,
                container = accent.copy(alpha = 0.14f),
                size = 60.dp,
                iconSize = 30.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

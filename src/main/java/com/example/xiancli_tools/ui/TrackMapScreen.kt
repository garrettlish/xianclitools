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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.TrackAnalytics
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.data.TransportMode
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.MapTrack
import com.example.xiancli_tools.ui.components.TrackMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMapScreen(
    sessions: List<TrackSession>,
    onBack: () -> Unit
) {
    val tracks = remember(sessions) {
        sessions.filter { it.points.isNotEmpty() }
            .map { MapTrack(it.mode, it.points) }
    }
    val modes = remember(sessions) {
        sessions.map { it.mode }.distinct().sortedBy { it.ordinal }
    }
    val totalDistance = remember(sessions) { sessions.sumOf { it.distanceMeters } }
    val totalDuration = remember(sessions) { sessions.sumOf { it.durationMillis } }
    val title = if (sessions.size == 1) "轨迹地图" else "轨迹地图（${sessions.size} 条）"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
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
        }
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
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1720)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                TrackMap(
                    tracks = tracks,
                    colorOf = { it.accent },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "双指缩放、拖动平移；绿色圈为起点，红色圈为终点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
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
                        text = "汇总",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MapStat(
                            modifier = Modifier.weight(1f),
                            label = "总里程",
                            value = TrackAnalytics.formatDistance(totalDistance)
                        )
                        MapStat(
                            modifier = Modifier.weight(1f),
                            label = "总时长",
                            value = TrackAnalytics.formatDuration(totalDuration)
                        )
                        MapStat(
                            modifier = Modifier.weight(1f),
                            label = "记录次数",
                            value = "${sessions.size} 次"
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (modes.isNotEmpty()) {
                        Text(
                            text = "交通方式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            modes.forEach { mode ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(mode.accent, CircleShape)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sessions.forEach { session ->
                    SessionSummaryRow(session)
                }
            }
        }
    }
}

@Composable
private fun MapStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SessionSummaryRow(session: TrackSession) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = session.mode.iconRes,
                tint = session.mode.accent,
                container = session.mode.accent.copy(alpha = 0.14f),
                size = 44.dp,
                iconSize = 22.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${session.mode.label} · ${formatRange(session)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${TrackAnalytics.formatDistance(session.distanceMeters)} · " +
                        TrackAnalytics.formatDuration(session.durationMillis) +
                        " · ${session.points.size} 个点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatRange(session: TrackSession): String {
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val start = formatter.format(Date(session.startMillis))
    val end = formatter.format(Date(session.endMillis.takeIf { it > 0 } ?: session.startMillis))
    return "$start - $end"
}

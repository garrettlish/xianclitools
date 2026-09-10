package com.example.xiancli_tools.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.RingtoneResolver
import com.example.xiancli_tools.data.SettingsRepository
import com.example.xiancli_tools.timer.TimerScheduler
import com.example.xiancli_tools.ui.components.IconBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private data class TimerOption(
    val label: String,
    val minutes: Int,
    val caption: String,
    val accent: Color,
    val icon: Int = R.drawable.ic_alarm,
    val custom: Boolean = false
)

private val timerOptions = listOf(
    TimerOption("5 分钟", 5, "泡面 · 小憩", Color(0xFF34A853)),
    TimerOption("10 分钟", 10, "冥想 · 放松", Color(0xFF2196F3)),
    TimerOption("30 分钟", 30, "高效专注", Color(0xFFF59E0B)),
    TimerOption("1 小时", 60, "深度工作", Color(0xFF8B5CF6)),
    TimerOption("自定义", 0, "自由设置", Color(0xFF00ACC1), R.drawable.ic_add, custom = true)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var endAt by remember { mutableLongStateOf(repository.scheduledEndAt) }
    var activeLabel by remember { mutableStateOf(repository.scheduledLabel) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCustomDialog by remember { mutableStateOf(false) }

    val scheduleDuration: (Long, String) -> Unit = { millis, label ->
        TimerScheduler.schedule(context, millis, label)
        endAt = System.currentTimeMillis() + millis
        activeLabel = label
        now = System.currentTimeMillis()
        scope.launch { snackbarHostState.showSnackbar("已设置 $label 定时") }
    }
    val alarmManager = remember {
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }
    var exactAllowed by remember { mutableStateOf(TimerScheduler.canScheduleExact(alarmManager)) }

    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        exactAllowed = TimerScheduler.canScheduleExact(alarmManager)
    }

    LaunchedEffect(endAt) {
        if (endAt <= 0L) return@LaunchedEffect
        while (isActive) {
            now = System.currentTimeMillis()
            if (now >= endAt) {
                endAt = 0L
                activeLabel = null
                break
            }
            delay(500)
        }
    }

    val vibrationEnabled = repository.vibrationEnabled
    val ringtoneTitle = RingtoneResolver.resolveTitle(context, repository)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("定时器", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "通知设置"
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
            if (!exactAllowed) {
                ExactAlarmBanner(
                    onEnable = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            exactAlarmLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                            )
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            if (endAt > 0L) {
                ActiveTimerCard(
                    label = activeLabel ?: "定时",
                    remainingMillis = (endAt - now).coerceAtLeast(0L),
                    onCancel = {
                        TimerScheduler.cancel(context)
                        endAt = 0L
                        activeLabel = null
                        scope.launch { snackbarHostState.showSnackbar("已取消定时") }
                    }
                )
                Spacer(Modifier.height(20.dp))
            }

            Text(
                text = "选择定时时长",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            timerOptions.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowOptions.forEach { option ->
                        DurationCard(
                            option = option,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (option.custom) {
                                    showCustomDialog = true
                                } else {
                                    scheduleDuration(option.minutes * 60_000L, option.label)
                                }
                            }
                        )
                    }
                    if (rowOptions.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))
            NotificationMethodCard(
                vibrationEnabled = vibrationEnabled,
                ringtoneTitle = ringtoneTitle,
                onConfigure = onOpenSettings
            )

            if (showCustomDialog) {
                CustomDurationDialog(
                    onDismiss = { showCustomDialog = false },
                    onConfirm = { millis, label ->
                        showCustomDialog = false
                        scheduleDuration(millis, label)
                    }
                )
            }
        }
    }
}

@Composable
private fun DurationCard(
    option: TimerOption,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = option.accent.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(
                icon = option.icon,
                tint = option.accent,
                container = Color.White.copy(alpha = 0.75f),
                size = 56.dp,
                iconSize = 30.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = option.accent
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = option.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveTimerCard(
    label: String,
    remainingMillis: Long,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = R.drawable.ic_tool_timer,
                tint = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                size = 52.dp,
                iconSize = 28.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$label 定时进行中",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = formatRemaining(remainingMillis),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            OutlinedButton(onClick = onCancel) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun NotificationMethodCard(
    vibrationEnabled: Boolean,
    ringtoneTitle: String,
    onConfigure: () -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "通知方式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onConfigure) {
                    Text("更改")
                }
            }
            Spacer(Modifier.height(8.dp))
            MethodRow(
                icon = R.drawable.ic_vibration,
                title = "震动",
                value = if (vibrationEnabled) "已开启" else "已关闭"
            )
            Spacer(Modifier.height(8.dp))
            MethodRow(
                icon = R.drawable.ic_ringtone,
                title = "铃声",
                value = ringtoneTitle
            )
        }
    }
}

@Composable
private fun MethodRow(icon: Int, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExactAlarmBanner(onEnable: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "未开启精确闹钟，定时可能略有延迟",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A5300),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEnable) {
                Text("去开启", color = Color(0xFF8A5300))
            }
        }
    }
}

@Composable
private fun CustomDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }

    val hourValue = hours.toIntOrNull() ?: 0
    val minuteValue = minutes.toIntOrNull() ?: 0
    val secondValue = seconds.toIntOrNull() ?: 0
    val totalSeconds = hourValue * 3600 + minuteValue * 60 + secondValue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义时长") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DurationField(
                        value = hours,
                        suffix = "时",
                        modifier = Modifier.weight(1f),
                        onValueChange = { hours = it }
                    )
                    DurationField(
                        value = minutes,
                        suffix = "分",
                        modifier = Modifier.weight(1f),
                        onValueChange = { minutes = it }
                    )
                    DurationField(
                        value = seconds,
                        suffix = "秒",
                        modifier = Modifier.weight(1f),
                        onValueChange = { seconds = it }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (totalSeconds > 0) {
                        "共计 ${formatCustomLabel(hourValue, minuteValue, secondValue)}"
                    } else {
                        "至少填写一项，例如「30 分」"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        totalSeconds * 1000L,
                        formatCustomLabel(hourValue, minuteValue, secondValue)
                    )
                },
                enabled = totalSeconds > 0
            ) {
                Text("开始")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DurationField(
    value: String,
    suffix: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter(Char::isDigit).take(3))
        },
        modifier = modifier,
        singleLine = true,
        label = { Text(suffix) },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun formatCustomLabel(hours: Int, minutes: Int, seconds: Int): String {
    val parts = buildList {
        if (hours > 0) add("$hours 小时")
        if (minutes > 0) add("$minutes 分钟")
        if (seconds > 0) add("$seconds 秒")
    }
    return parts.joinToString(" ")
}

private fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

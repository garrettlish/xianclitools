package com.example.xiancli_tools.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.RingtoneResolver
import com.example.xiancli_tools.data.SettingsRepository
import com.example.xiancli_tools.timer.AlarmRingingService
import com.example.xiancli_tools.timer.TimerScheduler
import com.example.xiancli_tools.ui.components.IconBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var vibrationEnabled by remember { mutableStateOf(repository.vibrationEnabled) }
    var ringtoneTitle by remember {
        mutableStateOf(RingtoneResolver.resolveTitle(context, repository))
    }
    var previewing by remember { mutableStateOf(false) }
    val ringtoneServiceIntent = remember {
        Intent(context, AlarmRingingService::class.java)
    }

    DisposableEffect(Unit) {
        onDispose { context.stopService(ringtoneServiceIntent) }
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(
                    it,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
            }
            val custom = picked?.takeIf { RingtoneResolver.isCustomRingtone(it) }
            repository.ringtoneUri = custom
            repository.ringtoneTitle = custom?.let { RingtoneResolver.titleOf(context, it) }
            ringtoneTitle = RingtoneResolver.resolveTitle(context, repository)
            scope.launch { snackbarHostState.showSnackbar("铃声已更新") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知方式", fontWeight = FontWeight.Bold) },
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
            Text(
                text = "定时结束时，按下面的方式提醒你。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            VibrationCard(
                enabled = vibrationEnabled,
                onToggle = {
                    vibrationEnabled = it
                    repository.vibrationEnabled = it
                }
            )

            Spacer(Modifier.height(12.dp))

            RingtoneCard(
                title = ringtoneTitle,
                onClick = {
                    ringtonePicker.launch(buildRingtonePickerIntent(repository))
                }
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (previewing) {
                        context.stopService(ringtoneServiceIntent)
                        previewing = false
                    } else {
                        val intent = Intent(ringtoneServiceIntent)
                            .putExtra(TimerScheduler.EXTRA_LABEL, "试听")
                            .putExtra(AlarmRingingService.EXTRA_PREVIEW, true)
                        ContextCompat.startForegroundService(context, intent)
                        previewing = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(
                        if (previewing) R.drawable.ic_stop else R.drawable.ic_ringtone
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (previewing) "停止试听" else "试听当前提醒")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "默认使用「震动 + 系统默认铃声」。可关闭震动，或挑选手机中的音乐作为铃声。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VibrationCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = R.drawable.ic_vibration,
                tint = Color(0xFF2196F3),
                container = Color(0xFF2196F3).copy(alpha = 0.14f)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "震动",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) "提醒时震动手机" else "提醒时不震动",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun RingtoneCard(title: String, onClick: () -> Unit) {
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = R.drawable.ic_ringtone,
                tint = Color(0xFF34A853),
                container = Color(0xFF34A853).copy(alpha = 0.14f)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "提示铃声",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = "选择铃声",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildRingtonePickerIntent(
    repository: SettingsRepository
): Intent {
    val currentUri = repository.ringtoneUri
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_TYPE,
            RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE or
                RingtoneManager.TYPE_NOTIFICATION
        )
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择提示铃声")
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
    }
}

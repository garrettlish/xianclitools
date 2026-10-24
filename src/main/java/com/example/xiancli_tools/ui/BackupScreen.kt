package com.example.xiancli_tools.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.BackupPayload
import com.example.xiancli_tools.data.BackupRepository
import com.example.xiancli_tools.ui.theme.LedgerAccent
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { BackupRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var payload by remember { mutableStateOf(BackupPayload()) }
    var pendingImport by remember { mutableStateOf<BackupPayload?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        payload = withContext(Dispatchers.IO) { repository.snapshot() }
    }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.IO) { repository.export(uri) }
            }
            busy = false
            result.onSuccess { exported ->
                notify(
                    "已导出 备忘 ${exported.notes.size} 条 · " +
                        "账本 ${exported.records.size} 条 · 足迹 ${exported.sessions.size} 条"
                )
            }.onFailure { notify("导出失败：${it.message ?: "未知错误"}") }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.IO) { repository.read(uri) }
            }
            busy = false
            result.onSuccess { pendingImport = it }
                .onFailure { notify("导入失败：${it.message ?: "文件无法读取"}") }
        }
    }

    val applyImport: (BackupPayload, Boolean) -> Unit = { incoming, replace ->
        pendingImport = null
        scope.launch {
            busy = true
            val before = payload
            val result = runCatching {
                withContext(Dispatchers.IO) { repository.apply(incoming, replace) }
            }
            if (result.isSuccess) {
                payload = withContext(Dispatchers.IO) { repository.snapshot() }
            }
            busy = false
            result.onSuccess {
                val message = if (replace) {
                    "已覆盖导入 备忘 ${payload.notes.size} 条 · " +
                        "账本 ${payload.records.size} 条 · 足迹 ${payload.sessions.size} 条"
                } else {
                    val added = incoming.newCountAgainst(before)
                    "已合并导入：新增 $added 条，跳过 ${incoming.totalCount - added} 条"
                }
                notify(message)
            }.onFailure { notify("导入失败：${it.message ?: "未知错误"}") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据备份", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "把备忘、账本和足迹打包成一个 JSON 文件；换手机或重装后导入即可恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "当前数据",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    CountRow("备忘", payload.notes.size, NoteAccent)
                    CountRow("账本", payload.records.size, LedgerAccent)
                    CountRow("足迹", payload.sessions.size, TrackAccent)
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    exportLauncher.launch(
                        "方寸备份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())}.json"
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_export),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("一键导出全部数据")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_import),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("一键导入数据")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "导出时会让你选择保存位置（如「下载」），文件只存在本机，不会上传。" +
                    "导入时可以选择「合并」（保留现有数据、只补新记录）或「覆盖」（先清空再导入）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    pendingImport?.let { incoming ->
        ImportConfirmDialog(
            incoming = incoming,
            existing = payload,
            onMerge = { applyImport(incoming, false) },
            onReplace = { applyImport(incoming, true) },
            onDismiss = { pendingImport = null }
        )
    }
}

@Composable
private fun CountRow(label: String, count: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accent, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ImportConfirmDialog(
    incoming: BackupPayload,
    existing: BackupPayload,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入备份", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "文件中包含：备忘 ${incoming.notes.size} 条 · " +
                        "账本 ${incoming.records.size} 条 · 足迹 ${incoming.sessions.size} 条。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "合并：保留现有数据，只补充新记录（预计新增 " +
                        "${incoming.newCountAgainst(existing)} 条）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "覆盖：先清空现有数据，再导入文件内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onMerge, modifier = Modifier.fillMaxWidth()) {
                    Text("合并导入")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onReplace, modifier = Modifier.fillMaxWidth()) {
                    Text("覆盖导入")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

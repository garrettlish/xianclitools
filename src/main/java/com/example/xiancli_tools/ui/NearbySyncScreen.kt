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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.BackupRepository
import com.example.xiancli_tools.data.lan.LanDiscovery
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.launch

/**
 * 同一 Wi-Fi 下两台设备直连互传：不经过微信、也不经过任何服务器。
 *
 * 两边都停在这个页面时，A 点 B → B 弹确认 → 双方各自合并，一次点击就完成双向同步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbySyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { BackupRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var manualAddress by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf(false) }

    val session = rememberNearbySyncSession(repository) { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // 这是直传的主页面，进来就起服务并开始搜索。
    LaunchedEffect(Unit) { session.start() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("附近直传", fontWeight = FontWeight.Bold) },
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
                text = "两台设备连同一个 Wi-Fi，都打开这个页面，点一下对方的设备名，" +
                    "确认后两边的数据会自动对齐。过程中不经过任何服务器。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "本机") {
                InfoRow("设备名", session.deviceName)
                InfoRow("地址", session.address ?: "获取中…")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = TrackAccent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = session.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "附近设备") {
                if (session.peers.isEmpty()) {
                    Text(
                        text = "正在搜索同一 Wi-Fi 下的设备…\n对方的手机上也要打开这个页面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    session.peers.forEach { peer ->
                        PeerRow(peer) { session.sendTo(peer) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { session.refresh() }) { Text("重新搜索") }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "手动连接") {
                Text(
                    text = "路由器挡了组播导致搜不到时，把对方「本机 · 地址」那一行填进来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualAddress,
                    onValueChange = {
                        manualAddress = it
                        manualError = false
                    },
                    singleLine = true,
                    isError = manualError,
                    placeholder = { Text("192.168.1.7:49301") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (manualError) {
                    Text(
                        text = "格式应该是 IP:端口",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (!session.sendToAddress(manualAddress)) manualError = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("连接")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "同步是双向的：对方的改动会过来，你的也会过去；" +
                    "同一条记录按最后修改时间取新，删除也会一并同步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    IncomingSyncRequestDialog(session)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PeerRow(peer: LanDiscovery.Peer, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${peer.host}:${peer.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

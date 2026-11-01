package com.example.xiancli_tools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.data.mijia.MijiaDevice
import com.example.xiancli_tools.data.mijia.MijiaDeviceConfig
import com.example.xiancli_tools.data.mijia.MijiaLabels
import com.example.xiancli_tools.ui.theme.MijiaAccent

/**
 * 改名 / 改分组 / 改分类。
 *
 * 改的只是这个 App 的显示：米家云端没有改名接口，所以米家 App 里还是原来的名字。
 * 分组留空是「跟随米家的房间」，另外给一个「设为未分组」的入口——
 * 两者在数据上不是一回事（null 与空串），界面上不能含糊。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MijiaConfigSheet(
    devices: List<MijiaDevice>,
    config: Map<String, MijiaDeviceConfig>,
    nameOf: (MijiaDevice) -> String,
    onRename: (String, String?) -> Unit,
    onRoom: (String, String?) -> Unit,
    onKind: (String, String?) -> Unit,
    onReset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MijiaDevice?>(null) }

    val filtered = remember(devices, query) {
        val text = query.trim()
        if (text.isEmpty()) {
            devices
        } else {
            devices.filter { device ->
                nameOf(device).contains(text, ignoreCase = true) ||
                    device.room.contains(text, ignoreCase = true) ||
                    MijiaLabels.categoryLabel(device.category).contains(text)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "改名 / 分组",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "只改这个 App 里的显示，米家 App 不受影响；改动不进备份包。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("按名字 / 房间 / 品类搜") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                items(filtered, key = { it.did }) { device ->
                    val override = config[device.did]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = device }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nameOf(device),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(
                                        (override?.room ?: device.room).takeIf { !it.isNullOrBlank() }
                                            ?: "未分组"
                                    )
                                    append(" · ")
                                    append(MijiaLabels.kindLabel(
                                        override?.kind ?: MijiaLabels.kindOf(device.category)
                                    ))
                                    if (override != null) append(" · 已改")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "改",
                            style = MaterialTheme.typography.labelLarge,
                            color = MijiaAccent
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { device ->
        EditDeviceDialog(
            device = device,
            override = config[device.did],
            displayName = nameOf(device),
            onSave = { name, room, kind ->
                onRename(device.did, name)
                onRoom(device.did, room)
                onKind(device.did, kind)
                editing = null
            },
            onReset = {
                onReset(device.did)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/**
 * 单台设备的编辑框。
 *
 * 三个字段都是「留空即跟随米家」：名称留空用原名，分组留空用米家的房间，
 * 分类不选就按品类自动归类。分组另给一个「设为未分组」，因为空分组是有意义的取值。
 */
@Composable
private fun EditDeviceDialog(
    device: MijiaDevice,
    override: MijiaDeviceConfig?,
    displayName: String,
    onSave: (String?, String?, String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(device.did) { mutableStateOf(override?.name.orEmpty()) }
    var room by remember(device.did) {
        mutableStateOf(if (override?.room != null) override.room else device.room)
    }
    var kind by remember(device.did) { mutableStateOf(override?.kind) }
    var ungrouped by remember(device.did) { mutableStateOf(override?.room == "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("显示设置") },
        text = {
            Column {
                Text(
                    text = "米家里的名字：${device.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("名称") },
                    placeholder = { Text(device.name) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = room,
                    onValueChange = {
                        room = it
                        ungrouped = false
                    },
                    singleLine = true,
                    enabled = !ungrouped,
                    label = { Text("分组") },
                    placeholder = { Text(device.room.ifBlank { "未分组" }) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        ungrouped = !ungrouped
                        if (ungrouped) room = ""
                    }) {
                        Text(if (ungrouped) "改为跟随米家房间" else "设为未分组")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MijiaLabels.KINDS.forEach { item ->
                        val active = kind == item.key
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) MijiaAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .background(
                                    if (active) MijiaAccent.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { kind = if (active) null else item.key }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Text(
                    text = "不选分类就按品类自动归类（现在是 ${MijiaLabels.kindLabel(MijiaLabels.kindOf(device.category))}）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim().takeIf { it.isNotEmpty() },
                        if (ungrouped) "" else room.trim().takeIf { it.isNotEmpty() },
                        kind
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("恢复默认") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

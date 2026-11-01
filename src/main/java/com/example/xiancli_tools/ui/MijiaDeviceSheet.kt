package com.example.xiancli_tools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.mijia.MijiaDevice
import com.example.xiancli_tools.data.mijia.MijiaLabels
import com.example.xiancli_tools.data.mijia.MijiaPropKey
import com.example.xiancli_tools.data.mijia.MijiaPropSpec
import com.example.xiancli_tools.data.mijia.MijiaRepository
import com.example.xiancli_tools.data.mijia.MijiaSpec
import com.example.xiancli_tools.ui.theme.MijiaAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 设备面板：点开一台设备之后的所有属性。
 *
 * 打开时先把这个设备所有可读属性批量读一遍（一个请求），再按
 * 「可控制 / 状态 / 动作」分三组摆出来：
 * - 布尔 → 开关；只写不可读的（「按一下」这类）→ 一次性按钮；
 * - 带取值列表 → 下拉；带范围的数字 → 滑块；其余数字和字符串 → 输入框；
 * - 只读属性单列一组「状态」，不给控件，只给值。
 *
 * 写操作都是「先改界面再发请求」，失败回滚并把原因写在底部——
 * 设备离线时界面不会卡住，也不会假装成功。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MijiaDeviceSheet(
    device: MijiaDevice,
    spec: MijiaSpec?,
    title: String,
    subtitle: String,
    values: Map<MijiaPropKey, Any?>,
    repository: MijiaRepository,
    onValues: (Map<MijiaPropKey, Any?>) -> Unit,
    onDismiss: () -> Unit,
    onEditConfig: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var errorText by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun commit(prop: MijiaPropSpec, value: Any, previous: Any?) {
        val key = MijiaPropKey(device.did, prop.siid, prop.piid)
        onValues(mapOf(key to value))
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.writeProp(device.did, prop.siid, prop.piid, value)
                }
                errorText = null
            } catch (failure: Exception) {
                onValues(mapOf(key to previous))
                errorText = failure.message?.takeIf { it.isNotBlank() } ?: "设备没有响应"
            } finally {
                busy = false
            }
        }
    }

    // 面板一打开就把这个设备的可读属性读一遍：卡片上只有一个主开关，其余都是未知。
    LaunchedEffect(device.did, spec) {
        val readable = spec?.properties?.filter { it.readable } ?: return@LaunchedEffect
        if (readable.isEmpty()) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                repository.readProps(
                    readable.map { MijiaPropKey(device.did, it.siid, it.piid) }
                )
            }
        }.onSuccess { result ->
            onValues(
                result.filterValues { it.ok }.mapValues { it.value.value }
            )
        }.onFailure { failure ->
            errorText = failure.message?.takeIf { it.isNotBlank() } ?: "状态没读到"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                spec == null -> Text(
                    text = "这台设备的规格还在读取中，稍等一下再点开。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )

                else -> {
                    val writable = spec.properties.filter { it.writable }
                    val readonly = spec.properties.filter { !it.writable }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (writable.isNotEmpty()) {
                            item(key = "title-writable") { GroupTitle("可控制") }
                            items(writable, key = { "w-${it.siid}-${it.piid}" }) { prop ->
                                PropRow(
                                    prop = prop,
                                    value = values[MijiaPropKey(device.did, prop.siid, prop.piid)],
                                    enabled = device.online && !busy,
                                    onCommit = { next ->
                                        commit(
                                            prop,
                                            next,
                                            values[MijiaPropKey(device.did, prop.siid, prop.piid)]
                                        )
                                    }
                                )
                            }
                        }
                        if (readonly.isNotEmpty()) {
                            item(key = "title-readonly") { GroupTitle("状态") }
                            items(readonly, key = { "r-${it.siid}-${it.piid}" }) { prop ->
                                ReadonlyRow(
                                    prop = prop,
                                    value = values[MijiaPropKey(device.did, prop.siid, prop.piid)]
                                )
                            }
                        }
                        if (spec.actions.isNotEmpty()) {
                            item(key = "title-actions") { GroupTitle("动作") }
                            item(key = "actions") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    spec.actions.chunked(3).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            row.forEach { action ->
                                                OutlinedButton(
                                                    onClick = {
                                                        busy = true
                                                        scope.launch {
                                                            try {
                                                                withContext(Dispatchers.IO) {
                                                                    repository.runAction(
                                                                        device.did,
                                                                        action.siid,
                                                                        action.aiid
                                                                    )
                                                                }
                                                                errorText = null
                                                            } catch (failure: Exception) {
                                                                errorText = failure.message
                                                                    ?: "动作没执行成功"
                                                            } finally {
                                                                busy = false
                                                            }
                                                        }
                                                    },
                                                    enabled = device.online && !busy
                                                ) {
                                                    Text(action.label)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (writable.isEmpty() && readonly.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    text = "这台设备没有可读写的属性。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            errorText?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.did,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEditConfig) { Text("名称 / 分组") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun ReadonlyRow(prop: MijiaPropSpec, value: Any?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = prop.label, style = MaterialTheme.typography.bodyMedium)
            if (prop.hint.isNotBlank()) {
                Text(
                    text = prop.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = MijiaLabels.formatValue(prop, value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PropRow(
    prop: MijiaPropSpec,
    value: Any?,
    enabled: Boolean,
    onCommit: (Any) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = prop.label, style = MaterialTheme.typography.bodyMedium)
            if (prop.hint.isNotBlank()) {
                Text(
                    text = prop.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(modifier = Modifier.padding(start = 12.dp)) {
            when {
                prop.isBool && prop.readable -> Switch(
                    checked = value == true,
                    onCheckedChange = { onCommit(it) },
                    enabled = enabled
                )

                prop.isBool -> OutlinedButton(
                    onClick = { onCommit(true) },
                    enabled = enabled
                ) { Text("触发") }

                prop.values.isNotEmpty() -> EnumPicker(prop, value, enabled, onCommit)

                prop.isNumber && prop.range != null && prop.range.size >= 2 -> RangePicker(
                    prop,
                    value,
                    enabled,
                    onCommit
                )

                else -> TextInput(prop, value, enabled, onCommit)
            }
        }
    }
}

/** 枚举：下拉里列出规格给的取值，中文名优先用 comment。 */
@Composable
private fun EnumPicker(
    prop: MijiaPropSpec,
    value: Any?,
    enabled: Boolean,
    onCommit: (Any) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val current = prop.values.firstOrNull { it.value.toString() == value.toString() }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) {
            Text(current?.label ?: "选择", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            prop.values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        open = false
                        onCommit(item.value)
                    }
                )
            }
        }
    }
}

/** 带范围的数字：拖动时只改本地值，松手才发请求，免得拖一下就发几十条。 */
@Composable
private fun RangePicker(
    prop: MijiaPropSpec,
    value: Any?,
    enabled: Boolean,
    onCommit: (Any) -> Unit
) {
    val range = prop.range ?: return
    val min = range[0].toFloat()
    val max = range[1].toFloat()
    val step = range.getOrNull(2)?.toFloat()?.takeIf { it > 0f } ?: 1f
    val initial = (value as? Number)?.toFloat()?.coerceIn(min, max) ?: min
    var draft by remember(prop.siid, prop.piid, value) { mutableStateOf(initial) }
    val isInteger = prop.type == "int" || prop.type == "uint"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                val snapped = min + ((draft - min) / step).roundToInt() * step
                val next = snapped.coerceIn(min, max)
                draft = next
                onCommit(if (isInteger) next.roundToInt() else next)
            },
            valueRange = min..max,
            enabled = enabled,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = if (isInteger) {
                draft.roundToInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", draft)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** 字符串 / 没有范围的数字：输入框 + 设置按钮，不边打字边发请求。 */
@Composable
private fun TextInput(
    prop: MijiaPropSpec,
    value: Any?,
    enabled: Boolean,
    onCommit: (Any) -> Unit
) {
    var text by remember(prop.siid, prop.piid, value) { mutableStateOf(value?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (prop.isNumber) KeyboardType.Number else KeyboardType.Text
            ),
            modifier = Modifier.width(120.dp)
        )
        TextButton(
            onClick = {
                val next: Any = if (prop.isNumber) {
                    text.trim().toDoubleOrNull()?.let { number ->
                        if (prop.type == "float") number else number.roundToInt()
                    } ?: return@TextButton
                } else {
                    text
                }
                onCommit(next)
            },
            enabled = enabled
        ) { Text("设置") }
    }
}

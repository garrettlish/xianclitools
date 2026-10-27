package com.example.xiancli_tools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R

/**
 * 工具页三个模式标签的统一图标：记录 / 明细 / 统计。
 * 五个工具共用同一套——同一位置同一个意思，换工具时不用重新认图标。
 */
private val ModeTabIcons = listOf(
    R.drawable.ic_tab_record,
    R.drawable.ic_tab_list,
    R.drawable.ic_tab_chart
)

/**
 * 分段切换控件。两处用它：
 * 1. 工具页顶部的「记事 / 明细 / 统计」（记账 / 明细 / 统计、记录 / 明细 / 统计）模式切换
 *    （传 accent，选中项用工具色，文字左边带一枚统一小图标）；
 * 2. 报表里的「日 / 周 / 月 / 年 / 自定义」周期切换（不传 accent，选中项用正文色，不带图标）。
 *
 * [icons] 不传时按 [accent] 自动判断：模式切换用 [ModeTabIcons]，周期切换不加图标。
 * 图标与文字同色、只占 15dp，是为了不抢标签本身的对比度——它只是帮眼睛定位，不是主体。
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    icons: List<Int>? = null
) {
    val resolvedIcons = icons ?: if (accent != null) ModeTabIcons else emptyList()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            // 带 accent 的是「模式切换」，用淡强调色底把它和下面的周期筛选区分开；
            // 不带的（日/周/月/年）用白底，视觉上退到第二层。
            val background = when {
                !active -> Color.Transparent
                accent != null -> accent.copy(alpha = 0.16f)
                else -> MaterialTheme.colorScheme.surface
            }
            val contentColor = when {
                !active -> MaterialTheme.colorScheme.onSurfaceVariant
                accent != null -> accent
                else -> MaterialTheme.colorScheme.onSurface
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val icon = resolvedIcons.getOrNull(index)
                    if (icon != null) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor
                    )
                }
            }
        }
    }
}

package com.example.xiancli_tools.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.ui.theme.Dimens

/**
 * 图标徽章：Bento 风格的圆角方块（不再用圆形），色块感更整，
 * 与首页两列工具网格、指标格的方形语言一致。
 */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(container, RoundedCornerShape(Dimens.CornerM)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

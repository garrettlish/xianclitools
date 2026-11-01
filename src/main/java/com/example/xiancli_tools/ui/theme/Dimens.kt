package com.example.xiancli_tools.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 间距与圆角的设计 token：全 App 只用这套值，不允许随手写 6dp / 13dp 这类夹缝数。
 * 间距走 4 的倍数（4/8/12/16/24/32），页面级留白 16、卡片内边距 16、元素间隙 8 或 12。
 */
object Dimens {
    // 间距阶梯
    val SpaceXS = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXL = 24.dp
    val SpaceXXL = 32.dp

    // 圆角阶梯：容器越大圆角越大，同一层级永远同值
    val CornerS = 10.dp   // 小内嵌块（标签选中块、指标格）
    val CornerM = 14.dp   // 中容器（分段标签、输入条）
    val CornerL = 18.dp   // 页面卡片（SectionCard、工具卡）
    val CornerXL = 24.dp  // 底部弹层、大面板

    // 触控目标：可点行与图标按钮不低于 44dp（WCAG 2.2 AA 的舒适下限）
    val TouchTarget = 44.dp
}

/** Material 组件（弹窗 / 菜单 / 输入框 / 底部弹层）的默认圆角，与卡片的 18dp 家族对齐。 */
val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.CornerS),
    small = RoundedCornerShape(Dimens.CornerS),
    medium = RoundedCornerShape(Dimens.CornerM),
    large = RoundedCornerShape(Dimens.CornerL),
    extraLarge = RoundedCornerShape(Dimens.CornerXL)
)

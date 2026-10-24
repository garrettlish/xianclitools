package com.example.xiancli_tools.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 纸感暖色盘：底色不用纯白，改成带一点米黄的暖白，长时间看不刺眼；
 * 五类强调色统一压饱和、加暖调，避免 Material 默认色那种「系统设置页」的冷感。
 */
val Paper = Color(0xFFFAF7F2)
val PaperDeep = Color(0xFFF3EDE3)
val CardWhite = Color(0xFFFFFFFF)
val Ink = Color(0xFF1F1D1A)
val InkSoft = Color(0xFF6F675C)
val LineSoft = Color(0xFFE6E0D6)

/** 五个工具的强调色，同一套暖色家族：琥珀 / 藕紫 / 咖褐 / 苔绿 / 水蓝。 */
val TimerAccent = Color(0xFFE8963A)
val NoteAccent = Color(0xFF9B6DB5)
val LedgerAccent = Color(0xFF8D6748)
val TrackAccent = Color(0xFF5AA469)
val StatsAccent = Color(0xFF2F86D6)

/** 陶土红：删除、警示等需要压过强调色的场合。 */
val ClayRed = Color(0xFFC05A45)

/** 暖灰：无类别 / 未分类的中性色，比 #9E9E9E 更贴纸感底色。 */
val WarmGray = Color(0xFF9C948A)

/** 暖灰青：医疗、其他等辅助类别。 */
val MutedTeal = Color(0xFF4E9BA0)

/** 暖玫褐：购物、娱乐等辅助类别。 */
val MutedRose = Color(0xFFC4677F)

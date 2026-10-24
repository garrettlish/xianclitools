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

/**
 * 品牌色：晨光与暮色。用于「朝夕」字标的渐变两端。
 * 比工具强调色深一档——22sp 粗体在暖白底上要够对比度（#C87A2E 约 3.1:1，#9E5570 约 4.9:1）。
 * 首页左侧那枚记号是启动图标的缩小版，色值跟着 `ic_launcher_foreground` 走，不取这两个值。
 */
val BrandDawn = Color(0xFFC87A2E)
val BrandDusk = Color(0xFF9E5570)

/** 五个工具的强调色，同一套暖色家族：琥珀 / 藕紫 / 咖褐 / 苔绿 / 水蓝。 */
val TimerAccent = Color(0xFFE8963A)
val NoteAccent = Color(0xFF9B6DB5)
val LedgerAccent = Color(0xFF8D6748)
val TrackAccent = Color(0xFF5AA469)
val StatsAccent = Color(0xFF2F86D6)

/**
 * 饮记：水蓝。直接取统计色，不另调一个近似值——
 * 同一族里多调一档，界面上两个蓝谁也说不清哪个是哪个。
 */
val DrinkAccent = StatsAccent

/** 陶土红：删除、警示等需要压过强调色的场合。 */
val ClayRed = Color(0xFFC05A45)

/** 暖灰：无类别 / 未分类的中性色，比 #9E9E9E 更贴纸感底色。 */
val WarmGray = Color(0xFF9C948A)

/** 暖灰青：医疗、其他等辅助类别。 */
val MutedTeal = Color(0xFF4E9BA0)

/** 暖玫褐：购物、娱乐等辅助类别。 */
val MutedRose = Color(0xFFC4677F)

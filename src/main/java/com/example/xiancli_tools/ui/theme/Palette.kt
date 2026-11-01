package com.example.xiancli_tools.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 艳丽高对比色板（Bento 风格）：底色是暖奶油纸，卡片纯白，强调色全部换成
 * 高饱和的 600 级色（Tailwind 色阶），在白卡上做正文级数字仍有 3.5:1 以上的对比，
 * 在暗色表面上也够亮。类别色卡（账本/备忘）直接引用这些常量，换这里 = 全局换血。
 */
val Paper = Color(0xFFFFF6EA)
val PaperDeep = Color(0xFFF9EBD9)
val CardWhite = Color(0xFFFFFFFF)
val Ink = Color(0xFF111827)
val InkSoft = Color(0xFF4B5563)
val LineSoft = Color(0xFFE8DCC6)

/**
 * 品牌色：晨光与暮色的艳丽渐变。左「朝」橙、右「夕」桃红，
 * 22sp 粗体在奶油底上分别约 3.6:1 / 4.5:1，大字达标。
 */
val BrandDawn = Color(0xFFEA580C)
val BrandDusk = Color(0xFFDB2777)

/** 六个工具的强调色：亮橙 / 紫 / 金 / 绿 / 蓝 / 桃红，六个色相互不粘连。 */
val TimerAccent = Color(0xFFEA580C)
val NoteAccent = Color(0xFF7C3AED)
val LedgerAccent = Color(0xFFCA8A04)
val TrackAccent = Color(0xFF16A34A)
val StatsAccent = Color(0xFF2563EB)

/** 饮记：直接取统计蓝，不另调近似值——同一族多调一档，两个蓝谁也说不清。 */
val DrinkAccent = StatsAccent

/** 米家：桃红。亮橙已被计时占住，桃红在首页远看也分得开。 */
val MijiaAccent = Color(0xFFDB2777)

/** 红：删除、警示。与桃红同族但更纯更正，只在危险场合出场。 */
val ClayRed = Color(0xFFDC2626)

/** 石板灰：无类别 / 未分类的中性色，艳丽色板里负责「收」的那一支。 */
val WarmGray = Color(0xFF64748B)

/** 青：医疗、其他等辅助类别。 */
val MutedTeal = Color(0xFF0D9488)

/** 洋红：购物、娱乐等辅助类别。 */
val MutedRose = Color(0xFFC026D3)

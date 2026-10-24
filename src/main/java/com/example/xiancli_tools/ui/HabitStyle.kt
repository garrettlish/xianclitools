package com.example.xiancli_tools.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.xiancli_tools.R
import com.example.xiancli_tools.ui.theme.ClayRed
import com.example.xiancli_tools.ui.theme.MutedTeal
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.StatsAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import com.example.xiancli_tools.ui.theme.WarmGray

/**
 * 习惯预设：新增时不逼用户挑图标，点一个常见习惯就带好图标和颜色，
 * 想自定义就选「其他」，再改名字即可。
 */
data class HabitPreset(
    val label: String,
    val iconKey: String,
    @DrawableRes val icon: Int,
    val accent: Color
)

val habitPresets = listOf(
    HabitPreset("喝水", "water", R.drawable.ic_water, StatsAccent),
    HabitPreset("吃药", "pill", R.drawable.ic_cat_medical, ClayRed),
    HabitPreset("运动", "sport", R.drawable.ic_directions_walk, TrackAccent),
    HabitPreset("读书", "read", R.drawable.ic_tool_note, NoteAccent),
    HabitPreset("早睡", "sleep", R.drawable.ic_cat_mood, TimerAccent),
    HabitPreset("其他", "custom", R.drawable.ic_tool_habit, WarmGray)
)

fun habitIcon(iconKey: String): Int =
    habitPresets.firstOrNull { it.iconKey == iconKey }?.icon ?: R.drawable.ic_tool_habit

fun habitAccent(iconKey: String): Color =
    habitPresets.firstOrNull { it.iconKey == iconKey }?.accent ?: MutedTeal

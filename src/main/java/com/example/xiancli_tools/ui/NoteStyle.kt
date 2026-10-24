package com.example.xiancli_tools.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.ui.theme.ClayRed
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.StatsAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import com.example.xiancli_tools.ui.theme.WarmGray

val NoteCategory.accent: Color
    get() = when (this) {
        NoteCategory.REMINDER -> ClayRed
        NoteCategory.TODO -> TrackAccent
        NoteCategory.DIARY -> StatsAccent
        NoteCategory.MOOD -> TimerAccent
        NoteCategory.IDEA -> NoteAccent
        NoteCategory.OTHER -> WarmGray
    }

val NoteCategory.iconRes: Int
    @DrawableRes get() = when (this) {
        NoteCategory.REMINDER -> R.drawable.ic_alarm
        NoteCategory.TODO -> R.drawable.ic_cat_todo
        NoteCategory.DIARY -> R.drawable.ic_tool_note
        NoteCategory.MOOD -> R.drawable.ic_cat_mood
        NoteCategory.IDEA -> R.drawable.ic_cat_idea
        NoteCategory.OTHER -> R.drawable.ic_cat_other
    }

package com.example.xiancli_tools.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.ui.theme.StatsAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent

val NoteCategory.accent: Color
    get() = when (this) {
        NoteCategory.TODO -> TrackAccent
        NoteCategory.DIARY -> StatsAccent
        NoteCategory.MOOD -> TimerAccent
    }

val NoteCategory.iconRes: Int
    @DrawableRes get() = when (this) {
        NoteCategory.TODO -> R.drawable.ic_cat_todo
        NoteCategory.DIARY -> R.drawable.ic_tool_note
        NoteCategory.MOOD -> R.drawable.ic_cat_mood
    }

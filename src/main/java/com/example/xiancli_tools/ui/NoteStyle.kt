package com.example.xiancli_tools.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.NoteCategory

val NoteCategory.accent: Color
    get() = when (this) {
        NoteCategory.REMINDER -> Color(0xFFEA4335)
        NoteCategory.TODO -> Color(0xFF34A853)
        NoteCategory.DIARY -> Color(0xFF2196F3)
        NoteCategory.MOOD -> Color(0xFFF59E0B)
        NoteCategory.IDEA -> Color(0xFF8B5CF6)
        NoteCategory.OTHER -> Color(0xFF9E9E9E)
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

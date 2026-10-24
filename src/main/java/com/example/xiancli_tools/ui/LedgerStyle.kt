package com.example.xiancli_tools.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.ui.theme.ClayRed
import com.example.xiancli_tools.ui.theme.MutedRose
import com.example.xiancli_tools.ui.theme.MutedTeal
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.StatsAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import com.example.xiancli_tools.ui.theme.WarmGray

val ExpenseCategory.accent: Color
    get() = when (this) {
        ExpenseCategory.FOOD -> ClayRed
        ExpenseCategory.TRANSPORT -> StatsAccent
        ExpenseCategory.HOTEL -> NoteAccent
        ExpenseCategory.TICKET -> TrackAccent
        ExpenseCategory.SHOPPING -> MutedRose
        ExpenseCategory.ENTERTAINMENT -> TimerAccent
        ExpenseCategory.MEDICAL -> MutedTeal
        ExpenseCategory.OTHER -> WarmGray
    }

val ExpenseCategory.iconRes: Int
    @DrawableRes get() = when (this) {
        ExpenseCategory.FOOD -> R.drawable.ic_cat_food
        ExpenseCategory.TRANSPORT -> R.drawable.ic_cat_transport
        ExpenseCategory.HOTEL -> R.drawable.ic_cat_hotel
        ExpenseCategory.TICKET -> R.drawable.ic_cat_ticket
        ExpenseCategory.SHOPPING -> R.drawable.ic_cat_shopping
        ExpenseCategory.ENTERTAINMENT -> R.drawable.ic_cat_entertainment
        ExpenseCategory.MEDICAL -> R.drawable.ic_cat_medical
        ExpenseCategory.OTHER -> R.drawable.ic_cat_other
    }

package com.example.xiancli_tools.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.ExpenseCategory

val ExpenseCategory.accent: Color
    get() = when (this) {
        ExpenseCategory.FOOD -> Color(0xFFEA4335)
        ExpenseCategory.TRANSPORT -> Color(0xFF2196F3)
        ExpenseCategory.HOTEL -> Color(0xFF8B5CF6)
        ExpenseCategory.TICKET -> Color(0xFF34A853)
        ExpenseCategory.SHOPPING -> Color(0xFFEC407A)
        ExpenseCategory.ENTERTAINMENT -> Color(0xFFF59E0B)
        ExpenseCategory.MEDICAL -> Color(0xFF00ACC1)
        ExpenseCategory.OTHER -> Color(0xFF9E9E9E)
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

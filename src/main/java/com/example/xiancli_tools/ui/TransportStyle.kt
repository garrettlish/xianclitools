package com.example.xiancli_tools.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.TransportMode

val TransportMode.accent: Color
    get() = when (this) {
        TransportMode.WALK -> Color(0xFF34A853)
        TransportMode.BIKE -> Color(0xFF2196F3)
        TransportMode.DRIVE -> Color(0xFFF59E0B)
    }

val TransportMode.iconRes: Int
    @DrawableRes get() = when (this) {
        TransportMode.WALK -> R.drawable.ic_directions_walk
        TransportMode.BIKE -> R.drawable.ic_directions_bike
        TransportMode.DRIVE -> R.drawable.ic_directions_car
    }

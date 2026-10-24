package com.example.xiancli_tools.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.TransportMode
import com.example.xiancli_tools.ui.theme.StatsAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent

val TransportMode.accent: Color
    get() = when (this) {
        TransportMode.WALK -> TrackAccent
        TransportMode.BIKE -> StatsAccent
        TransportMode.DRIVE -> TimerAccent
    }

val TransportMode.iconRes: Int
    @DrawableRes get() = when (this) {
        TransportMode.WALK -> R.drawable.ic_directions_walk
        TransportMode.BIKE -> R.drawable.ic_directions_bike
        TransportMode.DRIVE -> R.drawable.ic_directions_car
    }

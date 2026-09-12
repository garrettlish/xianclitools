package com.example.xiancli_tools.track

import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecorderSnapshot(
    val recording: Boolean = false,
    val mode: TransportMode = TransportMode.WALK,
    val startMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val pointCount: Int = 0,
    val startPoint: TrackPoint? = null,
    val lastPoint: TrackPoint? = null
)

object TrackRecorderState {

    private val _state = MutableStateFlow(RecorderSnapshot())
    val state: StateFlow<RecorderSnapshot> = _state.asStateFlow()

    fun update(snapshot: RecorderSnapshot) {
        _state.value = snapshot
    }

    fun reset() {
        _state.value = RecorderSnapshot()
    }
}

package com.example.xiancli_tools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

data class ChartSlice(val value: Float, val color: Color)

@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 24.dp,
    trackColor: Color = Color(0x1F000000),
    center: @Composable () -> Unit = {}
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = min(size.width, size.height) - stroke
            if (diameter <= 0f) return@Canvas
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )

            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas

            var startAngle = -90f
            slices.forEach { slice ->
                if (slice.value <= 0f) return@forEach
                val sweep = 360f * slice.value / total
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
        center()
    }
}

@Composable
fun StackedBar(
    segments: List<ChartSlice>,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    trackColor: Color = Color(0x1F000000)
) {
    Canvas(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
    ) {
        drawRect(trackColor, size = size)
        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas
        var x = 0f
        segments.forEach { segment ->
            val width = size.width * (segment.value / total)
            drawRect(
                color = segment.color,
                topLeft = Offset(x, 0f),
                size = Size(width, size.height)
            )
            x += width
        }
    }
}

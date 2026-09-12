package com.example.xiancli_tools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TransportMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

data class MapTrack(
    val mode: TransportMode,
    val points: List<TrackPoint>
)

private const val METERS_PER_DEGREE = 111_320.0

private data class Projection(
    val centerLat: Double,
    val centerLng: Double,
    val metersPerDegreeLng: Double,
    val pixelsPerMeter: Double
)

@Composable
fun TrackMap(
    tracks: List<MapTrack>,
    colorOf: (TransportMode) -> Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF0F1720),
    gridColor: Color = Color(0x1AFFFFFF)
) {
    val allPoints = remember(tracks) { tracks.flatMap { it.points } }
    if (allPoints.isEmpty()) {
        Box(modifier)
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier.pointerInput(tracks) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 24f)
                offset += pan
            }
        }
    ) {
        drawRect(backgroundColor)
        val projection = computeProjection(allPoints, size)
        val project: (TrackPoint) -> Offset = { point -> projectPoint(point, projection, size) }

        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset(size.width / 2f, size.height / 2f))
        }) {
            drawGrid(gridColor)
            tracks.forEach { track ->
                if (track.points.size < 2) return@forEach
                val path = Path()
                track.points.forEachIndexed { index, point ->
                    val projected = project(point)
                    if (index == 0) path.moveTo(projected.x, projected.y)
                    else path.lineTo(projected.x, projected.y)
                }
                drawPath(
                    path = path,
                    color = colorOf(track.mode),
                    style = Stroke(
                        width = 5.dp.toPx() / scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            tracks.forEach { track ->
                val first = track.points.firstOrNull() ?: return@forEach
                val last = track.points.lastOrNull() ?: return@forEach
                drawCircle(
                    color = Color(0xFF34A853),
                    radius = 6.dp.toPx() / scale,
                    center = project(first),
                    style = Stroke(width = 3.dp.toPx() / scale)
                )
                drawCircle(
                    color = Color(0xFFEA4335),
                    radius = 6.dp.toPx() / scale,
                    center = project(last),
                    style = Stroke(width = 3.dp.toPx() / scale)
                )
            }
        }

        drawScaleBar(
            textMeasurer = textMeasurer,
            metersPerPixel = 1.0 / projection.pixelsPerMeter / scale
        )
    }
}

private fun computeProjection(points: List<TrackPoint>, size: Size): Projection {
    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLng = Double.MAX_VALUE
    var maxLng = -Double.MAX_VALUE
    points.forEach { point ->
        minLat = min(minLat, point.latitude)
        maxLat = max(maxLat, point.latitude)
        minLng = min(minLng, point.longitude)
        maxLng = max(maxLng, point.longitude)
    }
    val centerLat = (minLat + maxLat) / 2.0
    val centerLng = (minLng + maxLng) / 2.0
    val metersPerDegreeLng = METERS_PER_DEGREE * cos(centerLat * PI / 180.0)
    val spanX = max((maxLng - minLng) * metersPerDegreeLng, 1.0)
    val spanY = max((maxLat - minLat) * METERS_PER_DEGREE, 1.0)
    val padding = 0.12f * min(size.width, size.height)
    val usableWidth = (size.width - 2 * padding).coerceAtLeast(1f)
    val usableHeight = (size.height - 2 * padding).coerceAtLeast(1f)
    val pixelsPerMeter = min(usableWidth / spanX, usableHeight / spanY)
    return Projection(centerLat, centerLng, metersPerDegreeLng, pixelsPerMeter)
}

private fun projectPoint(point: TrackPoint, projection: Projection, size: Size): Offset {
    val metersX = (point.longitude - projection.centerLng) * projection.metersPerDegreeLng
    val metersY = (point.latitude - projection.centerLat) * METERS_PER_DEGREE
    return Offset(
        x = size.width / 2f + (metersX * projection.pixelsPerMeter).toFloat(),
        y = size.height / 2f - (metersY * projection.pixelsPerMeter).toFloat()
    )
}

private fun DrawScope.drawGrid(color: Color) {
    val step = 64f
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawScaleBar(textMeasurer: TextMeasurer, metersPerPixel: Double) {
    val candidates = doubleArrayOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0)
    val targetPx = 90.0 * density
    val meters = candidates.firstOrNull { it / metersPerPixel >= targetPx } ?: candidates.last()
    val lengthPx = (meters / metersPerPixel).toFloat().coerceAtMost(size.width * 0.6f)

    val left = 16.dp.toPx()
    val bottom = size.height - 24.dp.toPx()
    val label = if (meters >= 1000) "${(meters / 1000).toInt()} km" else "${meters.toInt()} m"
    val textStyle = TextStyle(
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
    val textLayout = textMeasurer.measure(label, textStyle)

    drawLine(
        color = Color.White,
        start = Offset(left, bottom),
        end = Offset(left + lengthPx, bottom),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
    listOf(left, left + lengthPx).forEach { tickX ->
        drawLine(
            color = Color.White,
            start = Offset(tickX, bottom - 5.dp.toPx()),
            end = Offset(tickX, bottom + 5.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
    }
    drawText(
        textMeasurer = textMeasurer,
        text = label,
        topLeft = Offset(left, bottom - textLayout.size.height - 6.dp.toPx()),
        style = textStyle
    )
}

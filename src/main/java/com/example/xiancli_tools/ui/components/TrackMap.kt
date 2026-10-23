package com.example.xiancli_tools.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.example.xiancli_tools.data.GeoTransform
import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TransportMode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.io.File
import java.util.Locale
import android.graphics.Color as AndroidColor

data class MapTrack(
    val mode: TransportMode,
    val points: List<TrackPoint>
)

private val START_COLOR = AndroidColor.rgb(0x34, 0xA8, 0x53)
private val END_COLOR = AndroidColor.rgb(0xEA, 0x43, 0x35)

private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 18.0
private const val FIT_PADDING_DP = 28f
private const val MARKER_SIZE_DP = 18f
private const val LINE_WIDTH_DP = 5f

/**
 * 轨迹地图：高德标准底图 + WGS-84 转 GCJ-02 后的轨迹线。
 *
 * 手机定位记录的是 WGS-84，国内底图是 GCJ-02，转换在 [TrackPoint.toGeoPoint] 中完成，
 * 否则轨迹会整体偏离实际道路几百米。
 */
@Composable
fun TrackMap(
    tracks: List<MapTrack>,
    colorOf: (TransportMode) -> Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    val mapView = remember(context) { createTrackMapView(context) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    val lineWidthPx = with(density) { LINE_WIDTH_DP.dp.toPx() }
    val markerSizePx = with(density) { MARKER_SIZE_DP.dp.toPx() }
    val fitPaddingPx = with(density) { FIT_PADDING_DP.dp.roundToPx() }

    LaunchedEffect(mapView, tracks) {
        val drawn = mapView.renderTracks(tracks, colorOf, lineWidthPx, markerSizePx)
        if (drawn.isNotEmpty()) {
            // 等 MapView 完成测量后再按轨迹范围缩放，否则拿到的宽高是 0。
            mapView.post { mapView.fitToPoints(drawn, fitPaddingPx) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun createTrackMapView(context: Context): MapView {
    configureOsmdroid(context)
    return MapView(context).apply {
        setTileSource(AmapTileSource)
        setMultiTouchControls(true)
        setUseDataConnection(true)
        setMinZoomLevel(MIN_ZOOM)
        setMaxZoomLevel(MAX_ZOOM)
        controller.setZoom(16.0)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        overlays.add(
            ScaleBarOverlay(this).apply {
                setAlignBottom(true)
                setAlignRight(false)
                setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric)
            }
        )
        overlays.add(
            CopyrightOverlay(context).apply {
                setCopyrightNotice("© 高德地图")
                setAlignBottom(true)
                setAlignRight(true)
                setTextColor(AndroidColor.rgb(0x5F, 0x63, 0x68))
            }
        )
    }
}

private var osmdroidConfigured = false

/** osmdroid 默认把缓存写到外部存储，Android 10 之后会因无权限导致瓦片下载失败，这里改到应用私有目录。 */
private fun configureOsmdroid(context: Context) {
    if (osmdroidConfigured) return
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    config.setUserAgentValue(context.packageName)
    val basePath = File(context.filesDir, "osmdroid").apply { mkdirs() }
    val tileCache = File(context.cacheDir, "osmdroid/tiles").apply { mkdirs() }
    config.setOsmdroidBasePath(basePath)
    config.setOsmdroidTileCache(tileCache)
    osmdroidConfigured = true
}

/**
 * 高德标准底图（style=8 带中文标注）。瓦片本身是 GCJ-02，所以轨迹点必须先转换。
 */
private object AmapTileSource : OnlineTileSourceBase(
    "AmapStandard",
    MIN_ZOOM.toInt(),
    MAX_ZOOM.toInt(),
    256,
    ".png",
    arrayOf("https://wprd01.is.autonavi.com/appmaptile")
) {
    private const val URL_TEMPLATE =
        "https://wprd0%d.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scl=1&style=8&x=%d&y=%d&z=%d"

    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        // 四个子域轮询，避免单域名并发过高
        val host = 1 + ((x + y) % 4)
        return String.format(Locale.US, URL_TEMPLATE, host, x, y, zoom)
    }
}

private fun MapView.renderTracks(
    tracks: List<MapTrack>,
    colorOf: (TransportMode) -> Color,
    lineWidthPx: Float,
    markerSizePx: Float
): List<GeoPoint> {
    overlays.removeAll { it is Polyline || it is Marker }

    val allPoints = ArrayList<GeoPoint>()
    val startIcon = ringIcon(START_COLOR, markerSizePx)
    val endIcon = ringIcon(END_COLOR, markerSizePx)

    tracks.forEach { track ->
        val points = track.points.map { it.toGeoPoint() }
        if (points.isEmpty()) return@forEach
        allPoints += points

        if (points.size >= 2) {
            overlays.add(
                Polyline(this).apply {
                    setPoints(points)
                    setGeodesic(false)
                    setInfoWindow(null)
                    outlinePaint.color = colorOf(track.mode).toArgb()
                    outlinePaint.strokeWidth = lineWidthPx
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                }
            )
        }
        overlays.add(buildMarker(points.first(), startIcon))
        overlays.add(buildMarker(points.last(), endIcon))
    }

    invalidate()
    return allPoints
}

private fun MapView.buildMarker(point: GeoPoint, icon: BitmapDrawable): Marker =
    Marker(this).apply {
        setPosition(point)
        setIcon(icon)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
    }

/** 白底 + 彩色圆环，在花色底图上也能一眼看到起点终点。 */
private fun MapView.ringIcon(color: Int, sizePx: Float): BitmapDrawable {
    val size = sizePx.toInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val strokeWidth = size * 0.26f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(center, center, center, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth
    paint.color = color
    canvas.drawCircle(center, center, center - strokeWidth / 2f, paint)

    return BitmapDrawable(resources, bitmap).apply {
        bitmap.density = resources.displayMetrics.densityDpi
    }
}

private fun MapView.fitToPoints(points: List<GeoPoint>, paddingPx: Int) {
    if (points.isEmpty()) return
    val box = BoundingBox.fromGeoPoints(points)
    if (box.latitudeSpan < 1e-6 && box.longitudeSpanWithDateLine < 1e-6) {
        controller.setZoom(17.0)
        controller.setCenter(points.first())
    } else {
        zoomToBoundingBox(box, false, paddingPx)
    }
}

private fun TrackPoint.toGeoPoint(): GeoPoint {
    val (latitude, longitude) = GeoTransform.wgs84ToGcj02(this.latitude, this.longitude)
    return GeoPoint(latitude, longitude)
}

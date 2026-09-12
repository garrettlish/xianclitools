package com.example.xiancli_tools.track

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.TrackAnalytics
import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TrackRepository
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.data.TransportMode

class TrackRecorderService : Service() {

    private val repository by lazy { TrackRepository(this) }
    private var locationManager: LocationManager? = null

    private var mode = TransportMode.WALK
    private var startMillis = 0L
    private var sessionId = 0L
    private val points = mutableListOf<TrackPoint>()
    private var distanceMeters = 0.0
    private var lastAccepted: TrackPoint? = null
    private var updateCounter = 0

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocation(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishRecording(stopSelf = true)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val requested = intent.getStringExtra(EXTRA_MODE)
                    ?.let { runCatching { TransportMode.valueOf(it) }.getOrNull() }
                    ?: TransportMode.WALK
                startRecording(requested)
                return START_NOT_STICKY
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun startRecording(requestedMode: TransportMode) {
        if (TrackRecorderState.state.value.recording) return
        mode = requestedMode
        startMillis = System.currentTimeMillis()
        sessionId = startMillis
        points.clear()
        distanceMeters = 0.0
        lastAccepted = null
        updateCounter = 0

        startForegroundCompat(buildNotification())
        publishState()

        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: run {
            Log.e(TAG, "无法获取 LocationManager")
            finishRecording(stopSelf = true)
            return
        }
        locationManager = manager
        val provider = pickProvider(manager)
        if (provider == null) {
            Log.e(TAG, "没有可用的定位提供者")
            finishRecording(stopSelf = true)
            return
        }
        runCatching {
            manager.requestLocationUpdates(
                provider,
                MIN_TIME_MS,
                MIN_DISTANCE_METERS,
                locationListener,
                Looper.getMainLooper()
            )
        }.onFailure {
            Log.e(TAG, "注册定位监听失败", it)
            finishRecording(stopSelf = true)
        }
    }

    private fun pickProvider(manager: LocationManager): String? = runCatching {
        when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> manager.getProviders(true).firstOrNull()
        }
    }.getOrNull()

    private fun handleLocation(location: Location) {
        val time = if (location.time > 0L) location.time else System.currentTimeMillis()
        val point = TrackPoint(location.latitude, location.longitude, time)
        val previous = lastAccepted
        if (previous == null) {
            points.add(point)
            lastAccepted = point
        } else {
            if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return
            val delta = TrackAnalytics.haversineMeters(previous, point)
            if (delta < MIN_DISTANCE_METERS) return
            distanceMeters += delta
            points.add(point)
            lastAccepted = point
        }

        updateCounter++
        if (updateCounter % PERSIST_EVERY == 0) {
            repository.saveActiveSession(buildSession(endMillis = 0L))
        }
        if (updateCounter % NOTIFY_EVERY == 0) {
            notify(buildNotification())
        }
        publishState()
    }

    private fun publishState() {
        TrackRecorderState.update(
            RecorderSnapshot(
                recording = true,
                mode = mode,
                startMillis = startMillis,
                distanceMeters = distanceMeters,
                pointCount = points.size,
                startPoint = points.firstOrNull(),
                lastPoint = lastAccepted
            )
        )
    }

    private fun finishRecording(stopSelf: Boolean) {
        stopLocationUpdates()
        if (points.isNotEmpty()) {
            val session = buildSession(endMillis = System.currentTimeMillis())
            repository.appendSession(session)
        }
        repository.clearActiveSession()
        TrackRecorderState.reset()
        if (stopSelf) {
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun buildSession(endMillis: Long) = TrackSession(
        id = sessionId,
        mode = mode,
        startMillis = startMillis,
        endMillis = endMillis,
        distanceMeters = distanceMeters,
        points = points.toList()
    )

    private fun stopLocationUpdates() {
        locationManager?.let { manager ->
            runCatching { manager.removeUpdates(locationListener) }
        }
        locationManager = null
    }

    override fun onDestroy() {
        if (TrackRecorderState.state.value.recording) {
            finishRecording(stopSelf = false)
        }
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        val id = NOTIFICATION_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(id, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TrackRecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            20,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPendingIntent = openIntent?.let {
            PendingIntent.getActivity(
                this,
                21,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val summary = "${TrackAnalytics.formatDistance(distanceMeters)} · ${points.size} 个点"
        return NotificationCompat.Builder(this, CHANNEL_TRACK)
            .setSmallIcon(R.drawable.ic_tool_route)
            .setContentTitle("正在记录${mode.label}轨迹")
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_stop, "结束记录", stopPendingIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_TRACK) != null) return
        val channel = NotificationChannel(
            CHANNEL_TRACK,
            "轨迹记录",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "记录轨迹时的常驻通知"
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "TrackRecorderService"
        const val ACTION_START = "com.example.xiancli_tools.action.START_TRACK"
        const val ACTION_STOP = "com.example.xiancli_tools.action.STOP_TRACK"
        const val EXTRA_MODE = "extra_transport_mode"

        private const val CHANNEL_TRACK = "track_recording"
        private const val NOTIFICATION_ID = 2001
        private const val MIN_TIME_MS = 3_000L
        private const val MIN_DISTANCE_METERS = 3f
        private const val MAX_ACCURACY_METERS = 100f
        private const val PERSIST_EVERY = 5
        private const val NOTIFY_EVERY = 5
    }
}

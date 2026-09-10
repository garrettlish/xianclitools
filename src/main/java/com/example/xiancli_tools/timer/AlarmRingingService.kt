package com.example.xiancli_tools.timer

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.xiancli_tools.data.RingtoneResolver
import com.example.xiancli_tools.data.SettingsRepository

class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            stopSelf()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(TimerScheduler.EXTRA_LABEL) ?: "定时"
        NotificationHelper.ensureChannels(this)
        startForegroundCompat(NotificationHelper.buildRingingNotification(this, label))
        startRinging()
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        val id = NotificationHelper.NOTIFICATION_ID_RINGING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun startRinging() {
        val settings = SettingsRepository(this)

        RingtoneResolver.resolveUri(this, settings)?.let { uri ->
            runCatching {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmRingingService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.onFailure { Log.e(TAG, "播放铃声失败", it) }
        }

        if (settings.vibrationEnabled) {
            val device = obtainVibrator()
            vibrator = device
            val pattern = longArrayOf(0L, 800L, 600L)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    device.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    device.vibrate(pattern, 0)
                }
            }.onFailure { Log.e(TAG, "震动失败", it) }
        }
    }

    private fun obtainVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    private fun stopRinging() {
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingingService"
        const val ACTION_STOP = "com.example.xiancli_tools.action.STOP_ALARM"
    }
}

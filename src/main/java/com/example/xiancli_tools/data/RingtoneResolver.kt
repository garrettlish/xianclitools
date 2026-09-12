package com.example.xiancli_tools.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object RingtoneResolver {

    fun resolveUri(context: Context, settings: SettingsRepository): Uri? =
        settings.ringtoneUri?.takeIf(::isCustomRingtone)
            ?: actualDefaultUri(context, RingtoneManager.TYPE_ALARM)
            ?: actualDefaultUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: actualDefaultUri(context, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    fun resolveTitle(context: Context, settings: SettingsRepository): String {
        settings.ringtoneUri?.takeIf(::isCustomRingtone)?.let { uri ->
            settings.ringtoneTitle?.takeIf { it.isNotBlank() }?.let { return it }
            titleOf(context, uri)?.let { return it }
        }
        val defaultUri = actualDefaultUri(context, RingtoneManager.TYPE_ALARM)
            ?: actualDefaultUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        return defaultUri?.let { titleOf(context, it) } ?: DEFAULT_TITLE
    }

    fun isCustomRingtone(uri: Uri?): Boolean {
        if (uri == null || uri.toString().isBlank()) return false
        return !(uri.scheme == "content" && uri.authority == "settings")
    }

    private fun actualDefaultUri(context: Context, type: Int): Uri? =
        runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, type) }
            .getOrNull()

    fun titleOf(context: Context, uri: Uri): String? =
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    const val DEFAULT_TITLE = "系统默认铃声"
}

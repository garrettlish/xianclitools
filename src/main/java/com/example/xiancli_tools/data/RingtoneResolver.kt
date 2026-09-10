package com.example.xiancli_tools.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object RingtoneResolver {

    fun resolveUri(context: Context, settings: SettingsRepository): Uri? =
        settings.ringtoneUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    fun resolveTitle(context: Context, settings: SettingsRepository): String {
        settings.ringtoneUri?.let { uri ->
            settings.ringtoneTitle?.takeIf { it.isNotBlank() }?.let { return it }
            titleOf(context, uri)?.let { return it }
        }
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        return defaultUri?.let { titleOf(context, it) } ?: DEFAULT_TITLE
    }

    fun titleOf(context: Context, uri: Uri): String? =
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    const val DEFAULT_TITLE = "系统默认铃声"
}

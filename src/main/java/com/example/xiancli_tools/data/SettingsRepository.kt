package com.example.xiancli_tools.data

import android.content.Context
import android.net.Uri

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, DEFAULT_VIBRATION)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    var ringtoneUri: Uri?
        get() = prefs.getString(KEY_RINGTONE_URI, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_RINGTONE_URI, value?.toString()).apply()

    var ringtoneTitle: String?
        get() = prefs.getString(KEY_RINGTONE_TITLE, null)
        set(value) = prefs.edit().putString(KEY_RINGTONE_TITLE, value).apply()

    var scheduledEndAt: Long
        get() = prefs.getLong(KEY_SCHEDULED_END_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_SCHEDULED_END_AT, value).apply()

    var scheduledLabel: String?
        get() = prefs.getString(KEY_SCHEDULED_LABEL, null)
        set(value) = prefs.edit().putString(KEY_SCHEDULED_LABEL, value).apply()

    private companion object {
        const val PREFS_NAME = "xiancli_tools_settings"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_RINGTONE_URI = "ringtone_uri"
        const val KEY_RINGTONE_TITLE = "ringtone_title"
        const val KEY_SCHEDULED_END_AT = "scheduled_end_at"
        const val KEY_SCHEDULED_LABEL = "scheduled_label"
        const val DEFAULT_VIBRATION = true
    }
}

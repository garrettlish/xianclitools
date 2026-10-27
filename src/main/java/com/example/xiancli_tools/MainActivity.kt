package com.example.xiancli_tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.xiancli_tools.data.BackupContainer
import com.example.xiancli_tools.data.BackupPayload
import com.example.xiancli_tools.data.SettingsRepository
import com.example.xiancli_tools.data.ThemeMode
import com.example.xiancli_tools.timer.NotificationHelper
import com.example.xiancli_tools.ui.AppRoot
import com.example.xiancli_tools.ui.theme.XianclitoolsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** 从别的应用推进来的备份文件，解析成功后交给界面弹合并确认。 */
    private var incomingBackup by mutableStateOf<BackupPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)
        setContent {
            val settings = remember { SettingsRepository(this) }
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            // 没手动切过就跟随系统；切过一次后以用户的选择为准。
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 状态栏 / 导航栏的图标是系统画的，不跟 Compose 主题走：
            // enableEdgeToEdge 只在启动时按系统 uiMode 定过一次，手动切主题得自己翻。
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            XianclitoolsTheme(darkTheme = darkTheme) {
                AppRoot(
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        val next = if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                        themeMode = next
                        settings.themeMode = next
                    },
                    incomingBackup = incomingBackup,
                    onIncomingBackupConsumed = { incomingBackup = null }
                )
            }
        }
        consume(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    /**
     * 解析推进来的备份。轨迹点撑起来可能有几十 MB，所以放后台线程，
     * 别让启动卡在读文件和解析上；解析是流式的，内存只占一条记录的量。
     */
    private fun consume(intent: Intent?) {
        if (intent == null) return
        lifecycleScope.launch {
            val payload = withContext(Dispatchers.IO) { readBackup(intent) } ?: return@launch
            incomingBackup = payload
        }
    }

    /**
     * 把「用其他应用打开」给的 Uri 解析成备份。
     *
     * 解析失败一律返回 null，让 App 按正常启动处理：
     * 用户点错一个文件时不该打不开应用。
     */
    private fun readBackup(intent: Intent?): BackupPayload? {
        if (intent == null) return null
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> streamOf(intent)
            else -> null
        }
        if (uri == null) return null
        return runCatching {
            contentResolver.openInputStream(uri)?.use { BackupContainer.read(it) }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
}

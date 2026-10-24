package com.example.xiancli_tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.xiancli_tools.data.BackupCodec
import com.example.xiancli_tools.data.BackupPayload
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
            XianclitoolsTheme {
                AppRoot(
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
     * 解析推进来的备份。轨迹点撑起来可能有几 MB，所以放后台线程，
     * 别让启动卡在读文件和解析上。
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
     * 用户点错一个 json 文件时不该打不开应用。
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
            contentResolver.openInputStream(uri)?.use { stream ->
                BackupCodec.decode(stream.readBytes().toString(Charsets.UTF_8))
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
}

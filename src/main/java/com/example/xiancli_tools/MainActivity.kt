package com.example.xiancli_tools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.xiancli_tools.timer.NotificationHelper
import com.example.xiancli_tools.ui.AppRoot
import com.example.xiancli_tools.ui.theme.XianclitoolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)
        setContent {
            XianclitoolsTheme {
                AppRoot()
            }
        }
    }
}

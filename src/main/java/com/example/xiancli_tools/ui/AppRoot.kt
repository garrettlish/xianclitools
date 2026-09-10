package com.example.xiancli_tools.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private sealed interface Screen {
    data object Home : Screen
    data object Timer : Screen
    data object PhoneStats : Screen
    data object NotificationSettings : Screen
}

@Composable
fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = when (screen) {
            Screen.NotificationSettings -> Screen.Timer
            else -> Screen.Home
        }
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            onOpenTimer = { screen = Screen.Timer },
            onOpenPhoneStats = { screen = Screen.PhoneStats }
        )
        Screen.Timer -> TimerScreen(
            onBack = { screen = Screen.Home },
            onOpenSettings = { screen = Screen.NotificationSettings }
        )
        Screen.PhoneStats -> PhoneStatsScreen(
            onBack = { screen = Screen.Home }
        )
        Screen.NotificationSettings -> NotificationSettingsScreen(
            onBack = { screen = Screen.Timer }
        )
    }
}

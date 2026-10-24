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
import com.example.xiancli_tools.data.BackupPayload

private sealed interface Screen {
    data object Home : Screen
    data object Timer : Screen
    data object Note : Screen
    data object Ledger : Screen
    data object Track : Screen
    /** 只带 id：点阵由地图页自己去仓库取，别在导航状态里揣着几十万个坐标。 */
    data class TrackMap(val sessionIds: List<Long>) : Screen
    data object Drink : Screen
    data object Trip : Screen
    data object Review : Screen

    /** [incoming] 是从系统「用其他应用打开」带进来的备份，非空时进页就弹合并确认。 */
    data class Backup(val incoming: BackupPayload? = null) : Screen
    data object NearbySync : Screen
    data object NotificationSettings : Screen
}

@Composable
fun AppRoot(
    incomingBackup: BackupPayload? = null,
    onIncomingBackupConsumed: () -> Unit = {}
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val context = LocalContext.current

    LaunchedEffect(incomingBackup) {
        if (incomingBackup != null) screen = Screen.Backup(incomingBackup)
    }

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
            is Screen.TrackMap -> Screen.Track
            // 直传是从备份页进来的，返回时回到备份页，数据可能已经被同步过，正好重取一次。
            Screen.NearbySync -> Screen.Backup()
            else -> Screen.Home
        }
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            onOpenTimer = { screen = Screen.Timer },
            onOpenNote = { screen = Screen.Note },
            onOpenLedger = { screen = Screen.Ledger },
            onOpenTrack = { screen = Screen.Track },
            onOpenDrink = { screen = Screen.Drink },
            onOpenTrip = { screen = Screen.Trip },
            onOpenReview = { screen = Screen.Review },
            onOpenBackup = { screen = Screen.Backup() }
        )
        Screen.Timer -> TimerScreen(
            onBack = { screen = Screen.Home },
            onOpenSettings = { screen = Screen.NotificationSettings }
        )
        Screen.Note -> NoteScreen(
            onBack = { screen = Screen.Home }
        )
        Screen.Ledger -> LedgerScreen(
            onBack = { screen = Screen.Home }
        )
        Screen.Track -> TrackScreen(
            onBack = { screen = Screen.Home },
            onOpenMap = { screen = Screen.TrackMap(it) }
        )
        is Screen.TrackMap -> TrackMapScreen(
            sessionIds = current.sessionIds,
            onBack = { screen = Screen.Track }
        )
        Screen.Drink -> DrinkScreen(
            onBack = { screen = Screen.Home }
        )
        Screen.Trip -> TripScreen(
            onBack = { screen = Screen.Home }
        )
        Screen.Review -> ReviewScreen(
            onBack = { screen = Screen.Home },
            onOpenNote = { screen = Screen.Note },
            onOpenDrink = { screen = Screen.Drink }
        )
        is Screen.Backup -> BackupScreen(
            onBack = { screen = Screen.Home },
            onOpenNearby = { screen = Screen.NearbySync },
            incoming = current.incoming,
            onIncomingConsumed = onIncomingBackupConsumed
        )
        Screen.NearbySync -> NearbySyncScreen(
            onBack = { screen = Screen.Backup() }
        )
        Screen.NotificationSettings -> NotificationSettingsScreen(
            onBack = { screen = Screen.Timer }
        )
    }
}

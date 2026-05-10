package com.svllvsx.notifyrelay

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import com.svllvsx.notifyrelay.keepalive.KeepAliveService
import com.svllvsx.notifyrelay.ui.NotifyRelayAppRoot
import com.svllvsx.notifyrelay.ui.theme.NotificationRelayTheme

class MainActivity : ComponentActivity() {
    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) KeepAliveService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NotifyRelayApp).container
        setContent {
            NotificationRelayTheme {
                Surface {
                    NotifyRelayAppRoot(
                        container = container,
                        requestSmsPermission = { smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                        requestPostNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
            }
        }
    }
}

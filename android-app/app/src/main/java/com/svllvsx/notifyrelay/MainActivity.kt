package com.svllvsx.notifyrelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import com.svllvsx.notifyrelay.ui.NotifyRelayAppRoot
import com.svllvsx.notifyrelay.ui.theme.NotificationRelayTheme

class MainActivity : ComponentActivity() {
    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NotifyRelayApp).container
        setContent {
            NotificationRelayTheme {
                Surface {
                    NotifyRelayAppRoot(
                        container = container,
                        requestSmsPermission = { smsPermissionLauncher.launch(android.Manifest.permission.RECEIVE_SMS) },
                    )
                }
            }
        }
    }
}

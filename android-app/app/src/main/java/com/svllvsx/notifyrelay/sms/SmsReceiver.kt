package com.svllvsx.notifyrelay.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.svllvsx.notifyrelay.NotifyRelayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val sender = messages.firstOrNull()?.originatingAddress
                val timestamp = messages.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
                val body = messages.joinToString("") { it.messageBody.orEmpty() }
                (context.applicationContext as NotifyRelayApp).container.saveSmsEventUseCase(sender, body, timestamp)
            } finally {
                pending.finish()
            }
        }
    }
}

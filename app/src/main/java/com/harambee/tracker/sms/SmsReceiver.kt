package com.harambee.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.harambee.tracker.container
import kotlinx.coroutines.launch

/** Picks up M-Pesa confirmations the moment they arrive. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Long messages arrive in several parts; join them per sender.
        val messages = parts.filterNotNull()
            .groupBy { it.displayOriginatingAddress ?: it.originatingAddress }
            .filterKeys { SmsSources.isMpesaSender(it) }
            .values
            .map { list -> list.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" } to list.first().timestampMillis }
        if (messages.isEmpty()) return

        val container = context.container
        val pending = goAsync()
        container.scope.launch {
            try {
                messages.forEach { (body, time) -> container.handleIncomingSms(body, time) }
            } finally {
                pending.finish()
            }
        }
    }
}

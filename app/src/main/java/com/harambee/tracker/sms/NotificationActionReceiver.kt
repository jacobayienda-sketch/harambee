package com.harambee.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.harambee.tracker.container
import kotlinx.coroutines.launch

/** Handles the "Add ✅" and "Not a contribution" buttons on a payment notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val contributionId = intent.getLongExtra(EXTRA_CONTRIBUTION_ID, -1)
        if (contributionId < 0) return
        val container = context.container
        val pending = goAsync()
        container.scope.launch {
            try {
                when (intent.action) {
                    ACTION_ADD -> {
                        val campaignId = intent.getLongExtra(EXTRA_CAMPAIGN_ID, -1)
                        val result = container.repository.confirm(contributionId, campaignId)
                        if (result != null) container.notifier.showConfirmed(result) else container.notifier.cancel(contributionId)
                    }
                    ACTION_REJECT -> {
                        container.repository.reject(contributionId)
                        container.notifier.cancel(contributionId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD = "com.harambee.tracker.ADD"
        const val ACTION_REJECT = "com.harambee.tracker.REJECT"
        const val EXTRA_CONTRIBUTION_ID = "contribution_id"
        const val EXTRA_CAMPAIGN_ID = "campaign_id"
    }
}

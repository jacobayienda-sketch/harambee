package com.harambee.tracker.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.harambee.tracker.MainActivity
import com.harambee.tracker.R
import com.harambee.tracker.Settings
import com.harambee.tracker.core.Phone
import com.harambee.tracker.core.Templates
import com.harambee.tracker.core.Money
import com.harambee.tracker.data.ConfirmResult
import com.harambee.tracker.data.Contribution
import com.harambee.tracker.data.ReviewInfo

class Notifier(private val context: Context, private val settings: Settings) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_PAYMENTS, "Incoming payments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pops up when M-Pesa money arrives so you can add it to a Harambee"
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Totals and updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New totals, ready to share to WhatsApp"
            },
        )
    }

    /** The "pop-up": a heads-up notification asking whether this payment is a contribution. */
    fun showPending(contribution: Contribution, info: ReviewInfo) {
        val amount = Money.formatKes(contribution.amountCents)
        val campaign = info.suggestedCampaign
        val details = buildList {
            when {
                info.listMatch != null -> add("Matches \"${info.listMatch.listName ?: info.listMatch.senderName}\" on the list — will tick ✅")
                info.earlier.isNotEmpty() -> add("Already gave ${Money.formatKes(info.earlier.sumOf { it.amountCents })} to this Harambee")
                campaign != null -> add("New contributor")
            }
            add(if (campaign != null) "Add to ${campaign.name}?" else "Tap to choose a Harambee")
        }.joinToString("\n")

        val builder = NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$amount from ${contribution.senderName}")
            .setContentText(details.lineSequence().last())
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openApp(contribution.id, MainActivity.EXTRA_REVIEW_ID, contribution.id))
            .setAutoCancel(true)
        if (campaign != null) {
            builder.addAction(0, "Add ✅", action(NotificationActionReceiver.ACTION_ADD, contribution.id, campaign.id))
        }
        builder.addAction(0, "Not a contribution", action(NotificationActionReceiver.ACTION_REJECT, contribution.id, -1))
        notify(contribution.id, builder)
    }

    /** After adding: the new total, one tap away from the WhatsApp share sheet. */
    fun showConfirmed(result: ConfirmResult) {
        val c = result.contribution
        val title = "✅ ${c.listName ?: c.senderName} ${Money.format(c.amountCents)} — ${result.campaign.name}"
        val text = "Total ${Money.formatKes(result.totalCents)} · Tap to share the update on WhatsApp"
        val share = openApp(c.id, MainActivity.EXTRA_SHARE_CAMPAIGN_ID, result.campaign.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(share)
            .addAction(0, "Share to WhatsApp", share)
            .setAutoCancel(true)
        val phone = Phone.normalize(c.senderPhone)
        if (phone != null && !Phone.isMasked(phone) && phone.length == 10) {
            // wa.me opens the chat in WhatsApp with the thank-you typed in.
            val message = Templates.fill(settings.thankYouTemplate.value.value, c.listName ?: c.senderName, c.amountCents, result.campaign.name, "")
            val uri = Uri.parse("https://wa.me/254${phone.substring(1)}?text=" + Uri.encode(message))
            val thanks = PendingIntent.getActivity(
                context, notificationId(c.id) * 2 + 1, Intent(Intent.ACTION_VIEW, uri),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Say thanks", thanks)
        }
        notify(c.id, builder)
    }

    fun showReversed(contribution: Contribution) {
        val builder = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reversed: ${Money.formatKes(contribution.amountCents)} from ${contribution.senderName}")
            .setContentText("Removed from the total")
            .setContentIntent(contribution.campaignId?.let { openApp(contribution.id, MainActivity.EXTRA_SHARE_CAMPAIGN_ID, it) })
            .setAutoCancel(true)
        notify(contribution.id, builder)
    }

    /** Payments found by the catch-up scan. */
    fun showCatchUp(count: Int) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_REVIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val builder = NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (count == 1) "1 M-Pesa payment to review" else "$count M-Pesa payments to review")
            .setContentText("Found while the app wasn't running")
            .setContentIntent(PendingIntent.getActivity(context, CATCH_UP_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(CATCH_UP_ID, builder.build())
    }

    fun cancel(contributionId: Long) = manager.cancel(notificationId(contributionId))

    private fun notify(contributionId: Long, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(notificationId(contributionId), builder.build())
    }

    private fun notificationId(contributionId: Long) = (contributionId % Int.MAX_VALUE).toInt()

    private fun openApp(contributionId: Long, extra: String, value: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction("$extra:$value")
            .putExtra(extra, value)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, notificationId(contributionId), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(action: String, contributionId: Long, campaignId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_CONTRIBUTION_ID, contributionId)
            .putExtra(NotificationActionReceiver.EXTRA_CAMPAIGN_ID, campaignId)
        val requestCode = notificationId(contributionId) * 2 + if (action == NotificationActionReceiver.ACTION_ADD) 0 else 1
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_PAYMENTS = "payments"
        const val CHANNEL_UPDATES = "updates"
        private const val CATCH_UP_ID = Int.MAX_VALUE - 1
    }
}

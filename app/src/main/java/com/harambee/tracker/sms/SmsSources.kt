package com.harambee.tracker.sms

import android.content.Context
import android.provider.Telephony
import com.harambee.tracker.container
import com.harambee.tracker.data.ImportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsSources {
    /**
     * Only messages from Safaricom's "MPESA" sender ID are trusted. Fraudsters send fake
     * "Confirmed. You have received…" texts from ordinary numbers; those are never counted.
     */
    fun isMpesaSender(address: String?): Boolean =
        address != null && address.uppercase().filter { it.isLetterOrDigit() } == "MPESA"

    /**
     * Reads M-Pesa messages already in the phone's inbox since [since]. New payments go to the
     * review list (suggested for [campaignId], or the active Harambee at that time) unless
     * [autoConfirm]; anything already recorded is skipped.
     */
    suspend fun scanInbox(context: Context, since: Long, campaignId: Long?, autoConfirm: Boolean = false): ImportSummary = withContext(Dispatchers.IO) {
        val repository = context.container.repository
        var summary = ImportSummary()
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return@withContext summary
        cursor.use {
            val address = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                if (!isMpesaSender(it.getString(address))) continue
                val text = it.getString(body) ?: continue
                summary += repository.ingestSms(text, it.getLong(date), autoConfirm = autoConfirm, forcedCampaignId = campaignId)
            }
        }
        summary
    }
}

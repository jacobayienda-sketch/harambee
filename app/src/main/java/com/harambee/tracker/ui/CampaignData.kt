package com.harambee.tracker.ui

import com.harambee.tracker.core.PayTo
import com.harambee.tracker.core.UpdateContent
import com.harambee.tracker.core.UpdateLine
import com.harambee.tracker.data.Campaign
import com.harambee.tracker.data.Collector
import com.harambee.tracker.data.ContributionRow
import com.harambee.tracker.data.Status

/** Shared conversions from database rows to what the message builders need. */
object CampaignData {
    fun payTo(campaign: Campaign, collectors: List<Collector>): List<PayTo> =
        (listOf(PayTo(campaign.payToName, campaign.payToNumber)) + collectors.map { PayTo(it.name, it.phone) })
            .filter { it.label().isNotBlank() }

    fun payToLabel(campaign: Campaign, collectors: List<Collector>): String =
        payTo(campaign, collectors).joinToString(" or ") { it.label() }

    fun content(campaign: Campaign, collectors: List<Collector>) =
        UpdateContent(campaign.intro, payTo(campaign, collectors), campaign.footer, campaign.targetCents)

    /** Paid lines and pledges, in list order. */
    fun lines(rows: List<ContributionRow>): List<UpdateLine> =
        rows.filter { it.contribution.status == Status.COUNTED || it.contribution.status == Status.PLEDGED }.map {
            UpdateLine(it.contribution.contributorKey, it.displayName, it.contribution.amountCents, it.contribution.status == Status.COUNTED, it.contribution.receivedAt)
        }

    /** "This phone" plus any collectors, for the "Received by" picker. */
    fun collectorLabel(campaign: Campaign?, collectors: List<Collector>, id: Long?): String =
        collectors.firstOrNull { it.id == id }?.name ?: campaign?.payToName?.ifBlank { null } ?: "This phone"
}

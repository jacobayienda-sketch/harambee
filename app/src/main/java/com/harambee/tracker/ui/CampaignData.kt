package com.harambee.tracker.ui

import com.harambee.tracker.core.NameDisplay
import com.harambee.tracker.core.PayTo
import com.harambee.tracker.core.Phone
import com.harambee.tracker.core.Tally
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

    /**
     * Paid lines and pledges, in list order, for sharing: names follow the Harambee's privacy
     * setting and anonymous contributors become "Well-wisher".
     */
    fun lines(rows: List<ContributionRow>, campaign: Campaign?): List<UpdateLine> =
        rows.filter { it.contribution.status == Status.COUNTED || it.contribution.status == Status.PLEDGED }.map {
            val k = it.contribution
            UpdateLine(
                key = k.contributorKey,
                name = publicName(it, campaign),
                amountCents = k.amountCents,
                paid = k.status == Status.COUNTED,
                time = k.receivedAt,
                isNew = k.status == Status.COUNTED && (campaign?.lastSharedAt == null || (k.countedAt ?: k.createdAt) > campaign.lastSharedAt),
            )
        }

    fun publicName(row: ContributionRow, campaign: Campaign?): String =
        NameDisplay.apply(row.displayName, campaign?.nameDisplay ?: NameDisplay.FULL, row.contribution.anonymous)

    fun tally(rows: List<ContributionRow>, campaign: Campaign): Tally {
        val counted = rows.filter { it.contribution.status == Status.COUNTED }
        return Tally(counted.sumOf { it.contribution.amountCents }, counted.map { it.contribution.contributorKey }.distinct().size, campaign.targetCents)
    }

    /** One entry per person (paid and pledged), for the contributors list, dashboard and exports. */
    data class Person(
        val key: String,
        val name: String,
        val phone: String?,
        val paidCents: Long,
        val pledgedCents: Long,
        val payments: Int,
        val lastPaid: Long?,
        val anonymous: Boolean,
        val rows: List<ContributionRow>,
    )

    fun people(rows: List<ContributionRow>): List<Person> =
        rows.filter { it.contribution.status == Status.COUNTED || it.contribution.status == Status.PLEDGED }
            .groupBy { it.contribution.contributorKey }
            .map { (key, list) ->
                val paid = list.filter { it.contribution.status == Status.COUNTED }
                Person(
                    key = key,
                    name = list.maxBy { it.contribution.receivedAt }.displayName,
                    phone = list.mapNotNull { it.contribution.senderPhone }.firstOrNull { !Phone.isMasked(it) } ?: list.firstNotNullOfOrNull { it.contribution.senderPhone },
                    paidCents = paid.sumOf { it.contribution.amountCents },
                    pledgedCents = list.filter { it.contribution.status == Status.PLEDGED }.sumOf { it.contribution.amountCents },
                    payments = paid.size,
                    lastPaid = paid.maxOfOrNull { it.contribution.receivedAt },
                    anonymous = list.any { it.contribution.anonymous },
                    rows = list,
                )
            }

    /** "This phone" plus any collectors, for the "Received by" picker. */
    fun collectorLabel(campaign: Campaign?, collectors: List<Collector>, id: Long?): String =
        collectors.firstOrNull { it.id == id }?.name ?: campaign?.payToName?.ifBlank { null } ?: "This phone"
}

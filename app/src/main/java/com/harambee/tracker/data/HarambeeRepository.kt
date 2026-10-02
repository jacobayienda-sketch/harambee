package com.harambee.tracker.data

import com.harambee.tracker.core.MpesaMessage
import com.harambee.tracker.core.MpesaParser
import com.harambee.tracker.core.MpesaReceipt
import com.harambee.tracker.core.Names
import com.harambee.tracker.core.ParsedList
import com.harambee.tracker.core.Phone
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface IngestResult {
    /** A new payment; [Contribution.status] is PENDING unless it was confirmed straight away. */
    data class Recorded(val contribution: Contribution, val confirmed: ConfirmResult?) : IngestResult
    data class Duplicate(val code: String) : IngestResult
    data class Reversed(val contribution: Contribution) : IngestResult
    data object Ignored : IngestResult
}

data class ConfirmResult(
    val contribution: Contribution,
    val campaign: Campaign,
    /** True when the payment ticked an existing pledge / WhatsApp list line instead of adding a line. */
    val tickedListEntry: Boolean,
    val totalCents: Long,
)

data class ImportSummary(
    val added: Int = 0,
    val ticked: Int = 0,
    val toReview: Int = 0,
    val duplicates: Int = 0,
    val reversed: Int = 0,
    val ignored: Int = 0,
) {
    operator fun plus(result: IngestResult): ImportSummary = when (result) {
        is IngestResult.Recorded -> when {
            result.confirmed == null -> copy(toReview = toReview + 1)
            result.confirmed.tickedListEntry -> copy(ticked = ticked + 1)
            else -> copy(added = added + 1)
        }
        is IngestResult.Duplicate -> copy(duplicates = duplicates + 1)
        is IngestResult.Reversed -> copy(reversed = reversed + 1)
        IngestResult.Ignored -> copy(ignored = ignored + 1)
    }

    fun describe(): String = buildList {
        if (added > 0) add("$added added")
        if (ticked > 0) add("$ticked ticked ✅ on the list")
        if (toReview > 0) add("$toReview waiting for your review")
        add("$duplicates already recorded")
        if (reversed > 0) add("$reversed reversed")
        if (ignored > 0) add("$ignored not incoming payments")
    }.joinToString(" · ")
}

data class ListImportSummary(val added: Int, val pledges: Int, val linked: Int, val skipped: Int)

/** What the treasurer sees before deciding on a payment. */
data class ReviewInfo(
    val suggestedCampaign: Campaign?,
    /** Lines the same person already has in the suggested Harambee. */
    val earlier: List<Contribution>,
    /** The pledge / list line this payment would tick, if any. */
    val listMatch: Contribution?,
)

class HarambeeRepository(private val dao: HarambeeDao) {
    /** SMS can arrive while an inbox scan is running; serialise writes so codes are checked once. */
    private val lock = Mutex()

    val campaignSummaries = dao.observeCampaignSummaries()
    val campaigns = dao.observeCampaigns()
    val pending = dao.observePending()
    val pendingCount = dao.observePendingCount()

    fun campaign(id: Long) = dao.observeCampaign(id)
    fun contributions(campaignId: Long) = dao.observeContributions(campaignId)
    fun contribution(id: Long) = dao.observeContribution(id)

    suspend fun getCampaign(id: Long) = dao.getCampaign(id)
    suspend fun getContribution(id: Long) = dao.getContribution(id)
    suspend fun totalFor(campaignId: Long) = dao.totalFor(campaignId)
    suspend fun earliestActiveStart() = dao.earliestActiveStart()

    suspend fun saveCampaign(campaign: Campaign): Long =
        if (campaign.id == 0L) dao.insertCampaign(campaign) else campaign.id.also { dao.updateCampaign(campaign) }

    suspend fun deleteCampaign(campaign: Campaign) = lock.withLock {
        dao.deleteManualContributions(campaign.id)
        dao.detachContributions(campaign.id)
        dao.deleteCampaign(campaign)
    }

    /**
     * Handles one SMS body. New payments are stored as PENDING (the code is taken, so the same
     * message can never be counted twice) unless [autoConfirm] is set and an active Harambee fits.
     * [smsTime] is used when the message's own date cannot be read.
     */
    suspend fun ingestSms(
        body: String,
        smsTime: Long,
        autoConfirm: Boolean = false,
        forcedCampaignId: Long? = null,
    ): IngestResult = lock.withLock {
        when (val message = MpesaParser.parse(body)) {
            MpesaMessage.NotRelevant -> IngestResult.Ignored
            is MpesaMessage.Reversal -> {
                val original = dao.findByCode(message.reversedCode)
                if (original != null && original.status != Status.REVERSED) {
                    val reversed = original.copy(status = Status.REVERSED, note = appendNote(original.note, "Reversed by Safaricom"))
                    dao.updateContribution(reversed)
                    IngestResult.Reversed(reversed)
                } else {
                    IngestResult.Ignored
                }
            }
            is MpesaMessage.Received -> record(message.receipt, body, smsTime, autoConfirm, forcedCampaignId)
        }
    }

    private suspend fun record(r: MpesaReceipt, raw: String?, fallbackTime: Long, autoConfirm: Boolean, forcedCampaignId: Long?): IngestResult {
        if (dao.findByCode(r.code) != null) return IngestResult.Duplicate(r.code)
        val time = r.transactionTime ?: fallbackTime
        val campaign = forcedCampaignId?.let { dao.getCampaign(it) } ?: dao.activeCampaignsAt(time).firstOrNull()
        val pending = Contribution(
            campaignId = campaign?.id,
            mpesaCode = r.code,
            amountCents = r.amountCents,
            senderName = r.senderName,
            senderPhone = r.senderPhone,
            contributorKey = Phone.contributorKey(r.senderName, r.senderPhone),
            receivedAt = time,
            source = Source.MPESA_SMS,
            status = Status.PENDING,
            rawMessage = raw,
        )
        val id = dao.insertContribution(pending)
        if (id == -1L) return IngestResult.Duplicate(r.code)
        val stored = pending.copy(id = id)
        val confirmed = if (autoConfirm && campaign != null) confirmLocked(stored, campaign, null) else null
        return IngestResult.Recorded(confirmed?.contribution ?: stored, confirmed)
    }

    suspend fun reviewInfo(contribution: Contribution, campaignId: Long? = contribution.campaignId): ReviewInfo {
        val campaign = campaignId?.let { dao.getCampaign(it) } ?: return ReviewInfo(null, emptyList(), null)
        val others = dao.contributionsFor(campaign.id).filter { it.id != contribution.id }
        val earlier = others.filter { c ->
            c.status == Status.COUNTED &&
                (c.contributorKey == contribution.contributorKey || Names.matches(c.listName ?: c.senderName, contribution.senderName))
        }
        return ReviewInfo(campaign, earlier, findListEntry(others, contribution.senderName, contribution.amountCents))
    }

    /** "Add ✅": counts a pending payment in [campaignId], ticking a matching pledge when there is one. */
    suspend fun confirm(contributionId: Long, campaignId: Long, listName: String? = null): ConfirmResult? = lock.withLock {
        // Only payments still waiting; a second tap on a notification must not count anything twice.
        val contribution = dao.getContribution(contributionId)?.takeIf { it.status == Status.PENDING } ?: return@withLock null
        val campaign = dao.getCampaign(campaignId) ?: return@withLock null
        confirmLocked(contribution, campaign, listName)
    }

    private suspend fun confirmLocked(contribution: Contribution, campaign: Campaign, listName: String?): ConfirmResult {
        val others = dao.contributionsFor(campaign.id).filter { it.id != contribution.id }
        val match = findListEntry(others, contribution.senderName, contribution.amountCents)
        val result = if (match != null) {
            // Tick the existing line, keeping its place and the name people wrote on the list.
            dao.deleteContribution(contribution)
            val ticked = match.copy(
                mpesaCode = contribution.mpesaCode,
                amountCents = contribution.amountCents,
                listName = listName?.takeIf { it.isNotBlank() } ?: match.listName ?: match.senderName,
                senderName = contribution.senderName,
                senderPhone = contribution.senderPhone,
                contributorKey = contribution.contributorKey,
                status = Status.COUNTED,
                source = contribution.source,
                rawMessage = contribution.rawMessage,
            )
            dao.updateContribution(ticked)
            ticked to true
        } else {
            val counted = contribution.copy(
                campaignId = campaign.id,
                status = Status.COUNTED,
                listName = listName?.takeIf { it.isNotBlank() } ?: contribution.listName,
            )
            dao.updateContribution(counted)
            counted to false
        }
        return ConfirmResult(result.first, campaign, result.second, dao.totalFor(campaign.id))
    }

    /** "Not a contribution": kept (so the code stays blocked) but never counted. */
    suspend fun reject(contributionId: Long) = lock.withLock {
        dao.getContribution(contributionId)?.takeIf { it.status == Status.PENDING }?.let { dao.updateContribution(it.copy(status = Status.EXCLUDED)) }
    }

    /**
     * An unpaid pledge, or a ✅ line imported from WhatsApp without an M-Pesa code, that a payment
     * belongs to. Only returns a match when it is unambiguous.
     */
    private fun findListEntry(candidates: List<Contribution>, senderName: String, amountCents: Long): Contribution? {
        val matches = candidates.filter { c ->
            c.mpesaCode == null &&
                (c.status == Status.PLEDGED || (c.status == Status.COUNTED && c.source == Source.WHATSAPP_LIST && c.amountCents == amountCents)) &&
                Names.matches(c.listName ?: c.senderName, senderName)
        }
        if (matches.size == 1) return matches.single()
        return matches.singleOrNull { it.amountCents == amountCents }
    }

    sealed interface AddResult {
        data class Added(val id: Long) : AddResult
        data object DuplicateCode : AddResult
    }

    suspend fun addManual(
        campaignId: Long,
        name: String,
        phone: String?,
        amountCents: Long,
        source: String,
        mpesaCode: String?,
        receivedAt: Long,
        note: String,
        pledged: Boolean,
    ): AddResult = lock.withLock {
        val code = mpesaCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (code != null && dao.findByCode(code) != null) return@withLock AddResult.DuplicateCode
        val normalizedPhone = Phone.normalize(phone)
        val contribution = Contribution(
            campaignId = campaignId,
            mpesaCode = code,
            amountCents = amountCents,
            senderName = name.trim(),
            senderPhone = normalizedPhone,
            listName = name.trim(),
            contributorKey = Phone.contributorKey(name, normalizedPhone),
            receivedAt = receivedAt,
            source = source,
            status = if (pledged) Status.PLEDGED else Status.COUNTED,
            note = note.trim(),
        )
        val id = dao.insertContribution(contribution)
        if (id == -1L) AddResult.DuplicateCode else AddResult.Added(id)
    }

    /**
     * Brings in a list that was already circulating on WhatsApp. Lines that are already recorded
     * (same person, same amount) are linked rather than added twice.
     */
    suspend fun importWhatsAppList(campaignId: Long, list: ParsedList): ListImportSummary = lock.withLock {
        val campaign = dao.getCampaign(campaignId) ?: return@withLock ListImportSummary(0, 0, 0, 0)
        val existing = dao.contributionsFor(campaignId)
        val claimed = mutableSetOf<Long>()
        var added = 0
        var pledges = 0
        var linked = 0
        var skipped = 0

        list.entries.forEachIndexed { index, entry ->
            val sameLine = existing.firstOrNull { c ->
                c.id !in claimed && c.amountCents == entry.amountCents &&
                    (c.status == Status.COUNTED || c.status == Status.PLEDGED) &&
                    (Names.normalized(c.listName ?: c.senderName) == Names.normalized(entry.name) || Names.matches(entry.name, c.senderName))
            }
            if (sameLine != null) {
                claimed += sameLine.id
                val upgraded = sameLine.copy(
                    listName = sameLine.listName ?: entry.name,
                    status = if (entry.paid && sameLine.status == Status.PLEDGED) Status.COUNTED else sameLine.status,
                )
                if (upgraded != sameLine) {
                    dao.updateContribution(upgraded)
                    linked++
                } else {
                    skipped++
                }
                return@forEachIndexed
            }
            dao.insertContribution(
                Contribution(
                    campaignId = campaignId,
                    amountCents = entry.amountCents,
                    senderName = entry.name,
                    listName = entry.name,
                    contributorKey = Phone.contributorKey(entry.name, null),
                    // Keeps the list's numbering order ahead of payments that arrive later.
                    receivedAt = campaign.startAt + index,
                    source = Source.WHATSAPP_LIST,
                    status = if (entry.paid) Status.COUNTED else Status.PLEDGED,
                ),
            )
            if (entry.paid) added++ else pledges++
        }

        val updated = campaign.copy(
            intro = campaign.intro.ifBlank { list.intro },
            footer = list.footer.ifBlank { campaign.footer },
        )
        if (updated != campaign) dao.updateCampaign(updated)
        ListImportSummary(added, pledges, linked, skipped)
    }

    suspend fun updateContribution(contribution: Contribution) = dao.updateContribution(contribution)
    suspend fun deleteContribution(contribution: Contribution) = dao.deleteContribution(contribution)

    suspend fun setAlias(key: String, displayName: String?) {
        if (displayName.isNullOrBlank()) dao.deleteAlias(key) else dao.upsertAlias(ContributorAlias(key, displayName.trim()))
    }

    private fun appendNote(note: String, addition: String) = if (note.isBlank()) addition else "$note\n$addition"
}

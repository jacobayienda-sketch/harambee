package com.harambee.tracker.data

import com.harambee.tracker.core.MpesaMessage
import com.harambee.tracker.core.MpesaParser
import com.harambee.tracker.core.MpesaReceipt
import com.harambee.tracker.core.Names
import com.harambee.tracker.core.ParsedList
import com.harambee.tracker.core.Phone
import kotlinx.coroutines.sync.Mutex
import androidx.room.withTransaction
import com.harambee.tracker.core.Milestones
import com.harambee.tracker.core.Money
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
    /** 25 / 50 / 75 / 100 when this payment carried the total past that share of the target. */
    val milestone: Int? = null,
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
    /** Same person, same amount within two hours: maybe sent twice by mistake. */
    val possibleDoubleSend: Contribution? = null,
)

class HarambeeRepository(private val db: HarambeeDatabase) {
    private val dao = db.dao()

    /** SMS can arrive while an inbox scan is running; serialise writes so codes are checked once. */
    private val lock = Mutex()

    /** One write at a time, and each one all-or-nothing: a crash half way never leaves a broken list. */
    private suspend fun <T> locked(block: suspend () -> T): T = lock.withLock { db.withTransaction { block() } }

    private suspend fun log(campaignId: Long?, contributionId: Long?, action: String, detail: String) =
        dao.insertActivity(ActivityEntry(campaignId = campaignId, contributionId = contributionId, action = action, detail = detail))

    private fun describe(c: Contribution) =
        "${c.listName ?: c.senderName} KES ${Money.format(c.amountCents)}" + (c.mpesaCode?.let { " ($it)" } ?: "")

    fun activity(campaignId: Long) = dao.observeActivity(campaignId)
    val allActivity = dao.observeAllActivity()

    fun campaignSummaries(todayStart: Long) = dao.observeCampaignSummaries(todayStart)
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

    suspend fun saveCampaign(campaign: Campaign): Long = locked {
        if (campaign.id == 0L) {
            dao.insertCampaign(campaign).also { log(it, null, "Created", "Harambee \"${campaign.name}\" created") }
        } else {
            val old = dao.getCampaign(campaign.id)
            dao.updateCampaign(campaign)
            val action = when {
                old?.isActive == true && !campaign.isActive -> "Closed"
                old?.isActive == false && campaign.isActive -> "Reopened"
                else -> "Edited"
            }
            if (old != campaign) log(campaign.id, null, action, "Harambee details ${action.lowercase()}")
            campaign.id
        }
    }

    suspend fun deleteCampaign(campaign: Campaign) = locked {
        log(campaign.id, null, "Deleted", "Harambee \"${campaign.name}\" deleted")
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
        collectorId: Long? = null,
    ): IngestResult = locked {
        when (val message = MpesaParser.parse(body)) {
            MpesaMessage.NotRelevant -> IngestResult.Ignored
            is MpesaMessage.Reversal -> {
                val original = dao.findByCode(message.reversedCode)
                if (original != null && original.status != Status.REVERSED) {
                    val reversed = original.copy(status = Status.REVERSED, note = appendNote(original.note, "Reversed by Safaricom"))
                    dao.updateContribution(reversed)
                    log(reversed.campaignId, reversed.id, "Reversed", describe(reversed) + " reversed by Safaricom")
                    IngestResult.Reversed(reversed)
                } else {
                    IngestResult.Ignored
                }
            }
            is MpesaMessage.Received -> record(message.receipt, body, smsTime, autoConfirm, forcedCampaignId, collectorId)
        }
    }

    private suspend fun record(r: MpesaReceipt, raw: String?, fallbackTime: Long, autoConfirm: Boolean, forcedCampaignId: Long?, collectorId: Long?): IngestResult {
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
            collectorId = collectorId,
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
        val double = earlier.firstOrNull {
            it.amountCents == contribution.amountCents && kotlin.math.abs(it.receivedAt - contribution.receivedAt) < 2 * 3600_000L
        }
        return ReviewInfo(campaign, earlier, findListEntry(others, contribution.senderName, contribution.amountCents), double)
    }

    /** "Add ✅": counts a pending payment in [campaignId], ticking a matching pledge when there is one. */
    suspend fun confirm(contributionId: Long, campaignId: Long, listName: String? = null): ConfirmResult? = locked {
        // Only payments still waiting; a second tap on a notification must not count anything twice.
        val contribution = dao.getContribution(contributionId)?.takeIf { it.status == Status.PENDING } ?: return@locked null
        val campaign = dao.getCampaign(campaignId) ?: return@locked null
        confirmLocked(contribution, campaign, listName)
    }

    private suspend fun confirmLocked(contribution: Contribution, campaign: Campaign, listName: String?): ConfirmResult {
        val before = dao.totalFor(campaign.id)
        val now = System.currentTimeMillis()
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
                collectorId = contribution.collectorId,
                countedAt = now,
            )
            dao.updateContribution(ticked)
            ticked to true
        } else {
            val counted = contribution.copy(
                campaignId = campaign.id,
                status = Status.COUNTED,
                listName = listName?.takeIf { it.isNotBlank() } ?: contribution.listName,
                countedAt = now,
            )
            dao.updateContribution(counted)
            counted to false
        }
        log(campaign.id, result.first.id, if (result.second) "Ticked ✅" else "Added ✅", describe(result.first))
        val after = dao.totalFor(campaign.id)
        return ConfirmResult(result.first, campaign, result.second, after, Milestones.crossed(before, after, campaign.targetCents))
    }

    /** "Not a contribution": kept (so the code stays blocked) but never counted. */
    suspend fun reject(contributionId: Long) = locked {
        dao.getContribution(contributionId)?.takeIf { it.status == Status.PENDING }?.let {
            dao.updateContribution(it.copy(status = Status.EXCLUDED))
            log(it.campaignId, it.id, "Not a contribution", describe(it))
        }
    }

    /** Undo for "Not a contribution": puts the payment back in the review list. */
    suspend fun returnToReview(contributionId: Long) = locked {
        dao.getContribution(contributionId)?.takeIf { it.status == Status.EXCLUDED && it.mpesaCode != null }?.let {
            dao.updateContribution(it.copy(status = Status.PENDING))
            log(it.campaignId, it.id, "Back to review", describe(it))
        }
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
        collectorId: Long? = null,
    ): AddResult = locked {
        val code = mpesaCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (code != null && dao.findByCode(code) != null) return@locked AddResult.DuplicateCode
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
            collectorId = collectorId,
            countedAt = if (pledged) null else System.currentTimeMillis(),
        )
        val id = dao.insertContribution(contribution)
        if (id == -1L) return@locked AddResult.DuplicateCode
        log(campaignId, id, if (pledged) "Pledge added" else "Added ✅", "${Source.label(source)}: " + describe(contribution))
        AddResult.Added(id)
    }

    /**
     * Brings in a list that was already circulating on WhatsApp. Lines that are already recorded
     * (same person, same amount) are linked rather than added twice.
     */
    suspend fun importWhatsAppList(campaignId: Long, list: ParsedList): ListImportSummary = locked {
        val campaign = dao.getCampaign(campaignId) ?: return@locked ListImportSummary(0, 0, 0, 0)
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
                    countedAt = sameLine.countedAt ?: if (entry.paid) System.currentTimeMillis() else null,
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
                    countedAt = if (entry.paid) System.currentTimeMillis() else null,
                ),
            )
            if (entry.paid) added++ else pledges++
        }

        val updated = campaign.copy(
            intro = campaign.intro.ifBlank { list.intro },
            footer = list.footer.ifBlank { campaign.footer },
        )
        if (updated != campaign) dao.updateCampaign(updated)
        log(campaignId, null, "List imported", "WhatsApp list: $added paid, $pledges pledges added, $linked matched")
        ListImportSummary(added, pledges, linked, skipped)
    }

    /** Saves a correction; [reason] is kept in the activity history. */
    suspend fun updateContribution(edited: Contribution, reason: String = "") = locked {
        val old = dao.getContribution(edited.id) ?: return@locked
        val contribution = if (edited.status == Status.COUNTED && edited.countedAt == null) edited.copy(countedAt = System.currentTimeMillis()) else edited
        dao.updateContribution(contribution)
        val changes = buildList {
            if (old.amountCents != contribution.amountCents) add("amount ${Money.format(old.amountCents)} → ${Money.format(contribution.amountCents)}")
            if ((old.listName ?: old.senderName) != (contribution.listName ?: contribution.senderName)) {
                add("name \"${old.listName ?: old.senderName}\" → \"${contribution.listName ?: contribution.senderName}\"")
            }
            if (old.status != contribution.status) add("${statusWord(old.status)} → ${statusWord(contribution.status)}")
            if (old.campaignId != contribution.campaignId) add("moved to another Harambee")
            if (old.collectorId != contribution.collectorId) add("received-by changed")
            if (old.note != contribution.note) add("note edited")
            if (old.anonymous != contribution.anonymous) add(if (contribution.anonymous) "made anonymous" else "name shown again")
        }
        if (changes.isNotEmpty()) {
            val why = reason.trim().takeIf { it.isNotEmpty() }?.let { " — Reason: $it" } ?: ""
            log(contribution.campaignId, contribution.id, "Corrected", describe(old) + ": " + changes.joinToString(", ") + why)
            if (old.campaignId != contribution.campaignId) log(old.campaignId, contribution.id, "Moved out", describe(old))
        }
    }

    suspend fun deleteContribution(contribution: Contribution, reason: String = "") = locked {
        dao.deleteContribution(contribution)
        log(contribution.campaignId, contribution.id, "Deleted", describe(contribution) + (reason.trim().takeIf { it.isNotEmpty() }?.let { " — Reason: $it" } ?: ""))
    }

    /** Typed entries that look like one already recorded (same person and amount within 3 days). */
    suspend fun possibleDuplicates(campaignId: Long, name: String, amountCents: Long, at: Long): List<Contribution> =
        dao.contributionsFor(campaignId).filter { c ->
            c.amountCents == amountCents &&
                c.status in setOf(Status.COUNTED, Status.PLEDGED, Status.PENDING) &&
                kotlin.math.abs(c.receivedAt - at) < 3 * 24 * 3600_000L &&
                (Names.normalized(c.listName ?: c.senderName) == Names.normalized(name) ||
                    Names.matches(name, c.senderName) || Names.matches(c.listName ?: c.senderName, name))
        }

    /** Remembers that an update went out, so the next "new since last update" starts here. */
    suspend fun markShared(campaignId: Long) = locked {
        dao.getCampaign(campaignId)?.let { dao.updateCampaign(it.copy(lastSharedAt = System.currentTimeMillis())) }
    }

    // People (contributors)

    /** One name for every entry from this person in this Harambee. */
    suspend fun renamePerson(campaignId: Long, key: String, name: String) = locked {
        dao.setListNameForPerson(campaignId, key, name.trim())
        log(campaignId, null, "Renamed", "Contributor now shown as \"${name.trim()}\"")
    }

    suspend fun setAnonymous(campaignId: Long, key: String, anonymous: Boolean, displayName: String) = locked {
        dao.setAnonymousForPerson(campaignId, key, anonymous)
        log(campaignId, null, if (anonymous) "Made anonymous" else "Name shown", displayName)
    }

    /** Joins two entries that are the same person (e.g. one from M-Pesa, one typed by hand). */
    suspend fun mergePeople(campaignId: Long, fromKey: String, intoKey: String, fromName: String, intoName: String) = locked {
        dao.mergePerson(campaignId, fromKey, intoKey, intoName)
        log(campaignId, null, "Merged", "\"$fromName\" merged into \"$intoName\"")
    }

    private fun statusWord(status: String) = when (status) {
        Status.COUNTED -> "counted"
        Status.PLEDGED -> "pledge"
        Status.EXCLUDED -> "not counted"
        Status.REVERSED -> "reversed"
        else -> "waiting"
    }

    suspend fun setAlias(key: String, displayName: String?) {
        if (displayName.isNullOrBlank()) dao.deleteAlias(key) else dao.upsertAlias(ContributorAlias(key, displayName.trim()))
    }

    // Collectors

    fun collectors(campaignId: Long) = dao.observeCollectors(campaignId)

    suspend fun addCollector(campaignId: Long, name: String, phone: String) = locked {
        dao.insertCollector(Collector(campaignId = campaignId, name = name.trim(), phone = Phone.normalize(phone) ?: ""))
        log(campaignId, null, "Collector added", name.trim())
    }

    suspend fun deleteCollector(collector: Collector) = locked {
        dao.clearCollector(collector.id)
        dao.deleteCollector(collector)
        log(collector.campaignId, null, "Collector removed", collector.name)
    }

    // Backup

    suspend fun snapshot(): BackupData = locked {
        BackupData(
            campaigns = dao.allCampaigns(),
            contributions = dao.allContributions(),
            aliases = dao.allAliases(),
            collectors = dao.allCollectors(),
            members = dao.allMembers(),
            activity = dao.allActivity(),
        )
    }

    /** Replaces everything with [data] in one transaction; if anything fails nothing changes. */
    suspend fun restore(data: BackupData, reason: String = "Restored from backup") = locked {
        dao.clearContributions()
        dao.clearCollectors()
        dao.clearCampaigns()
        dao.clearAliases()
        dao.clearMembers()
        dao.clearActivity()
        dao.insertCampaigns(data.campaigns)
        dao.insertContributions(data.contributions)
        dao.insertAliases(data.aliases)
        dao.insertCollectors(data.collectors)
        dao.insertMembers(data.members)
        dao.insertActivities(data.activity)
        log(null, null, reason.substringBefore(" "), "$reason: ${data.campaigns.size} Harambees, ${data.contributions.size} records")
    }

    suspend fun deleteEverything() = restore(BackupData(), "Deleted everything")

    // Members

    val groups = dao.observeGroups()
    fun members(group: String) = dao.observeMembers(group)

    /** Adds people not already in the group (matched by phone or name). Returns how many were added. */
    suspend fun addMembers(group: String, people: List<Pair<String, String?>>): Int {
        val existing = dao.membersOf(group)
        val existingNames = existing.map { Names.normalized(it.name) }.toMutableSet()
        val existingPhones = existing.mapNotNull { Phone.normalize(it.phone) }.toMutableSet()
        val fresh = people.filter { (name, phone) ->
            val p = Phone.normalize(phone)
            val key = Names.normalized(name)
            val isNew = key.isNotEmpty() && key !in existingNames && (p == null || p !in existingPhones)
            if (isNew) {
                existingNames += key
                p?.let { existingPhones += it }
            }
            isNew
        }
        dao.insertMembers(fresh.map { (name, phone) -> Member(groupName = group.trim(), name = name.trim(), phone = Phone.normalize(phone)) })
        return fresh.size
    }

    /** Builds a members list from everyone who has contributed to a Harambee. */
    suspend fun addContributorsAsMembers(campaignId: Long, group: String): Int {
        val rows = dao.contributionsFor(campaignId).filter { it.status == Status.COUNTED || it.status == Status.PLEDGED }
        val people = rows.map { (it.listName ?: it.senderName) to it.senderPhone?.takeUnless { p -> Phone.isMasked(p) } }
        return addMembers(group, people)
    }

    suspend fun updateMember(member: Member) = dao.updateMember(member)
    suspend fun deleteMember(member: Member) = dao.deleteMember(member)

    private fun appendNote(note: String, addition: String) = if (note.isBlank()) addition else "$note\n$addition"
}

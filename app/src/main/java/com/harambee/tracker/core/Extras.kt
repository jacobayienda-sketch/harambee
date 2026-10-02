package com.harambee.tracker.core

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Message templates; {name}, {amount}, {harambee}, {payto} are filled in. */
object Templates {
    const val THANK_YOU = "Asante sana {name} 🙏 We have received your contribution of KES {amount} towards {harambee}. God bless you."
    const val PLEDGE_REMINDER = "Hello {name}, kind reminder of your pledge of KES {amount} towards {harambee}. Please send to {payto}. Thank you 🙏"
    const val MEMBER_REMINDER = "Hello {name}, kind reminder to send your contribution{amount_part} towards {harambee} to {payto}. Thank you 🙏"

    fun fill(template: String, name: String, amountCents: Long?, harambee: String, payTo: String): String = template
        .replace("{name}", name)
        .replace("{amount_part}", amountCents?.let { " of KES ${Money.format(it)}" } ?: "")
        .replace("{amount}", amountCents?.let { Money.format(it) } ?: "")
        .replace("{harambee}", harambee)
        .replace("{payto}", payTo.ifBlank { "the treasurer" })

    /** A list for the group: who still owes, without singling anyone out in private. */
    fun pendingList(title: String, lines: List<Pair<String, Long?>>, payTo: String): String = buildString {
        append("*").append(title).append("*\n")
        lines.forEachIndexed { i, (name, amount) ->
            append(i + 1).append(". ").append(name)
            if (amount != null && amount > 0) append(" ").append(Money.format(amount))
            append("\n")
        }
        if (payTo.isNotBlank()) append("\nKindly send to *").append(payTo).append("*")
        append("\nThank you 🙏")
    }
}

data class MemberRef(val id: Long, val name: String, val phone: String?)
data class PaidRef(val listName: String, val senderName: String, val phone: String?, val amountCents: Long)

/** Works out which members of a group have contributed to a Harambee. */
object MemberMatcher {
    /** Member id -> amount paid. Members missing from the map have not paid. */
    fun paidByMember(members: List<MemberRef>, paid: List<PaidRef>): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        for (p in paid) {
            val member = find(members, p) ?: continue
            result[member.id] = (result[member.id] ?: 0) + p.amountCents
        }
        return result
    }

    private fun find(members: List<MemberRef>, p: PaidRef): MemberRef? {
        val phone = Phone.normalize(p.phone)
        if (phone != null && !Phone.isMasked(phone)) {
            members.singleOrNull { Phone.normalize(it.phone) == phone }?.let { return it }
        }
        val listKey = Names.normalized(p.listName)
        members.filter { Names.normalized(it.name) == listKey }.singleOrNull()?.let { return it }
        val byName = members.filter { Names.matches(it.name, p.senderName) || Names.matches(it.name, p.listName) }
        if (byName.size == 1) return byName.single()
        // Masked numbers ("0723***873") still narrow down people with similar names.
        if (phone != null && Phone.isMasked(phone) && byName.size > 1) {
            val prefix = phone.substringBefore('*')
            val suffix = phone.substringAfterLast('*')
            return byName.singleOrNull { m -> Phone.normalize(m.phone)?.let { it.startsWith(prefix) && it.endsWith(suffix) } == true }
        }
        return null
    }
}

/** Reads a pasted list of members: one per line, optional numbering and phone number. */
object RosterParser {
    private val numbering = Regex("""^\s*\d{1,4}\s*[.)]\s*""")
    private val phone = Regex("""(?:\+?254|0)\s?[17]\d{2}\s?\d{3}\s?\d{3}""")

    fun parse(text: String): List<Pair<String, String?>> = text.lines().mapNotNull { raw ->
        var line = raw.replace(numbering, "").replace("✅", "").trim()
        val number = phone.find(line)?.value
        if (number != null) line = line.replace(number, " ")
        // Drop a trailing amount ("Eliud Murkomen 1,000") if the line came from a contribution list.
        line = line.replace(Regex("""[\s\-–:]*(?:KES|Ksh)?\s*\d[\d,]*(?:\.\d+)?\s*(?:/=|/-)?\s*$""", RegexOption.IGNORE_CASE), "")
        val name = line.trim().trim('-', '–', ':', ',').trim().replace(Regex("\\s+"), " ")
        if (name.none { it.isLetter() }) null else name to Phone.normalize(number)
    }.distinctBy { Names.normalized(it.first) }
}

data class ReportData(
    val name: String,
    val firstPayment: Long?,
    val lastPayment: Long?,
    val lines: List<UpdateLine>,
    val byMethod: List<Pair<String, Long>>,
    val byCollector: List<Pair<String, Long>>,
    val targetCents: Long?,
    val membersPaid: Int?,
    val membersTotal: Int?,
    val footer: String,
)

/** The summary shared when a Harambee closes. */
object ClosingReport {
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).withZone(MpesaParser.NAIROBI)

    fun build(d: ReportData, combineRepeat: Boolean = true): String = buildString {
        val paid = WhatsAppUpdateBuilder.arrange(d.lines.filter { it.paid }, combineRepeat, sortByAmount = false)
        val pledges = d.lines.filter { !it.paid }
        val total = paid.sumOf { it.amountCents }

        append("*").append(d.name).append(" — Final report*\n")
        if (d.firstPayment != null && d.lastPayment != null) {
            val from = date.format(Instant.ofEpochMilli(d.firstPayment))
            val to = date.format(Instant.ofEpochMilli(d.lastPayment))
            append("Collected: ").append(if (from == to) from else "$from – $to").append("\n")
        }
        append("\n*Total received: KES ").append(Money.format(total)).append("*\n")
        append("Contributors: ").append(paid.map { it.key }.distinct().size).append("\n")
        val target = d.targetCents
        if (target != null && target > 0) {
            append("Target: KES ").append(Money.format(target)).append(" (").append(total * 100 / target).append("%)\n")
        }
        if (d.byMethod.size > 1) {
            append("By method: ").append(d.byMethod.joinToString(" · ") { "${it.first} ${Money.format(it.second)}" }).append("\n")
        }
        if (d.byCollector.size > 1) {
            append("Received by: ").append(d.byCollector.joinToString(" · ") { "${it.first} ${Money.format(it.second)}" }).append("\n")
        }
        if (pledges.isNotEmpty()) {
            append("Unpaid pledges: KES ").append(Money.format(pledges.sumOf { it.amountCents }))
                .append(" (").append(pledges.size).append(")\n")
        }
        if (d.membersPaid != null && d.membersTotal != null && d.membersTotal > 0) {
            append("Members contributed: ").append(d.membersPaid).append(" of ").append(d.membersTotal).append("\n")
        }
        append("\n     *Contributors*\n")
        paid.forEachIndexed { i, l -> append(i + 1).append(". ").append(l.name).append(" ").append(Money.format(l.amountCents)).append(" ✅\n") }
        if (d.footer.isNotBlank()) append("\n").append(d.footer.trim())
    }.trimEnd()
}

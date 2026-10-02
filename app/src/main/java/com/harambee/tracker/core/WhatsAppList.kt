package com.harambee.tracker.core

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line of a WhatsApp contribution list. */
data class ListEntry(
    val name: String,
    val amountCents: Long,
    /** ✅ — money received. Without it the line is a pledge. */
    val paid: Boolean,
)

data class ParsedList(
    val intro: String,
    val entries: List<ListEntry>,
    val footer: String,
)

/**
 * Reads a contribution list that has been circulating on WhatsApp, e.g.
 *
 *     *Contribution List*
 *     1. Eliud Murkomen 1,000 ✅
 *     38. John cheruyot 1,000
 *     72.
 *     Thanks for your generous contribution 🙏
 */
object WhatsAppListParser {
    private val line = Regex("""^\s*\d{1,4}\s*[.)]\s*(.*?)\s*$""")
    // The amount is the last number on the line; anything after it (✅, "paid") has no digits.
    private val nameAmount = Regex("""^(.+?)\s*[-–:=]?\s*(?:(?:KES|Kshs|Ksh)\.?\s*)?(\d[\d,]*(?:\.\d{1,2})?)\s*(?:/=|/-)?\s*(\D*)$""", RegexOption.IGNORE_CASE)
    private val paidMarks = listOf("✅", "✔", "☑", "👍", "paid", "pd")
    private val heading = Regex("""^\W*(contribution|contributors|michango|list)\b.*$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedList {
        val lines = text.lines()
        val numbered = lines.indices.filter { line.matches(lines[it]) }
        if (numbered.isEmpty()) return ParsedList(text.trim(), emptyList(), "")

        val first = numbered.first()
        val last = numbered.last()
        val introLines = lines.subList(0, first).toMutableList()
        // Drop a trailing "*Contribution List*" heading from the intro; the builder adds its own.
        while (introLines.isNotEmpty() && (introLines.last().isBlank() || heading.matches(introLines.last().replace("*", "").trim()))) {
            introLines.removeAt(introLines.lastIndex)
        }

        val entries = numbered.mapNotNull { idx -> parseLine(line.matchEntire(lines[idx])!!.groupValues[1]) }
        val footer = lines.subList(last + 1, lines.size).joinToString("\n").trim()
        return ParsedList(introLines.joinToString("\n").trim(), entries, footer)
    }

    internal fun parseLine(content: String): ListEntry? {
        if (content.isBlank()) return null
        val m = nameAmount.matchEntire(content) ?: return null
        val name = m.groupValues[1].trim().trimEnd('-', '–', ':', '=', '.').trim()
        val cents = Money.parseToCents(m.groupValues[2]) ?: return null
        if (name.isBlank() || cents <= 0) return null
        val rest = m.groupValues[3]
        val paid = paidMarks.any { rest.contains(it, ignoreCase = true) }
        return ListEntry(name.replace(Regex("\\s+"), " "), cents, paid)
    }
}

/** One contribution as it appears in an update. [key] identifies the person (phone or name). */
data class UpdateLine(val key: String, val name: String, val amountCents: Long, val paid: Boolean, val time: Long)

/** A number people can send money to. */
data class PayTo(val name: String, val number: String) {
    fun label(): String = listOf(name.trim(), Phone.pretty(number).ifBlank { number.trim() }).filter { it.isNotBlank() }.joinToString(" ")
}

data class UpdateOptions(
    val showTotal: Boolean = true,
    val showTarget: Boolean = true,
    val showPledges: Boolean = true,
    val addNextNumber: Boolean = true,
    val showDate: Boolean = false,
    val sortByAmount: Boolean = false,
    /** One line per person: two payments of 500 show as "Name 1,000 ✅". */
    val combineRepeat: Boolean = true,
    /** null = whole list; 0 = totals only; n = only the latest n names (for very long lists). */
    val listLimit: Int? = null,
)

data class UpdateContent(
    val intro: String,
    val payTo: List<PayTo>,
    val footer: String,
    val targetCents: Long?,
)

/** Produces the text that gets pasted into the WhatsApp group. */
object WhatsAppUpdateBuilder {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.US).withZone(MpesaParser.NAIROBI)

    /** Lines in list order, with repeat payments merged when [combine] is set. */
    fun arrange(lines: List<UpdateLine>, combine: Boolean, sortByAmount: Boolean): List<UpdateLine> {
        val merged = if (!combine) lines else lines
            .groupBy { it.key to it.paid }
            .values
            .map { group ->
                val first = group.minBy { it.time }
                first.copy(amountCents = group.sumOf { it.amountCents })
            }
        return if (sortByAmount) merged.sortedWith(compareByDescending<UpdateLine> { it.amountCents }.thenBy { it.time })
        else merged.sortedBy { it.time }
    }

    fun build(content: UpdateContent, lines: List<UpdateLine>, options: UpdateOptions, now: Long): String {
        val sb = StringBuilder()
        val intro = content.intro.trim()
        if (intro.isNotEmpty()) sb.append(intro).append("\n")
        val introDigits = intro.filter { it.isDigit() }
        // Numbers already written in the appeal text are not repeated.
        val payTo = content.payTo.filter { p ->
            val digits = p.number.filter { it.isDigit() }
            p.label().isNotBlank() && !(digits.length >= 9 && introDigits.contains(digits.takeLast(9)))
        }
        if (payTo.isNotEmpty()) {
            if (intro.isNotEmpty()) sb.append("\n")
            sb.append("Send your contribution to ").append(payTo.joinToString(" or ") { "*${it.label()}*" }).append("\n")
        }
        if (sb.isNotEmpty()) sb.append("\n")

        val ordered = arrange(lines.filter { it.paid || options.showPledges }, options.combineRepeat, options.sortByAmount)
        val limit = options.listLimit
        if (limit != 0) {
            sb.append("     *Contribution List*")
            if (options.showDate) sb.append("\n_Updated ").append(dateFormat.format(Instant.ofEpochMilli(now))).append("_")
            sb.append("\n")
            val hidden = if (limit != null && limit < ordered.size) ordered.size - limit else 0
            if (hidden > 0) sb.append("_…").append(hidden).append(" earlier names not shown_\n")
            ordered.forEachIndexed { i, l ->
                if (i < hidden) return@forEachIndexed
                sb.append(i + 1).append(". ").append(l.name).append(" ").append(Money.format(l.amountCents))
                if (l.paid) sb.append(" ✅")
                sb.append("\n")
            }
            if (options.addNextNumber) sb.append(ordered.size + 1).append(". \n")
        } else if (options.showDate) {
            sb.append("_Updated ").append(dateFormat.format(Instant.ofEpochMilli(now))).append("_\n")
        }

        val paidTotal = lines.filter { it.paid }.sumOf { it.amountCents }
        val pledged = lines.filter { !it.paid }.sumOf { it.amountCents }
        if (options.showTotal || limit == 0) {
            if (limit != 0) sb.append("\n")
            sb.append("*Total received: KES ").append(Money.format(paidTotal)).append("*")
            if (limit == 0) sb.append("\nContributors: ").append(lines.filter { it.paid }.map { it.key }.distinct().size)
            if (options.showPledges && pledged > 0) sb.append("\nPledges pending: KES ").append(Money.format(pledged))
            val target = content.targetCents
            if (options.showTarget && target != null && target > 0) {
                val pct = (paidTotal * 100 / target).coerceAtLeast(0)
                sb.append("\nTarget: KES ").append(Money.format(target)).append(" (").append(pct).append("%)")
                val balance = target - paidTotal
                if (balance > 0) sb.append("\nBalance: KES ").append(Money.format(balance))
                else sb.append("\nTarget reached 🎉")
            }
            sb.append("\n")
        }
        val footer = content.footer.trim()
        if (footer.isNotEmpty()) sb.append(if (options.showTotal || limit == 0) "\n" else "").append(footer).append("\n")
        return sb.toString().trimEnd()
    }
}

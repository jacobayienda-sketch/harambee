package com.harambee.tracker.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

data class MpesaReceipt(
    val code: String,
    val amountCents: Long,
    val senderName: String,
    val senderPhone: String?,
    /** Time stated inside the message, if it could be read. */
    val transactionTime: Long?,
)

sealed interface MpesaMessage {
    data class Received(val receipt: MpesaReceipt) : MpesaMessage
    data class Reversal(val reversedCode: String) : MpesaMessage
    data object NotRelevant : MpesaMessage
}

/**
 * Reads Safaricom M-Pesa confirmation SMS. Only money *received* matters for a Harambee;
 * sends, payments, withdrawals and airtime are ignored.
 *
 * Example: "SJ12ABC3DE Confirmed.You have received Ksh1,500.00 from JANE WANJIKU 0712345678
 * on 2/10/26 at 3:45 PM  New M-PESA balance is Ksh12,345.00."
 */
object MpesaParser {
    val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")

    private const val CODE = """[A-Z0-9]{10}"""

    private val received = Regex(
        """\b($CODE)\s*(?:Confirmed|Imethibitishwa)\.?\s*""" +
            """(?:You have received|Umepokea)\s*Ksh\s?([\d,]+(?:\.\d{1,2})?)\s+""" +
            """(?:from|kutoka(?:\s+kwa)?)\s+(.+?)\s+""" +
            """(?:on|mnamo|tarehe)\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+(?:at|saa)\s+(\d{1,2}:\d{2}\s*[AaPp]\.?[Mm]\.?)""",
        RegexOption.IGNORE_CASE,
    )

    /** Fallback for variants without a readable date (e.g. some Pochi la Biashara messages). */
    private val receivedNoDate = Regex(
        """\b($CODE)\s*(?:Confirmed|Imethibitishwa)\.?\s*""" +
            """(?:You have received|Umepokea)\s*Ksh\s?([\d,]+(?:\.\d{1,2})?)\s+""" +
            """(?:from|kutoka(?:\s+kwa)?)\s+([^.]+?)(?:\.|\s+New M-PESA|\s+in your|$)""",
        RegexOption.IGNORE_CASE,
    )

    private val reversal = Regex("""(?:transaction|muamala)\s+($CODE)\b[^.]*revers""", RegexOption.IGNORE_CASE)
    private val reversalAlt = Regex("""revers\w*\s+(?:of\s+)?(?:transaction\s+)?($CODE)\b""", RegexOption.IGNORE_CASE)

    /** Trailing phone number or account: "0712345678", "254712***678", "0712 345 678", "300600". */
    private val trailingNumber = Regex("""^(.*?)[\s-]+(\+?[0-9][0-9*\s]{4,}[0-9*])$""")

    private val dateShort = DateTimeFormatter.ofPattern("d/M/uu", Locale.US).withResolverStyle(ResolverStyle.STRICT)
    private val dateLong = DateTimeFormatter.ofPattern("d/M/uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun parse(body: String): MpesaMessage {
        val text = body.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

        received.find(text)?.let { m ->
            val (code, amount, from, date, time) = m.destructured
            return build(code, amount, from, parseDateTime(date, time))
        }
        receivedNoDate.find(text)?.let { m ->
            val (code, amount, from) = m.destructured
            return build(code, amount, from, null)
        }
        if (text.contains("revers", ignoreCase = true)) {
            val code = (reversal.find(text) ?: reversalAlt.find(text))?.groupValues?.get(1)
            if (code != null) return MpesaMessage.Reversal(code.uppercase())
        }
        return MpesaMessage.NotRelevant
    }

    private fun build(code: String, amount: String, from: String, time: Long?): MpesaMessage {
        val cents = Money.parseToCents(amount) ?: return MpesaMessage.NotRelevant
        val (name, phone) = splitSender(from)
        return MpesaMessage.Received(
            MpesaReceipt(
                code = code.uppercase(),
                amountCents = cents,
                senderName = Names.titleCase(name).ifBlank { "Unknown" },
                senderPhone = phone?.let { Phone.normalize(it) },
                transactionTime = time,
            ),
        )
    }

    internal fun splitSender(from: String): Pair<String, String?> {
        val cleaned = from.trim().trimEnd('.', ',')
        val m = trailingNumber.matchEntire(cleaned)
        if (m != null && m.groupValues[1].isNotBlank()) {
            return m.groupValues[1].trim().trimEnd('-').trim() to m.groupValues[2].replace(" ", "")
        }
        return cleaned to null
    }

    internal fun parseDateTime(date: String, time: String): Long? = try {
        val year = date.substringAfterLast('/')
        val d = LocalDate.parse(date, if (year.length == 4) dateLong else dateShort)
        val t = LocalTime.parse(time.replace(".", "").replace(Regex("\\s+"), " ").uppercase().let {
            if (it.contains(' ')) it else it.dropLast(2) + " " + it.takeLast(2)
        }, timeFormat)
        d.atTime(t).atZone(NAIROBI).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }

    /** Splits a block of pasted text that may contain many M-Pesa messages. */
    fun splitMessages(text: String): List<String> {
        val starts = Regex("""\b$CODE\s*(?:Confirmed|Imethibitishwa)""", RegexOption.IGNORE_CASE)
            .findAll(text).map { it.range.first }.toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexed { i, start ->
            text.substring(start, if (i + 1 < starts.size) starts[i + 1] else text.length).trim()
        }
    }
}

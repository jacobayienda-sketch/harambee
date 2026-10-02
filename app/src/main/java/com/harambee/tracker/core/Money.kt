package com.harambee.tracker.core

/** Amounts are stored as whole cents (Long) so totals never drift. */
object Money {
    private val amountPattern = Regex("""^\s*(?:KES|Ksh|KSh|Kshs)?\.?\s*([\d,]+)(?:\.(\d{1,2}))?\s*$""", RegexOption.IGNORE_CASE)

    /** Parses "1,500.00", "1500", "Ksh 2,000" into cents, or null if not a number. */
    fun parseToCents(text: String): Long? {
        val match = amountPattern.matchEntire(text) ?: return null
        val whole = match.groupValues[1].replace(",", "")
        if (whole.isEmpty()) return null
        val fraction = match.groupValues[2].padEnd(2, '0')
        return whole.toLongOrNull()?.let { it * 100 + (fraction.toLongOrNull() ?: 0) }
    }

    /** "1,500" for whole shillings, "1,500.50" otherwise. */
    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val whole = abs / 100
        val fraction = abs % 100
        val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
        val body = if (fraction == 0L) grouped else "$grouped.${fraction.toString().padStart(2, '0')}"
        return if (negative) "-$body" else body
    }

    fun formatKes(cents: Long): String = "KES ${format(cents)}"
}

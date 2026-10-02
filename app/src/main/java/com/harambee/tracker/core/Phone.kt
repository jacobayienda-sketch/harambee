package com.harambee.tracker.core

object Phone {
    /** Normalises Kenyan numbers to 07XXXXXXXX / 01XXXXXXXX. Masked numbers ("0712***678") are kept as-is. */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val compact = raw.replace(Regex("[\\s-]"), "").removePrefix("+")
        return when {
            compact.startsWith("254") && compact.length >= 12 -> "0" + compact.substring(3)
            compact.length == 9 && (compact.startsWith("7") || compact.startsWith("1")) -> "0$compact"
            else -> compact
        }
    }

    fun isMasked(phone: String?): Boolean = phone?.contains('*') == true

    /** "0712345678" -> "0712 345 678" */
    fun pretty(phone: String?): String {
        val p = normalize(phone) ?: return ""
        return if (p.length == 10 && !isMasked(p)) "${p.substring(0, 4)} ${p.substring(4, 7)} ${p.substring(7)}" else p
    }

    /** Stable key for grouping contributions by person. */
    fun contributorKey(name: String, phone: String?): String {
        val p = normalize(phone)
        return if (p != null && !isMasked(p) && p.length >= 10) "tel:$p" else "name:${Names.normalized(name)}|${p ?: ""}"
    }
}

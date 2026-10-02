package com.harambee.tracker.core

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CsvRow(
    val name: String,
    val phone: String?,
    val amountCents: Long,
    val status: String,
    val source: String,
    val code: String?,
    val time: Long,
    val note: String,
)

object CsvExport {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).withZone(MpesaParser.NAIROBI)

    fun build(rows: List<CsvRow>): String = buildString {
        append("No,Name,Phone,Amount (KES),Status,Method,M-Pesa code,Date,Note\n")
        rows.forEachIndexed { i, r ->
            append(listOf(
                (i + 1).toString(), r.name, r.phone.orEmpty(), Money.format(r.amountCents).replace(",", ""),
                r.status, r.source, r.code.orEmpty(), format.format(Instant.ofEpochMilli(r.time)), r.note,
            ).joinToString(",") { escape(it) })
            append("\n")
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

package com.harambee.tracker.core

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PublicPageData(
    val name: String,
    val intro: String,
    val payTo: List<PayTo>,
    val tally: Tally,
    /** Already privacy-filtered names, in list order. */
    val lines: List<UpdateLine>,
    val showAmounts: Boolean,
    val updatedAt: Long,
)

/**
 * A single self-contained web page for a Harambee (no internet needed to open it): progress,
 * how to contribute and the list, with names already shortened or hidden per the privacy setting.
 * Shared as a file on WhatsApp; opens in any phone browser.
 */
object PublicPage {
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US).withZone(MpesaParser.NAIROBI)

    fun html(d: PublicPageData): String {
        val target = d.tally.targetCents?.takeIf { it > 0 }
        val pct = target?.let { (d.tally.totalCents * 100 / it).coerceIn(0, 100) }
        val paid = WhatsAppUpdateBuilder.arrange(d.lines.filter { it.paid }, combine = true, sortByAmount = false)
        val rows = paid.withIndex().joinToString("") { (i, l) ->
            "<li><span class=\"n\">${i + 1}.</span><span class=\"who\">${esc(l.name)}</span>" +
                (if (d.showAmounts) "<span class=\"amt\">${Money.format(l.amountCents)}</span>" else "") + "<span class=\"ok\">✓</span></li>"
        }
        val payTo = d.payTo.filter { it.label().isNotBlank() }.joinToString("") {
            val number = Phone.pretty(it.number).ifBlank { it.number.trim() }
            // Keep the phone number on one line.
            "<div class=\"pay\">M-Pesa: <b>${esc(it.name.trim())} <span class=\"num\">${esc(number)}</span></b></div>"
        }
        return """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(d.name)}</title>
<style>
:root{--bg:#f6f8f7;--card:#fff;--ink:#14201b;--muted:#5b6b64;--accent:#0a7c52;--track:#dfe7e3;--line:#e6ece9}
@media (prefers-color-scheme:dark){:root{--bg:#0f1513;--card:#18211e;--ink:#e6efeb;--muted:#9fb2aa;--accent:#7bd8b1;--track:#2a3632;--line:#25302c}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:640px;margin:0 auto;padding:20px 16px 40px}
.card{background:var(--card);border-radius:16px;padding:20px;margin-bottom:16px;box-shadow:0 1px 2px rgba(0,0,0,.06)}
h1{font-size:22px;margin:0 0 8px}.intro{color:var(--muted);white-space:pre-line;margin:0}
.total{font-size:36px;font-weight:700;margin:4px 0}.label{color:var(--muted);font-size:14px}
.bar{height:10px;background:var(--track);border-radius:5px;overflow:hidden;margin:12px 0 6px}.bar i{display:block;height:100%;background:var(--accent);border-radius:5px}
.stats{display:flex;gap:16px;flex-wrap:wrap;color:var(--muted);font-size:14px}.stats b{color:var(--ink)}
.pay{margin-top:8px}.num{white-space:nowrap}ol{list-style:none;margin:0;padding:0}
li{display:flex;gap:8px;padding:8px 0;border-bottom:1px solid var(--line)}li:last-child{border:0}
.n{color:var(--muted);min-width:2.2em;text-align:right}.who{flex:1}.amt{font-variant-numeric:tabular-nums}.ok{color:var(--accent)}
footer{color:var(--muted);font-size:13px;text-align:center}
</style></head><body><main>
<div class="card"><h1>${esc(d.name)}</h1>${if (d.intro.isNotBlank()) "<p class=\"intro\">${esc(d.intro.replace("*", ""))}</p>" else ""}</div>
<div class="card"><div class="label">Total received</div><div class="total">KES ${Money.format(d.tally.totalCents)}</div>
${if (target != null) "<div class=\"bar\" role=\"progressbar\" aria-valuenow=\"$pct\" aria-valuemin=\"0\" aria-valuemax=\"100\"><i style=\"width:$pct%\"></i></div>" else ""}
<div class="stats"><span><b>${d.tally.contributors}</b> contributors</span>${
            if (target != null) "<span><b>$pct%</b> of KES ${Money.format(target)}</span><span>Balance <b>KES ${Money.format((target - d.tally.totalCents).coerceAtLeast(0))}</b></span>" else ""
        }</div>$payTo</div>
<div class="card"><div class="label">Contributors</div><ol>$rows</ol></div>
<footer>Updated ${date.format(Instant.ofEpochMilli(d.updatedAt))} · Prepared with Harambee Tracker</footer>
</main></body></html>
"""
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** One row per person, for a spreadsheet. */
object ContributorsCsv {
    data class Person(val name: String, val phone: String?, val paidCents: Long, val pledgedCents: Long, val payments: Int, val lastPaid: Long?, val anonymous: Boolean)

    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).withZone(MpesaParser.NAIROBI)

    fun build(people: List<Person>): String = buildString {
        append("No,Name,Phone,Paid (KES),Pledged unpaid (KES),Payments,Last payment,Anonymous\n")
        people.forEachIndexed { i, p ->
            append(listOf(
                (i + 1).toString(), p.name, p.phone.orEmpty(), Money.format(p.paidCents).replace(",", ""),
                Money.format(p.pledgedCents).replace(",", ""), p.payments.toString(),
                p.lastPaid?.let { format.format(Instant.ofEpochMilli(it)) }.orEmpty(), if (p.anonymous) "yes" else "",
            ).joinToString(",") { v -> if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v })
            append("\n")
        }
    }
}

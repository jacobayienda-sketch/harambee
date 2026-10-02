package com.harambee.tracker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.core.ContributorsCsv
import com.harambee.tracker.core.CsvExport
import com.harambee.tracker.core.CsvRow
import com.harambee.tracker.core.Money
import com.harambee.tracker.core.MpesaParser
import com.harambee.tracker.core.Phone
import com.harambee.tracker.core.PublicPage
import com.harambee.tracker.core.PublicPageData
import com.harambee.tracker.core.Tally
import com.harambee.tracker.core.Templates
import com.harambee.tracker.data.Campaign
import com.harambee.tracker.data.Collector
import com.harambee.tracker.data.ContributionRow
import com.harambee.tracker.data.Source
import com.harambee.tracker.data.Status
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Chart ink: one hue, checked for contrast on light and dark surfaces. */
@Composable
private fun chartColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF7BD8B1) else Color(0xFF0A7C52)

// ---------- Dashboard ----------

@Composable
fun DashboardScreen(campaignId: Long, onBack: () -> Unit, onPeople: () -> Unit, onActivity: () -> Unit, onReview: () -> Unit) {
    val repository = appContainer().repository
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())
    val activity by repository.activity(campaignId).collectAsStateWithLifecycle(emptyList())
    val pendingAll by repository.pending.collectAsStateWithLifecycle(emptyList())
    val c = campaign ?: return
    val counted = rows.filter { it.contribution.status == Status.COUNTED }
    val tally = CampaignData.tally(rows, c)
    val people = CampaignData.people(rows)
    val pledged = rows.filter { it.contribution.status == Status.PLEDGED }
    val pending = pendingAll.count { it.contribution.campaignId == campaignId }
    val target = c.targetCents?.takeIf { it > 0 }

    Scaffold(topBar = { BackTopBar("Dashboard", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Total received", Money.formatKes(tally.totalCents), Modifier.weight(1f), emphasis = true)
                Tile(
                    if (target != null) "Balance" else "Contributors",
                    if (target != null) Money.formatKes((target - tally.totalCents).coerceAtLeast(0)) else tally.contributors.toString(),
                    Modifier.weight(1f),
                )
            }
            if (target != null) {
                Column {
                    Text("${tally.totalCents * 100 / target}% of ${Money.formatKes(target)}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    ProgressBar((tally.totalCents.toFloat() / target).coerceIn(0f, 1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Contributors", tally.contributors.toString(), Modifier.weight(1f))
                Tile("Average", if (tally.contributors > 0) Money.formatKes(tally.totalCents / tally.contributors) else "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Pledges unpaid", "${Money.formatKes(pledged.sumOf { it.contribution.amountCents })} (${pledged.size})", Modifier.weight(1f))
                Tile("To review", pending.toString(), Modifier.weight(1f), onClick = if (pending > 0) onReview else null)
            }

            ChartCard("Received per day") { DailyChart(counted) }

            val byMethod = counted.groupBy { Source.label(it.contribution.source).substringBefore(" (") }
                .map { (k, v) -> k to v.sumOf { it.contribution.amountCents } }.sortedByDescending { it.second }
            if (byMethod.isNotEmpty()) ChartCard("By payment method") { BarList(byMethod) }

            if (collectors.isNotEmpty()) {
                val byCollector = counted.groupBy { CampaignData.collectorLabel(c, collectors, it.contribution.collectorId) }
                    .map { (k, v) -> k to v.sumOf { it.contribution.amountCents } }.sortedByDescending { it.second }
                ChartCard("By collector") { BarList(byCollector) }
            }

            val top = people.sortedByDescending { it.paidCents }.filter { it.paidCents > 0 }.take(5)
            if (top.isNotEmpty()) {
                ChartCard("Top contributors", action = "All contributors" to onPeople) {
                    BarList(top.map { (if (it.anonymous) "${it.name} (anonymous)" else it.name) to it.paidCents })
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent activity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onActivity) { Text("See all") }
                    }
                    if (activity.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodySmall)
                    activity.take(5).forEach { a ->
                        Text("${Formats.dateTime(a.at)} · ${a.action}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(a.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, emphasis: Boolean = false, onClick: (() -> Unit)? = null) {
    Card(
        modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = if (emphasis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val color = chartColor()
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        drawRect(track)
        drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(5.dp.toPx()))
    }
}

@Composable
private fun ChartCard(title: String, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Horizontal bars, one hue; values in text ink beside each bar. */
@Composable
private fun BarList(items: List<Pair<String, Long>>) {
    val max = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1
    val color = chartColor()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            Column(Modifier.semantics { contentDescription = "$label ${Money.formatKes(value)}" }) {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Money.formatKes(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    drawRoundRect(color, size = Size(size.width * value / max, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                }
            }
        }
    }
}

/** Money received per day for the last 14 days of collecting; tap a bar for its value, or view as a table. */
@Composable
private fun DailyChart(counted: List<ContributionRow>) {
    val days = remember(counted) {
        val dated = counted.filter { it.contribution.source != Source.WHATSAPP_LIST }
        if (dated.isEmpty()) return@remember emptyList<Pair<LocalDate, Long>>()
        val byDay = dated.groupBy { Instant.ofEpochMilli(it.contribution.receivedAt).atZone(MpesaParser.NAIROBI).toLocalDate() }
            .mapValues { (_, v) -> v.sumOf { it.contribution.amountCents } }
        val last = maxOf(byDay.keys.max(), LocalDate.now(MpesaParser.NAIROBI))
        val first = maxOf(byDay.keys.min(), last.minusDays(13))
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map { it to (byDay[it] ?: 0L) }.toList()
    }
    if (days.isEmpty()) {
        Text("No dated M-Pesa or cash payments yet.", style = MaterialTheme.typography.bodySmall)
        return
    }
    var selected by remember(days) { mutableStateOf(days.indexOfLast { it.second > 0 }) }
    var asTable by remember { mutableStateOf(false) }
    val label = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    val color = chartColor()
    val muted = MaterialTheme.colorScheme.outlineVariant

    if (asTable) {
        days.reversed().forEach { (d, v) -> LabelValue(label.format(d), Money.formatKes(v)) }
    } else {
        selected.takeIf { it >= 0 }?.let { i ->
            Text("${label.format(days[i].first)}: ${Money.formatKes(days[i].second)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        val max = days.maxOf { it.second }.coerceAtLeast(1)
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .semantics { contentDescription = "Bar chart of money received per day. Use 'Show as table' for the values." }
                .pointerInput(days) {
                    detectTapGestures { offset -> selected = (offset.x / (size.width.toFloat() / days.size)).toInt().coerceIn(0, days.lastIndex) }
                },
        ) {
            val slot = size.width / days.size
            val gap = 2.dp.toPx()
            val radius = 4.dp.toPx()
            days.forEachIndexed { i, (_, v) ->
                val h = size.height * v / max
                if (h > 0f) {
                    val alpha = if (selected < 0 || selected == i) 1f else 0.55f
                    // Rounded top, square base on the baseline.
                    drawRoundRect(color.copy(alpha = alpha), topLeft = Offset(i * slot + gap / 2, size.height - h), size = Size(slot - gap, h), cornerRadius = CornerRadius(radius))
                    drawRect(color.copy(alpha = alpha), topLeft = Offset(i * slot + gap / 2, size.height - minOf(h, radius)), size = Size(slot - gap, minOf(h, radius)))
                }
            }
            drawLine(muted, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row {
            Text(label.format(days.first().first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(label.format(days.last().first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TextButton(onClick = { asTable = !asTable }) { Text(if (asTable) "Show as chart" else "Show as table") }
}

// ---------- Contributors ----------

private enum class PeopleSort(val label: String) { AMOUNT("Amount"), NAME("Name"), RECENT("Recent") }

@Composable
fun PeopleScreen(campaignId: Long, onBack: () -> Unit, onOpenContribution: (Long) -> Unit) {
    val container = appContainer()
    val repository = container.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())
    val thankYou by container.settings.thankYouTemplate.value.collectAsStateWithLifecycle()
    val reminder by container.settings.pledgeReminderTemplate.value.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(PeopleSort.AMOUNT) }
    var openKey by remember { mutableStateOf<String?>(null) }
    val c = campaign ?: return
    val people = CampaignData.people(rows)
    val shown = people
        .filter { query.isBlank() || it.name.contains(query, true) || it.phone?.contains(query) == true }
        .let { list ->
            when (sort) {
                PeopleSort.AMOUNT -> list.sortedByDescending { it.paidCents + it.pledgedCents }
                PeopleSort.NAME -> list.sortedBy { it.name.lowercase() }
                PeopleSort.RECENT -> list.sortedByDescending { it.lastPaid ?: 0 }
            }
        }

    Scaffold(topBar = {
        BackTopBar("Contributors", onBack) {
            TextButton(onClick = { exportContributors(context, c, people) }) { Text("Export") }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${people.size} people · ${Money.formatKes(people.sumOf { it.paidCents })} paid", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(query, { query = it }, placeholder = { Text("Search name or phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeopleSort.entries.forEach { s -> FilterChip(sort == s, { sort = s }, label = { Text(s.label) }) }
                    }
                    Text("Tap a person to rename, hide their name, merge duplicates, or send a thank-you / reminder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(shown, key = { it.key }) { p ->
                Row(Modifier.fillMaxWidth().clickable { openKey = p.key }.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name + if (p.anonymous) "  · anonymous" else "", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(
                                Phone.pretty(p.phone).ifBlank { null },
                                "${p.payments} payment${if (p.payments == 1) "" else "s"}",
                                p.pledgedCents.takeIf { it > 0 }?.let { "pledged ${Money.format(it)}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Money.format(p.paidCents), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    val person = people.firstOrNull { it.key == openKey }
    if (person != null) {
        var name by remember(person.key) { mutableStateOf(person.name) }
        var mergeOpen by remember(person.key) { mutableStateOf(false) }
        val payTo = CampaignData.payToLabel(c, collectors)
        AlertDialog(
            onDismissRequest = { openKey = null },
            title = { Text(person.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    person.rows.sortedBy { it.contribution.receivedAt }.forEach { r ->
                        Row(Modifier.fillMaxWidth().clickable { openKey = null; onOpenContribution(r.contribution.id) }) {
                            Text(
                                (if (r.contribution.source == Source.WHATSAPP_LIST) "From list" else Formats.dateTime(r.contribution.receivedAt)) +
                                    if (r.contribution.status == Status.PLEDGED) " · pledge" else "",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                            )
                            Text(Money.format(r.contribution.amountCents) + if (r.contribution.status == Status.COUNTED) " ✅" else "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                    OutlinedTextField(name, { name = it }, label = { Text("Name on the list") }, singleLine = true)
                    TextButton(enabled = name.isNotBlank() && name != person.name, onClick = {
                        scope.launch { repository.renamePerson(campaignId, person.key, name) }
                        openKey = null
                    }) { Text("Rename on all their entries") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Anonymous")
                            Text("Shown as \"Well-wisher\" in anything shared", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(person.anonymous, { on -> scope.launch { repository.setAnonymous(campaignId, person.key, on, person.name) } })
                    }
                    Box {
                        OutlinedButton(onClick = { mergeOpen = true }) { Text("Same person as…") }
                        DropdownMenu(mergeOpen, { mergeOpen = false }) {
                            people.filter { it.key != person.key }.sortedBy { it.name.lowercase() }.forEach { other ->
                                DropdownMenuItem(text = { Text("${other.name} · ${Money.format(other.paidCents)}") }, onClick = {
                                    mergeOpen = false
                                    openKey = null
                                    scope.launch { repository.mergePeople(campaignId, person.key, other.key, person.name, other.name) }
                                })
                            }
                        }
                    }
                    if (person.paidCents > 0) {
                        OutlinedButton(onClick = {
                            Sharing.messagePerson(context, person.phone, Templates.fill(thankYou, person.name, person.paidCents, c.name, ""))
                        }) { Text("Send thank-you") }
                    }
                    if (person.pledgedCents > 0) {
                        OutlinedButton(onClick = {
                            Sharing.messagePerson(context, person.phone, Templates.fill(reminder, person.name, person.pledgedCents, c.name, payTo))
                        }) { Text("Remind about pledge") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openKey = null }) { Text("Close") } },
        )
    }
}

// ---------- Public page & progress card ----------

@Composable
fun PublicPageScreen(campaignId: Long, onBack: () -> Unit) {
    val repository = appContainer().repository
    val context = LocalContext.current
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())
    val c = campaign ?: return
    val tally = CampaignData.tally(rows, c)
    val payTo = CampaignData.payTo(c, collectors)
    val card = remember(c, tally, payTo) { ProgressCard.draw(c, tally, payTo.map { it.label() }) }

    Scaffold(topBar = { BackTopBar("Public page", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Share progress with people outside the committee. Names follow this Harambee's privacy setting " +
                    "(${com.harambee.tracker.core.NameDisplay.all.first { it.first == c.nameDisplay }.second}${if (!c.showAmounts) ", amounts hidden" else ""}); phone numbers are never included.",
                style = MaterialTheme.typography.bodySmall,
            )
            Image(card.asImageBitmap(), contentDescription = "Progress card for ${c.name}", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
            Button(onClick = {
                val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName(c.name, "progress", "png"))
                file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Sharing.shareFile(context, file, "image/png", "Share progress card")
            }, modifier = Modifier.fillMaxWidth()) { Text("Share progress card (WhatsApp Status, groups)") }
            OutlinedButton(onClick = { sharePublicPage(context, c, rows, collectors) }, modifier = Modifier.fillMaxWidth()) {
                Text("Share campaign page (opens in any browser)")
            }
            Text(
                "The campaign page is a single file with the progress bar, how to contribute and the contributor list. " +
                    "To give it a web link, upload the file to Google Drive or any website host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun sharePublicPage(context: Context, c: Campaign, rows: List<ContributionRow>, collectors: List<Collector>) {
    val html = PublicPage.html(
        PublicPageData(
            name = c.name, intro = c.intro, payTo = CampaignData.payTo(c, collectors), tally = CampaignData.tally(rows, c),
            lines = CampaignData.lines(rows, c), showAmounts = c.showAmounts, updatedAt = System.currentTimeMillis(),
        ),
    )
    val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName(c.name, "page", "html"))
    file.writeText(html)
    Sharing.shareFile(context, file, "text/html", "Share campaign page")
}

/** A 1080×1350 image of the campaign's progress, sized for WhatsApp Status and chats. */
object ProgressCard {
    fun draw(c: Campaign, tally: Tally, payTo: List<String>): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0B6E4F.toInt(), 0xFF064330.toInt(), Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val white = 0xFFFFFFFF.toInt()
        val soft = 0xCCFFFFFF.toInt()
        fun paint(size: Float, color: Int = white, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val margin = 96f
        var y = 140f
        canvas.drawText("HARAMBEE", margin, y, paint(36f, soft, bold = true).apply { letterSpacing = 0.2f })
        y += 40f
        val title = StaticLayout.Builder.obtain(c.name, 0, c.name.length, paint(68f, bold = true), (w - 2 * margin).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        canvas.save()
        canvas.translate(margin, y)
        title.draw(canvas)
        canvas.restore()
        y += title.height + 120f
        canvas.drawText("Total received", margin, y, paint(40f, soft))
        y += 120f
        canvas.drawText("KES ${Money.format(tally.totalCents)}", margin, y, paint(118f, bold = true))
        val target = c.targetCents?.takeIf { it > 0 }
        if (target != null) {
            y += 70f
            val frac = (tally.totalCents.toFloat() / target).coerceIn(0f, 1f)
            val track = RectF(margin, y, w - margin, y + 36f)
            canvas.drawRoundRect(track, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF })
            if (frac > 0f) canvas.drawRoundRect(RectF(margin, y, margin + (w - 2 * margin) * frac, y + 36f), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7BD8B1.toInt() })
            y += 100f
            canvas.drawText("${tally.totalCents * 100 / target}% of KES ${Money.format(target)}", margin, y, paint(44f, bold = true))
            y += 60f
            val balance = target - tally.totalCents
            canvas.drawText(if (balance > 0) "Balance KES ${Money.format(balance)}" else "Target reached — thank you!", margin, y, paint(40f, soft))
        }
        y += 80f
        canvas.drawText("${tally.contributors} contributors so far", margin, y, paint(40f, soft))
        var py = h - 120f - (payTo.size - 1) * 56f
        if (payTo.isNotEmpty()) {
            canvas.drawText("Send your contribution to M-Pesa", margin, py - 64f, paint(36f, soft))
            payTo.forEach { label ->
                canvas.drawText(label, margin, py, paint(46f, bold = true))
                py += 56f
            }
        }
        canvas.drawText(
            "Updated ${DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).withZone(MpesaParser.NAIROBI).format(Instant.now())}",
            w - margin, 80f, paint(30f, soft).apply { textAlign = Paint.Align.RIGHT },
        )
        return bmp
    }
}

// ---------- Exports ----------

fun exportTransactions(context: Context, c: Campaign, rows: List<ContributionRow>, collectors: List<Collector>) {
    val csv = CsvExport.build(rows.map { r ->
        val k = r.contribution
        CsvRow(
            r.displayName, k.senderPhone, k.amountCents, k.status, Source.label(k.source), k.mpesaCode, k.receivedAt,
            listOfNotNull(
                k.note.ifBlank { null },
                CampaignData.collectorLabel(c, collectors, k.collectorId).takeIf { collectors.isNotEmpty() }?.let { "Received by $it" },
                "Anonymous".takeIf { k.anonymous },
            ).joinToString("; "),
        )
    })
    val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName(c.name, "transactions", "csv"))
    file.writeText(csv)
    Sharing.shareFile(context, file, "text/csv", "Export transactions")
}

fun exportContributors(context: Context, c: Campaign, people: List<CampaignData.Person>) {
    val csv = ContributorsCsv.build(
        people.sortedByDescending { it.paidCents }.map { ContributorsCsv.Person(it.name, it.phone, it.paidCents, it.pledgedCents, it.payments, it.lastPaid, it.anonymous) },
    )
    val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName(c.name, "contributors", "csv"))
    file.writeText(csv)
    Sharing.shareFile(context, file, "text/csv", "Export contributors")
}

/** The export menu on a Harambee: spreadsheets, report, public page. */
@Composable
fun ExportDialog(c: Campaign, rows: List<ContributionRow>, collectors: List<Collector>, onReport: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ExportOption("Transactions (spreadsheet)", "Every payment with code, date, method, status — full names, for the committee") {
                    onDismiss(); exportTransactions(context, c, rows, collectors)
                }
                ExportOption("Contributors (spreadsheet)", "One row per person: paid, pledged, payments") {
                    onDismiss(); exportContributors(context, c, CampaignData.people(rows))
                }
                ExportOption("Report (PDF / WhatsApp)", "Totals, breakdowns and the full list") { onDismiss(); onReport() }
                ExportOption("Campaign page (web page)", "For the public; follows your privacy settings") {
                    onDismiss(); sharePublicPage(context, c, rows, collectors)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ExportOption(title: String, body: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.harambee.tracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.core.Milestones
import com.harambee.tracker.core.Money
import com.harambee.tracker.core.UpdateFormats
import com.harambee.tracker.core.WhatsAppUpdateBuilder
import com.harambee.tracker.data.Status
import kotlinx.coroutines.launch

/** The update formats a treasurer can send to the group. */
enum class UpdateFormat(val label: String) {
    FULL("Full list"),
    BATCH("New since last update"),
    SINGLE("One contribution"),
    MILESTONE("Milestone"),
    REPORT("Final report"),
}

@Composable
fun UpdateScreen(campaignId: Long, initialFormat: String?, contributionId: Long?, onBack: () -> Unit, onReport: () -> Unit) {
    val container = appContainer()
    val repository = container.repository
    val settings = container.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())
    // Remembers the treasurer's choices for next time.
    var options by remember { mutableStateOf(settings.updateOptions()) }
    LaunchedEffect(options) { settings.saveUpdateOptions(options) }
    var format by remember {
        mutableStateOf(
            (initialFormat ?: settings.updateFormat.value.value).let { f -> UpdateFormat.entries.firstOrNull { it.name == f } ?: UpdateFormat.FULL }
                .takeIf { it != UpdateFormat.REPORT } ?: UpdateFormat.FULL,
        )
    }
    LaunchedEffect(format) { settings.updateFormat.set(format.name) }
    var singleId by remember { mutableStateOf(contributionId) }
    var pickerOpen by remember { mutableStateOf(false) }

    val c = campaign
    if (c == null) {
        Scaffold(topBar = { BackTopBar("WhatsApp update", onBack) }) { Spacer(Modifier.padding(it)) }
        return
    }
    val opts = options.copy(showAmounts = c.showAmounts)
    val lines = CampaignData.lines(rows, c)
    val tally = CampaignData.tally(rows, c)
    val payTo = CampaignData.payTo(c, collectors)
    val counted = rows.filter { it.contribution.status == Status.COUNTED }.sortedByDescending { it.contribution.countedAt ?: it.contribution.createdAt }
    val single = counted.firstOrNull { it.contribution.id == singleId } ?: counted.firstOrNull()

    val text = when (format) {
        UpdateFormat.FULL, UpdateFormat.REPORT -> WhatsAppUpdateBuilder.build(CampaignData.content(c, collectors), lines, opts, System.currentTimeMillis())
        UpdateFormat.BATCH -> UpdateFormats.batch(c.name, lines, opts, tally, payTo)
        UpdateFormat.SINGLE -> single?.let { UpdateFormats.single(c.name, CampaignData.publicName(it, c), it.contribution.amountCents, c.showAmounts, tally, payTo) }
            ?: "No contributions yet."
        UpdateFormat.MILESTONE -> UpdateFormats.milestone(c.name, Milestones.reached(tally.totalCents, c.targetCents), tally, payTo)
    }

    fun shared() {
        // Lists of people move the "new since last update" marker; single and milestone posts don't.
        if (format == UpdateFormat.FULL || format == UpdateFormat.BATCH) scope.launch { repository.markShared(campaignId) }
    }

    Scaffold(topBar = { BackTopBar("WhatsApp update", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateFormat.entries.forEach { f ->
                    FilterChip(format == f, { if (f == UpdateFormat.REPORT) onReport() else format = f }, label = { Text(f.label) })
                }
            }
            when (format) {
                UpdateFormat.BATCH -> Text(
                    c.lastSharedAt?.let { "People counted since the last list you shared (${Formats.dateTime(it)}), with their numbers from the full list." }
                        ?: "Nothing shared yet, so everyone is new. After you share, the next batch starts from then.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UpdateFormat.SINGLE -> Box {
                    OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(single?.let { "${it.displayName} · ${Money.format(it.contribution.amountCents)}" } ?: "No contributions yet")
                    }
                    DropdownMenu(pickerOpen, { pickerOpen = false }) {
                        counted.take(30).forEach { r ->
                            DropdownMenuItem(
                                text = { Text("${r.displayName} · ${Money.format(r.contribution.amountCents)} · ${Formats.dateTime(r.contribution.receivedAt)}") },
                                onClick = { singleId = r.contribution.id; pickerOpen = false },
                            )
                        }
                    }
                }
                UpdateFormat.MILESTONE -> if (c.targetCents == null) {
                    Text("Set a target in Edit Harambee to celebrate 25%, 50%, 75% and 100%.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Sharing.toWhatsApp(context, text); shared() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share to WhatsApp")
                }
                OutlinedButton(onClick = { Sharing.copy(context, text); shared() }) { Text("Copy") }
            }
            if (format == UpdateFormat.FULL && options.listLimit != null) {
                OutlinedButton(
                    onClick = {
                        val full = WhatsAppUpdateBuilder.build(CampaignData.content(c, collectors), lines, opts.copy(listLimit = null, addNextNumber = false), System.currentTimeMillis())
                        Sharing.sharePdf(context, ReportPdf.create(context, fileName(c.name, "list"), c.name, full))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share full list as PDF") }
            }
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            if (format == UpdateFormat.FULL || format == UpdateFormat.BATCH) {
                if (format == UpdateFormat.FULL) {
                    Text("List length", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null to "Whole list", 20 to "Latest 20", 0 to "Totals only").forEach { (limit, label) ->
                            FilterChip(options.listLimit == limit, { options = options.copy(listLimit = limit) }, label = { Text(label) })
                        }
                    }
                }
                Text("Options", style = MaterialTheme.typography.titleSmall)
                Option("One line per person (combine repeat payments)", options.combineRepeat) { options = options.copy(combineRepeat = it) }
                if (format == UpdateFormat.FULL) {
                    Option("Show total and balance", options.showTotal) { options = options.copy(showTotal = it) }
                    Option("Show target", options.showTarget) { options = options.copy(showTarget = it) }
                    Option("Include pledges (no ✅)", options.showPledges) { options = options.copy(showPledges = it) }
                    Option("Add a blank next number for people to fill", options.addNextNumber) { options = options.copy(addNextNumber = it) }
                    Option("Show \"Updated\" date and time", options.showDate) { options = options.copy(showDate = it) }
                    Option("Biggest amounts first", options.sortByAmount) { options = options.copy(sortByAmount = it) }
                }
            }
            Text(
                "Names: ${com.harambee.tracker.core.NameDisplay.all.first { it.first == c.nameDisplay }.second}" +
                    (if (!c.showAmounts) " · amounts hidden" else "") + ". Change privacy, the appeal message or send-to number in Edit Harambee.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label)
    }
}

fun fileName(campaign: String, suffix: String, ext: String = "pdf") = campaign.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_') + "_" + suffix + "." + ext

package com.harambee.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.core.CsvExport
import com.harambee.tracker.core.CsvRow
import com.harambee.tracker.core.Money
import com.harambee.tracker.core.Phone
import com.harambee.tracker.data.ContributionRow
import com.harambee.tracker.data.Source
import com.harambee.tracker.data.Status
import kotlinx.coroutines.launch

private enum class Filter(val label: String, val statuses: Set<String>) {
    LIST("List", setOf(Status.COUNTED, Status.PLEDGED)),
    PAID("Paid ✅", setOf(Status.COUNTED)),
    PLEDGES("Pledges", setOf(Status.PLEDGED)),
    REMOVED("Excluded / reversed", setOf(Status.EXCLUDED, Status.REVERSED)),
}

@Composable
fun CampaignScreen(
    campaignId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShareUpdate: () -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onOpenContribution: (Long) -> Unit,
) {
    val repository = appContainer().repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    var filter by remember { mutableStateOf(Filter.LIST) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val c = campaign ?: run {
        Scaffold(topBar = { BackTopBar("", onBack) }) { Spacer(Modifier.padding(it)) }
        return
    }
    val paid = rows.filter { it.contribution.status == Status.COUNTED }
    val total = paid.sumOf { it.contribution.amountCents }
    val pledged = rows.filter { it.contribution.status == Status.PLEDGED }.sumOf { it.contribution.amountCents }
    val people = paid.map { it.contribution.contributorKey }.distinct().size
    val numbers = rows.filter { it.contribution.status in Filter.LIST.statuses }
        .withIndex().associate { (i, r) -> r.contribution.id to i + 1 }
    val visible = rows.filter { it.contribution.status in filter.statuses }
        .filter { query.isBlank() || it.displayName.contains(query, true) || it.contribution.senderPhone?.contains(query) == true || it.contribution.mpesaCode?.contains(query, true) == true }

    Scaffold(
        topBar = {
            BackTopBar(c.name, onBack) {
                IconButton(onClick = onShareUpdate) { Icon(Icons.Default.Share, "Share update") }
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit Harambee") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Import messages or a list") }, onClick = { menuOpen = false; onImport() })
                    DropdownMenuItem(text = { Text("Export to spreadsheet (CSV)") }, onClick = {
                        menuOpen = false
                        val csv = CsvExport.build(rows.map { r ->
                            CsvRow(r.displayName, r.contribution.senderPhone, r.contribution.amountCents, r.contribution.status,
                                Source.label(r.contribution.source), r.contribution.mpesaCode, r.contribution.receivedAt, r.contribution.note)
                        })
                        Sharing.shareCsv(context, c.name.replace(Regex("[^A-Za-z0-9]+"), "_") + ".csv", csv)
                    })
                    DropdownMenuItem(text = { Text("Delete Harambee") }, onClick = { menuOpen = false; confirmDelete = true })
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Total received", style = MaterialTheme.typography.labelLarge)
                        Text(Money.formatKes(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(
                            "$people contributors · ${paid.size} payments" + if (pledged > 0) " · ${Money.formatKes(pledged)} pledged" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val target = c.targetCents
                        if (target != null && target > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { (total.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            val balance = target - total
                            Text(
                                "${total * 100 / target}% of ${Money.formatKes(target)}" + if (balance > 0) " · Balance ${Money.formatKes(balance)}" else " · Target reached 🎉",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (c.payToNumber.isNotBlank()) {
                            Text("Send to: ${c.payToName} ${Phone.pretty(c.payToNumber)}".trim(), style = MaterialTheme.typography.bodySmall)
                        }
                        if (!c.isActive) Text("Closed — not collecting new payments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onShareUpdate, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("WhatsApp update")
                    }
                    OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add / pledge")
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Filter.entries) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                }
            }
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Search name, phone or M-Pesa code") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        if (rows.isEmpty()) "No contributions yet. They appear here as M-Pesa payments arrive, or tap Add / pledge." else "Nothing here.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(visible, key = { it.contribution.id }) { row ->
                ContributionItem(row, numbers[row.contribution.id], onClick = { onOpenContribution(row.contribution.id) })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${c.name}?") },
            text = { Text("Typed entries and pledges are deleted. M-Pesa payments are kept aside so the same message can never be counted twice.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repository.deleteCampaign(c)
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ContributionItem(row: ContributionRow, number: Int?, onClick: () -> Unit) {
    val k = row.contribution
    val struck = k.status == Status.EXCLUDED || k.status == Status.REVERSED
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                (number?.let { "$it. " } ?: "") + row.displayName,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            )
            val sub = buildList {
                add(Source.label(k.source))
                k.mpesaCode?.let { add(it) }
                if (k.source != Source.WHATSAPP_LIST) add(Formats.dateTime(k.receivedAt))
                if (k.status == Status.PLEDGED) add("pledge")
                if (k.status == Status.REVERSED) add("reversed")
                if (k.status == Status.EXCLUDED) add("not counted")
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            Money.format(k.amountCents) + if (k.status == Status.COUNTED) " ✅" else "",
            style = MaterialTheme.typography.titleMedium,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
        )
    }
}

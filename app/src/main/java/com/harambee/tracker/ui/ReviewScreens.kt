package com.harambee.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.core.Money
import com.harambee.tracker.core.Phone
import com.harambee.tracker.data.Campaign
import com.harambee.tracker.data.ReviewInfo
import com.harambee.tracker.data.Status
import kotlinx.coroutines.launch

@Composable
fun CampaignPicker(campaigns: List<Campaign>, selectedId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(campaigns.firstOrNull { it.id == selectedId }?.name ?: "Choose Harambee…", maxLines = 1)
        }
        DropdownMenu(open, { open = false }) {
            campaigns.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name + if (c.isActive) "" else " (closed)") },
                    onClick = { open = false; onSelect(c.id) },
                )
            }
        }
    }
}

/** All payments waiting for a decision. */
@Composable
fun ReviewListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onShareUpdate: (Long) -> Unit) {
    val container = appContainer()
    val repository = container.repository
    val scope = rememberCoroutineScope()
    val pending by repository.pending.collectAsStateWithLifecycle(emptyList())
    val campaigns by repository.campaigns.collectAsStateWithLifecycle(emptyList())
    var bulkCampaign by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(campaigns) { if (bulkCampaign == null) bulkCampaign = campaigns.firstOrNull { it.isActive }?.id }

    Scaffold(topBar = { BackTopBar("Review payments", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (pending.isEmpty()) {
                item { Text("All caught up. New M-Pesa payments will appear here.", Modifier.padding(16.dp)) }
            } else if (campaigns.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Add all ${pending.size} (${Money.formatKes(pending.sumOf { it.contribution.amountCents })}) to:", style = MaterialTheme.typography.titleSmall)
                            CampaignPicker(campaigns, bulkCampaign, { bulkCampaign = it })
                            Button(
                                enabled = bulkCampaign != null,
                                onClick = {
                                    val target = bulkCampaign ?: return@Button
                                    scope.launch {
                                        pending.forEach {
                                            repository.confirm(it.contribution.id, target)
                                            container.notifier.cancel(it.contribution.id)
                                        }
                                        onShareUpdate(target)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Add all ✅") }
                            Text("Only do this if every payment below is a contribution. Otherwise tap each one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(pending, key = { it.contribution.id }) { row ->
                val k = row.contribution
                Row(Modifier.fillMaxWidth().clickable { onOpen(k.id) }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(Phone.pretty(k.senderPhone).ifBlank { null }, k.mpesaCode, Formats.dateTime(k.receivedAt)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Money.format(k.amountCents), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/** One payment: is it a contribution, and to which Harambee? */
@Composable
fun ReviewScreen(contributionId: Long, onBack: () -> Unit, onAdded: (Long) -> Unit, onNewCampaign: () -> Unit) {
    val container = appContainer()
    val repository = container.repository
    val scope = rememberCoroutineScope()
    val row by repository.contribution(contributionId).collectAsStateWithLifecycle(null)
    val campaigns by repository.campaigns.collectAsStateWithLifecycle(emptyList())
    var selected by remember { mutableStateOf<Long?>(null) }
    var info by remember { mutableStateOf<ReviewInfo?>(null) }
    var listName by remember { mutableStateOf<String?>(null) }

    val r = row
    LaunchedEffect(r?.contribution?.id, campaigns) {
        if (r != null && selected == null) selected = r.contribution.campaignId ?: campaigns.firstOrNull { it.isActive }?.id
    }
    LaunchedEffect(r?.contribution?.id, selected) {
        if (r != null) {
            val i = repository.reviewInfo(r.contribution, selected)
            info = i
            listName = i.listMatch?.let { it.listName ?: it.senderName } ?: r.displayName
        }
    }

    Scaffold(topBar = { BackTopBar("New payment", onBack) }) { padding ->
        if (r == null) {
            Text("This payment no longer exists.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val k = r.contribution
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(Money.formatKes(k.amountCents), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("from ${k.senderName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (k.senderPhone != null) LabelValue("Phone", Phone.pretty(k.senderPhone))
                    k.mpesaCode?.let { LabelValue("M-Pesa code", it) }
                    LabelValue("Received", Formats.dateTime(k.receivedAt))
                }
            }

            if (k.status != Status.PENDING) {
                Text(
                    when (k.status) {
                        Status.COUNTED -> "Already added ✅"
                        Status.EXCLUDED -> "Marked as not a contribution."
                        Status.REVERSED -> "Reversed by Safaricom."
                        else -> "Already handled."
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                k.campaignId?.takeIf { k.status == Status.COUNTED }?.let { id ->
                    Button(onClick = { onAdded(id) }, modifier = Modifier.fillMaxWidth()) { Text("Share WhatsApp update") }
                }
                return@Column
            }

            if (campaigns.isEmpty()) {
                Text("Create a Harambee first, then add this payment to it.")
                Button(onClick = onNewCampaign, modifier = Modifier.fillMaxWidth()) { Text("New Harambee") }
                return@Column
            }

            Text("Add to", style = MaterialTheme.typography.titleSmall)
            CampaignPicker(campaigns, selected, { selected = it })

            info?.let { i ->
                val (title, body) = when {
                    i.listMatch != null -> "On the list ✅" to "Matches \"${i.listMatch.listName ?: i.listMatch.senderName}\" (${Money.format(i.listMatch.amountCents)}${if (i.listMatch.status == Status.PLEDGED) ", pledged" else ""}). Adding will tick that line instead of adding a new one."
                    i.earlier.isNotEmpty() -> "Has contributed before" to i.earlier.joinToString("\n") { "• ${Money.formatKes(it.amountCents)} on ${Formats.dateTime(it.receivedAt)}" } + "\nThis will be added as another contribution."
                    i.suggestedCampaign != null -> "New contributor" to "Not yet on this list."
                    else -> null to null
                }
                if (title != null) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(body ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            OutlinedTextField(
                listName ?: "", { listName = it },
                label = { Text("Name on the list") },
                supportingText = { Text("e.g. \"CO Peter chesos\" — how it should appear in the WhatsApp list") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = selected != null,
                onClick = {
                    val target = selected ?: return@Button
                    scope.launch {
                        val name = listName?.trim()?.takeIf { it.isNotEmpty() && it != k.senderName }
                        repository.confirm(k.id, target, name)
                        container.notifier.cancel(k.id)
                        onAdded(target)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add ✅ and share update") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        repository.reject(k.id)
                        container.notifier.cancel(k.id)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Not a contribution") }

            k.rawMessage?.let {
                Text("Original message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

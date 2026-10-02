package com.harambee.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.core.Money
import com.harambee.tracker.core.Phone
import com.harambee.tracker.core.Templates
import com.harambee.tracker.data.Contribution
import com.harambee.tracker.data.HarambeeRepository
import com.harambee.tracker.data.Source
import com.harambee.tracker.data.Status
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Add a cash/bank payment, an M-Pesa payment made to someone else's phone, or a pledge. */
@Composable
fun AddContributionScreen(campaignId: Long, onBack: () -> Unit) {
    val repository = appContainer().repository
    val scope = rememberCoroutineScope()
    var duplicates by remember { mutableStateOf<List<Contribution>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(Source.CASH) }
    var code by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var pledge by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var collectorId by remember { mutableStateOf<Long?>(null) }
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())

    fun save() {
        val cents = Money.parseToCents(amount)
        when {
            name.isBlank() -> error = "Enter a name"
            cents == null || cents <= 0 -> error = "Enter an amount"
            else -> scope.launch {
                val now = System.currentTimeMillis()
                // Typed entries have no M-Pesa code to check, so look for the same person and amount.
                if (!saving) {
                    val similar = repository.possibleDuplicates(campaignId, name, cents, now)
                    if (similar.isNotEmpty()) {
                        duplicates = similar
                        return@launch
                    }
                }
                saving = false
                val result = repository.addManual(
                    campaignId = campaignId, name = name, phone = phone.ifBlank { null }, amountCents = cents,
                    source = if (pledge) Source.OTHER else source,
                    mpesaCode = code.takeIf { !pledge && source == Source.MPESA_MANUAL },
                    receivedAt = System.currentTimeMillis(), note = note, pledged = pledge,
                    collectorId = collectorId.takeIf { !pledge },
                )
                when (result) {
                    is HarambeeRepository.AddResult.Added -> onBack()
                    HarambeeRepository.AddResult.DuplicateCode -> error = "M-Pesa code ${code.uppercase()} is already recorded"
                }
            }
        }
    }

    Scaffold(topBar = { BackTopBar(if (pledge) "Add pledge" else "Add contribution", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!pledge, { pledge = false }, label = { Text("Paid ✅") })
                FilterChip(pledge, { pledge = true }, label = { Text("Pledge (not yet paid)") })
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name as it should appear *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                amount, { amount = it }, label = { Text("Amount (KES) *") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            if (!pledge) {
                Text("Paid by", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Source.CASH to "Cash", Source.MPESA_MANUAL to "M-Pesa", Source.BANK to "Bank", Source.OTHER to "Other").forEach { (value, label) ->
                        FilterChip(source == value, { source = value }, label = { Text(label) })
                    }
                }
                if (source == Source.MPESA_MANUAL) {
                    OutlinedTextField(
                        code, { code = it.uppercase() }, label = { Text("M-Pesa code (recommended)") },
                        supportingText = { Text("Prevents the same payment being counted twice") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (!pledge) CollectorPicker(campaign, collectors, collectorId) { collectorId = it }
            OutlinedTextField(
                phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            if (pledge) {
                Text(
                    "When this person's M-Pesa payment arrives, the pledge is ticked ✅ automatically.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }

    if (duplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { duplicates = emptyList() },
            title = { Text("Possible duplicate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This looks like an entry already recorded:")
                    duplicates.forEach { d ->
                        Text(
                            "• ${d.listName ?: d.senderName} · KES ${Money.format(d.amountCents)} · ${Source.label(d.source)} · ${Formats.dateTime(d.receivedAt)}" +
                                if (d.status == Status.PLEDGED) " (pledge)" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Add it only if it's really a second contribution.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    duplicates = emptyList()
                    saving = true
                    save()
                }) { Text("Add anyway") }
            },
            dismissButton = { TextButton(onClick = { duplicates = emptyList() }) { Text("Cancel") } },
        )
    }
}

/** Details of one line: rename, exclude, move, or delete. */
@Composable
fun ContributionScreen(contributionId: Long, onBack: () -> Unit, onShareSingle: (campaignId: Long, contributionId: Long) -> Unit) {
    val container = appContainer()
    val repository = container.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val row by repository.contribution(contributionId).collectAsStateWithLifecycle(null)
    val campaigns by repository.campaigns.collectAsStateWithLifecycle(emptyList())
    val thankYou by container.settings.thankYouTemplate.value.collectAsStateWithLifecycle()
    var collectorId by remember { mutableStateOf<Long?>(null) }
    val collectorsFlow = remember(row?.contribution?.campaignId) { row?.contribution?.campaignId?.let { repository.collectors(it) } ?: flowOf(emptyList()) }
    val collectors by collectorsFlow.collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var applyToAll by remember { mutableStateOf(false) }
    var campaignId by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var initialised by remember { mutableStateOf(false) }
    var anonymous by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val r = row
    LaunchedEffect(r) {
        if (r != null && !initialised) {
            name = r.displayName
            amount = Money.format(r.contribution.amountCents)
            note = r.contribution.note
            campaignId = r.contribution.campaignId
            collectorId = r.contribution.collectorId
            anonymous = r.contribution.anonymous
            initialised = true
        }
    }

    Scaffold(topBar = { BackTopBar("Contribution", onBack) }) { padding ->
        if (r == null) return@Scaffold
        val k = r.contribution
        val fromMpesa = k.mpesaCode != null
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    LabelValue("Status", when (k.status) {
                        Status.COUNTED -> "Counted ✅"
                        Status.PLEDGED -> "Pledge (not yet paid)"
                        Status.EXCLUDED -> "Not counted"
                        Status.REVERSED -> "Reversed"
                        else -> "Waiting for review"
                    })
                    LabelValue("Method", Source.label(k.source))
                    if (fromMpesa) LabelValue("M-Pesa name", k.senderName)
                    k.senderPhone?.let { LabelValue("Phone", Phone.pretty(it)) }
                    k.mpesaCode?.let { LabelValue("M-Pesa code", it) }
                    LabelValue("Received", Formats.dateTime(k.receivedAt))
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name on the list") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (fromMpesa) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(applyToAll, { applyToAll = it })
                    Text("Use this name for every payment from this number")
                }
            } else {
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount (KES)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Anonymous")
                    Text("Shown as \"Well-wisher\" in anything shared", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(anonymous, { anonymous = it })
            }
            CollectorPicker(campaigns.firstOrNull { it.id == k.campaignId }, collectors, collectorId) { collectorId = it }
            if (campaigns.size > 1) {
                Text("Harambee", style = MaterialTheme.typography.titleSmall)
                CampaignPicker(campaigns, campaignId, { campaignId = it })
            }
            OutlinedTextField(
                reason, { reason = it; error = null },
                label = { Text("Reason for change") },
                supportingText = { Text("Kept in the activity history. Needed when changing an amount, moving or removing a counted entry.") },
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val cents = if (fromMpesa) k.amountCents else Money.parseToCents(amount) ?: k.amountCents
                    val significant = k.status == Status.COUNTED && (cents != k.amountCents || (campaignId ?: k.campaignId) != k.campaignId)
                    if (significant && reason.isBlank()) {
                        error = "Give a reason for this correction"
                        return@Button
                    }
                    scope.launch {
                        val listName = name.trim().takeIf { it.isNotEmpty() && it != k.senderName }
                        if (applyToAll) repository.setAlias(k.contributorKey, listName)
                        repository.updateContribution(
                            k.copy(
                                listName = if (applyToAll) null else listName,
                                amountCents = cents, note = note.trim(), campaignId = campaignId ?: k.campaignId,
                                collectorId = collectorId, anonymous = anonymous,
                            ),
                            reason,
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }

            if (k.status == Status.COUNTED) {
                OutlinedButton(
                    onClick = {
                        val harambee = campaigns.firstOrNull { it.id == k.campaignId }?.name ?: "the Harambee"
                        Sharing.messagePerson(context, k.senderPhone, Templates.fill(thankYou, r.displayName, k.amountCents, harambee, ""))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send thank-you message") }
                k.campaignId?.let { cid ->
                    OutlinedButton(onClick = { onShareSingle(cid, k.id) }, modifier = Modifier.fillMaxWidth()) { Text("Share this contribution to the group") }
                }
            }
            when (k.status) {
                Status.COUNTED -> OutlinedButton(
                    onClick = {
                        if (reason.isBlank()) {
                            error = "Give a reason before removing it from the total"
                        } else {
                            scope.launch { repository.updateContribution(k.copy(status = Status.EXCLUDED), reason); onBack() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Not a contribution — remove from total") }
                Status.PLEDGED -> OutlinedButton(
                    onClick = { scope.launch { repository.updateContribution(k.copy(status = Status.COUNTED), reason); onBack() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mark as paid ✅ (cash / other)") }
                Status.EXCLUDED -> OutlinedButton(
                    onClick = { scope.launch { repository.updateContribution(k.copy(status = Status.COUNTED), reason); onBack() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Count it again") }
                else -> Unit
            }
            if (!fromMpesa) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    "M-Pesa payments can't be deleted, only excluded, so the same message can never be counted twice.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            k.rawMessage?.let {
                Text("Original message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete this entry?") },
                text = { Text(if (reason.isBlank()) "Tip: write a reason first; it's kept in the activity history." else "Reason: $reason") },
                confirmButton = { TextButton(onClick = { scope.launch { repository.deleteContribution(k, reason); onBack() } }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

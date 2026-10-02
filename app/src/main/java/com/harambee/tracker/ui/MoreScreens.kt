package com.harambee.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.harambee.tracker.core.ClosingReport
import com.harambee.tracker.core.MemberMatcher
import com.harambee.tracker.core.MemberRef
import com.harambee.tracker.core.Money
import com.harambee.tracker.core.PaidRef
import com.harambee.tracker.core.Phone
import com.harambee.tracker.core.ReportData
import com.harambee.tracker.core.RosterParser
import com.harambee.tracker.core.Templates
import com.harambee.tracker.data.Campaign
import com.harambee.tracker.data.Collector
import com.harambee.tracker.data.ContributionRow
import com.harambee.tracker.data.Member
import com.harambee.tracker.data.Source
import com.harambee.tracker.data.Status
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Member id -> amount paid into this Harambee. */
fun memberPayments(members: List<Member>, rows: List<ContributionRow>): Map<Long, Long> = MemberMatcher.paidByMember(
    members.map { MemberRef(it.id, it.name, it.phone) },
    rows.filter { it.contribution.status == Status.COUNTED }.map {
        PaidRef(it.displayName, it.contribution.senderName, it.contribution.senderPhone, it.contribution.amountCents)
    },
)

@Composable
private fun groupMembers(campaign: Campaign?): List<Member> {
    val repository = appContainer().repository
    val flow = remember(campaign?.memberGroup) { campaign?.memberGroup?.let { repository.members(it) } ?: flowOf(emptyList()) }
    return flow.collectAsStateWithLifecycle(emptyList()).value
}

/** "Received by": this phone or another committee member. Hidden when there are no other collectors. */
@Composable
fun CollectorPicker(campaign: Campaign?, collectors: List<Collector>, selected: Long?, onSelect: (Long?) -> Unit) {
    if (collectors.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Text("Received by", style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(CampaignData.collectorLabel(campaign, collectors, selected))
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(CampaignData.collectorLabel(campaign, collectors, null) + " (this phone)") }, onClick = { open = false; onSelect(null) })
            collectors.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { open = false; onSelect(c.id) }) }
        }
    }
}

@Composable
fun ReportScreen(campaignId: Long, onBack: () -> Unit) {
    val repository = appContainer().repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())
    val members = groupMembers(campaign)
    var combine by remember { mutableStateOf(true) }
    val c = campaign ?: return

    val counted = rows.filter { it.contribution.status == Status.COUNTED }
    val paidMembers = if (members.isEmpty()) null else memberPayments(members, rows).size
    val text = ClosingReport.build(
        ReportData(
            name = c.name,
            firstPayment = counted.filter { it.contribution.source != Source.WHATSAPP_LIST }.minOfOrNull { it.contribution.receivedAt },
            lastPayment = counted.filter { it.contribution.source != Source.WHATSAPP_LIST }.maxOfOrNull { it.contribution.receivedAt },
            lines = CampaignData.lines(rows),
            byMethod = counted.groupBy { Source.label(it.contribution.source).substringBefore(" (") }
                .map { (k, v) -> k to v.sumOf { it.contribution.amountCents } }.sortedByDescending { it.second },
            byCollector = counted.groupBy { CampaignData.collectorLabel(c, collectors, it.contribution.collectorId) }
                .map { (k, v) -> k to v.sumOf { it.contribution.amountCents } }.sortedByDescending { it.second },
            targetCents = c.targetCents,
            membersPaid = paidMembers,
            membersTotal = members.size.takeIf { it > 0 },
            footer = c.footer,
        ),
        combine,
    )

    Scaffold(topBar = { BackTopBar("Closing report", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Sharing.toWhatsApp(context, text) }, modifier = Modifier.weight(1f)) { Text("WhatsApp") }
                OutlinedButton(onClick = { Sharing.sharePdf(context, ReportPdf.create(context, fileName(c.name, "report"), "${c.name} — Final report", text)) }) { Text("PDF") }
                OutlinedButton(onClick = { Sharing.copy(context, text) }) { Text("Copy") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("One line per person", Modifier.weight(1f))
                Switch(combine, { combine = it })
            }
            if (c.isActive) {
                OutlinedButton(
                    onClick = { scope.launch { repository.saveCampaign(c.copy(isActive = false, endAt = System.currentTimeMillis())) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Close this Harambee (stop collecting)") }
            } else {
                Text("This Harambee is closed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
fun PledgesScreen(campaignId: Long, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val container = appContainer()
    val repository = container.repository
    val context = LocalContext.current
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())
    val template by container.settings.pledgeReminderTemplate.value.collectAsStateWithLifecycle()
    val c = campaign ?: return
    val pledges = rows.filter { it.contribution.status == Status.PLEDGED }
    val payTo = CampaignData.payToLabel(c, collectors)

    Scaffold(topBar = { BackTopBar("Pledge reminders", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pledges.size} unpaid pledges · ${Money.formatKes(pledges.sumOf { it.contribution.amountCents })}", style = MaterialTheme.typography.titleMedium)
                    Button(
                        enabled = pledges.isNotEmpty(),
                        onClick = {
                            Sharing.toWhatsApp(context, Templates.pendingList("Pending pledges — ${c.name}", pledges.map { it.displayName to it.contribution.amountCents }, payTo))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Share pending list to the group") }
                    Text("Or remind people one by one (opens WhatsApp or SMS). Edit the wording in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(pledges, key = { it.contribution.id }) { row ->
                PersonRow(
                    name = row.displayName,
                    detail = listOfNotNull(Money.formatKes(row.contribution.amountCents), Phone.pretty(row.contribution.senderPhone).ifBlank { null }).joinToString(" · "),
                    action = "Remind",
                    onAction = {
                        Sharing.messagePerson(context, row.contribution.senderPhone, Templates.fill(template, row.displayName, row.contribution.amountCents, c.name, payTo))
                    },
                    onClick = { onOpen(row.contribution.id) },
                )
            }
        }
    }
}

@Composable
private fun PersonRow(name: String, detail: String, action: String?, onAction: () -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

/** Which members of the group have / have not contributed to this Harambee. */
@Composable
fun MemberStatusScreen(campaignId: Long, onBack: () -> Unit, onEdit: () -> Unit, onManageMembers: () -> Unit) {
    val container = appContainer()
    val repository = container.repository
    val context = LocalContext.current
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())
    val template by container.settings.memberReminderTemplate.value.collectAsStateWithLifecycle()
    val members = groupMembers(campaign)
    var showPaid by remember { mutableStateOf(false) }
    val c = campaign ?: return
    val paid = memberPayments(members, rows)
    val notYet = members.filter { it.id !in paid }
    val payTo = CampaignData.payToLabel(c, collectors)

    Scaffold(topBar = { BackTopBar("Members", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (c.memberGroup == null) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This Harambee isn't linked to a members group. Link one to see who has and hasn't contributed.")
                        Button(onClick = onEdit) { Text("Choose a group") }
                    }
                }
                return@LazyColumn
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${paid.size} of ${members.size} members of ${c.memberGroup} have contributed", style = MaterialTheme.typography.titleMedium)
                    c.expectedCents?.let { Text("Expected: ${Money.formatKes(it)} each", style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!showPaid, { showPaid = false }, label = { Text("Not yet (${notYet.size})") })
                        FilterChip(showPaid, { showPaid = true }, label = { Text("Paid (${paid.size})") })
                    }
                    if (!showPaid) {
                        Button(
                            enabled = notYet.isNotEmpty(),
                            onClick = { Sharing.toWhatsApp(context, Templates.pendingList("Yet to contribute — ${c.name}", notYet.map { it.name to c.expectedCents }, payTo)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Share \"yet to contribute\" list to the group") }
                    }
                    TextButton(onClick = onManageMembers) { Text("Edit members list") }
                }
            }
            if (members.isEmpty()) item { Text("The group has no members yet.", Modifier.padding(16.dp)) }
            val shown = if (showPaid) members.filter { it.id in paid } else notYet
            items(shown, key = { it.id }) { m ->
                if (showPaid) {
                    val amount = paid[m.id] ?: 0
                    val short = c.expectedCents?.let { amount < it } == true
                    PersonRow(m.name, Money.formatKes(amount) + if (short) " · below expected" else " ✅", null, {})
                } else {
                    PersonRow(m.name, Phone.pretty(m.phone), "Remind", {
                        Sharing.messagePerson(context, m.phone, Templates.fill(template, m.name, c.expectedCents, c.name, payTo))
                    })
                }
            }
        }
    }
}

/** Other committee members whose numbers also collect for this Harambee. */
@Composable
fun CollectorsScreen(campaignId: Long, onBack: () -> Unit) {
    val repository = appContainer().repository
    val scope = rememberCoroutineScope()
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    val collectors by repository.collectors(campaignId).collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val counted = rows.filter { it.contribution.status == Status.COUNTED }
    fun totalFor(id: Long?) = counted.filter { it.contribution.collectorId == id }.sumOf { it.contribution.amountCents }

    Scaffold(topBar = { BackTopBar("Collectors", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Add committee members who also receive contributions on their own M-Pesa numbers. Their numbers appear in the WhatsApp update. " +
                    "This phone can't see their M-Pesa messages: ask them to forward the messages to you, then share or paste them into Import and choose who received them.",
                style = MaterialTheme.typography.bodySmall,
            )
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    LabelValue("${CampaignData.collectorLabel(campaign, collectors, null)} (this phone)", Money.formatKes(totalFor(null)))
                    collectors.forEach { col ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LabelValue("${col.name} ${Phone.pretty(col.phone)}", Money.formatKes(totalFor(col.id)), Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { repository.deleteCollector(col) } }) { Icon(Icons.Default.Delete, "Remove ${col.name}") }
                        }
                    }
                }
            }
            Text("Add a collector", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                phone, { phone = it }, label = { Text("M-Pesa number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = { scope.launch { repository.addCollector(campaignId, name, phone); name = ""; phone = "" } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add") }
        }
    }
}

/** Members groups (welfare, church, workplace) reused across Harambees. */
@Composable
fun MembersScreen(onBack: () -> Unit) {
    val repository = appContainer().repository
    val scope = rememberCoroutineScope()
    val groups by repository.groups.collectAsStateWithLifecycle(emptyList())
    val campaigns by repository.campaigns.collectAsStateWithLifecycle(emptyList())
    var group by remember { mutableStateOf<String?>(null) }
    var newGroup by remember { mutableStateOf("") }
    LaunchedEffect(groups) { if (group == null || group !in groups) group = group?.takeIf { it.isNotBlank() } ?: groups.firstOrNull() }
    val membersFlow = remember(group) { group?.let { repository.members(it) } ?: flowOf(emptyList()) }
    val members by membersFlow.collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var fromCampaign by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackTopBar("Members groups", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "For welfare, church or workplace groups where every member is expected to contribute. Link a group to a Harambee (Edit Harambee) to see who hasn't contributed yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    (groups + listOfNotNull(group?.takeIf { it !in groups })).forEach { g ->
                        FilterChip(group == g, { group = g }, label = { Text(g) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newGroup, { newGroup = it }, label = { Text("New group name") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = newGroup.isNotBlank(), onClick = { group = newGroup.trim(); newGroup = "" }) { Text("Create") }
                }
            }
            val g = group ?: return@LazyColumn
            item { Text("$g · ${members.size} members", style = MaterialTheme.typography.titleMedium) }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add one member", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                        Button(enabled = name.isNotBlank(), onClick = {
                            scope.launch {
                                val n = repository.addMembers(g, listOf(name to phone.ifBlank { null }))
                                message = if (n == 0) "Already in the group" else "Added $name"
                                name = ""; phone = ""
                            }
                        }) { Text("Add") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paste a list", style = MaterialTheme.typography.titleSmall)
                        Text("One name per line; numbers like \"1.\", phone numbers and amounts are handled. A WhatsApp contribution list works too.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(pasted, { pasted = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
                        val parsed = remember(pasted) { RosterParser.parse(pasted) }
                        Button(enabled = parsed.isNotEmpty(), onClick = {
                            scope.launch { message = "Added ${repository.addMembers(g, parsed)} of ${parsed.size} names"; pasted = "" }
                        }) { Text("Add ${parsed.size} names") }
                    }
                }
            }
            if (campaigns.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Add everyone who contributed to a Harambee", style = MaterialTheme.typography.titleSmall)
                            CampaignPicker(campaigns, fromCampaign, { fromCampaign = it })
                            Button(enabled = fromCampaign != null, onClick = {
                                scope.launch { message = "Added ${repository.addContributorsAsMembers(fromCampaign!!, g)} members" }
                            }) { Text("Add contributors") }
                        }
                    }
                }
            }
            items(members, key = { it.id }) { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name)
                        if (m.phone != null) Text(Phone.pretty(m.phone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch { repository.deleteMember(m) } }) { Icon(Icons.Default.Delete, "Remove ${m.name}") }
                }
                HorizontalDivider()
            }
        }
    }
}

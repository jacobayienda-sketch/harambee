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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.harambee.tracker.core.Money
import com.harambee.tracker.core.MpesaParser
import com.harambee.tracker.data.Campaign
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignEditScreen(campaignId: Long?, onDone: (Long) -> Unit, onBack: () -> Unit) {
    val repository = appContainer().repository
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Campaign?>(null) }
    var name by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var payToName by remember { mutableStateOf("") }
    var payToNumber by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("Thanks for your generous contribution 🙏") }
    var startAt by remember { mutableLongStateOf(Formats.startOfToday()) }
    var active by remember { mutableStateOf(true) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(campaignId) {
        if (campaignId != null) {
            repository.getCampaign(campaignId)?.let { c ->
                loaded = c
                name = c.name
                intro = c.intro
                target = c.targetCents?.let { Money.format(it) } ?: ""
                payToName = c.payToName
                payToNumber = c.payToNumber
                footer = c.footer
                startAt = c.startAt
                active = c.isActive
            }
        }
    }

    Scaffold(topBar = { BackTopBar(if (campaignId == null) "New Harambee" else "Edit Harambee", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name *") }, placeholder = { Text("e.g. Agnes' sister burial") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                intro, { intro = it },
                label = { Text("Appeal message (top of each WhatsApp update)") },
                placeholder = { Text("Good morning colleagues. Following the demise of…") },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
            )
            OutlinedTextField(payToName, { payToName = it }, label = { Text("Send-to name") }, placeholder = { Text("Eliud Murkomen") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                payToNumber, { payToNumber = it },
                label = { Text("Send-to M-Pesa number") }, placeholder = { Text("0723 934 660") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                target, { target = it },
                label = { Text("Target (KES, optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(footer, { footer = it }, label = { Text("Closing line") }, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Count payments from", style = MaterialTheme.typography.titleSmall)
                    Text(Formats.date(startAt), style = MaterialTheme.typography.bodyMedium)
                    Text("M-Pesa money received earlier is ignored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { pickingDate = true }) { Text("Change") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Collecting now", style = MaterialTheme.typography.titleSmall)
                    Text("Incoming M-Pesa payments are offered to this Harambee.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(active, { active = it })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val targetCents = target.takeIf { it.isNotBlank() }?.let { Money.parseToCents(it) }
                    when {
                        name.isBlank() -> error = "Give the Harambee a name"
                        target.isNotBlank() && targetCents == null -> error = "Target must be a number"
                        else -> scope.launch {
                            val base = loaded ?: Campaign(name = "", startAt = startAt)
                            val id = repository.saveCampaign(
                                base.copy(
                                    name = name.trim(), intro = intro.trim(), targetCents = targetCents,
                                    payToName = payToName.trim(), payToNumber = payToNumber.trim(),
                                    footer = footer.trim(), startAt = startAt, isActive = active,
                                ),
                            )
                            onDone(id)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (pickingDate) {
        // The date picker works in UTC midnights; convert to/from Nairobi local dates.
        val initialUtc = Instant.ofEpochMilli(startAt).atZone(MpesaParser.NAIROBI).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        startAt = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(MpesaParser.NAIROBI).toInstant().toEpochMilli()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

package com.harambee.tracker.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.core.UpdateContent
import com.harambee.tracker.core.UpdateLine
import com.harambee.tracker.core.UpdateOptions
import com.harambee.tracker.core.WhatsAppUpdateBuilder
import com.harambee.tracker.data.Status

@Composable
fun UpdateScreen(campaignId: Long, onBack: () -> Unit) {
    val repository = appContainer().repository
    val context = LocalContext.current
    val campaign by repository.campaign(campaignId).collectAsStateWithLifecycle(null)
    val rows by repository.contributions(campaignId).collectAsStateWithLifecycle(emptyList())
    var options by remember { mutableStateOf(UpdateOptions()) }

    val c = campaign
    val text = if (c == null) "" else WhatsAppUpdateBuilder.build(
        UpdateContent(c.intro, c.payToName, c.payToNumber, c.footer, c.targetCents),
        rows.filter { it.contribution.status == Status.COUNTED || it.contribution.status == Status.PLEDGED }.map {
            UpdateLine(it.displayName, it.contribution.amountCents, it.contribution.status == Status.COUNTED, it.contribution.receivedAt)
        },
        options,
        System.currentTimeMillis(),
    )

    Scaffold(topBar = { BackTopBar("WhatsApp update", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Sharing.toWhatsApp(context, text) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share to WhatsApp")
                }
                OutlinedButton(onClick = { Sharing.copy(context, text) }) { Text("Copy") }
            }
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            Text("Options", style = MaterialTheme.typography.titleSmall)
            Option("Show total and balance", options.showTotal) { options = options.copy(showTotal = it) }
            Option("Show target", options.showTarget) { options = options.copy(showTarget = it) }
            Option("Include pledges (no ✅)", options.showPledges) { options = options.copy(showPledges = it) }
            Option("Add a blank next number for people to fill", options.addNextNumber) { options = options.copy(addNextNumber = it) }
            Option("Show \"Updated\" date and time", options.showDate) { options = options.copy(showDate = it) }
            Option("Biggest amounts first", options.sortByAmount) { options = options.copy(sortByAmount = it) }
            Text(
                "Edit the appeal message, send-to number or closing line from the Harambee's Edit screen.",
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

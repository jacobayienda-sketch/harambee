package com.harambee.tracker.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.BuildConfig
import com.harambee.tracker.core.MpesaParser
import com.harambee.tracker.core.WhatsAppListParser
import com.harambee.tracker.data.ImportSummary
import com.harambee.tracker.sms.SmsSources
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(initialCampaignId: Long?, initialText: String, onBack: () -> Unit, onReview: () -> Unit, onOpenCampaign: (Long) -> Unit) {
    val repository = appContainer().repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campaigns by repository.campaigns.collectAsStateWithLifecycle(emptyList())
    var campaignId by remember { mutableStateOf(initialCampaignId) }
    var text by remember { mutableStateOf(initialText) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var collectorId by remember { mutableStateOf<Long?>(null) }
    val collectorsFlow = remember(campaignId) { campaignId?.let { repository.collectors(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val collectors by collectorsFlow.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(campaigns) { if (campaignId == null) campaignId = campaigns.firstOrNull { it.isActive }?.id ?: campaigns.firstOrNull()?.id }

    val campaign = campaigns.firstOrNull { it.id == campaignId }
    val messages = remember(text) { MpesaParser.splitMessages(text) }
    val list = remember(text) { if (messages.isEmpty()) WhatsAppListParser.parse(text) else null }

    fun scan() {
        val c = campaign ?: return
        busy = true
        scope.launch {
            val summary = SmsSources.scanInbox(context, c.startAt, c.id)
            busy = false
            result = "Inbox since ${Formats.date(c.startAt)}: ${summary.describe()}"
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) scan() }

    Scaffold(topBar = { BackTopBar("Import", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (campaigns.isEmpty()) {
                Text("Create a Harambee first.")
                return@Column
            }
            Text("Into", style = MaterialTheme.typography.titleSmall)
            CampaignPicker(campaigns, campaignId, { campaignId = it; result = null })

            result?.let { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(r)
                        if (r.contains("review")) OutlinedButton(onClick = onReview) { Text("Review payments") }
                        campaignId?.let { id -> OutlinedButton(onClick = { onOpenCampaign(id) }) { Text("Open Harambee") } }
                    }
                }
            }

            // The Play Store edition has no SMS access; pasting and sharing still work.
            if (BuildConfig.SMS_CAPTURE) {
                Text("1. Messages already on this phone", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Finds M-Pesa payments received since ${campaign?.let { Formats.date(it.startAt) } ?: "the start date"}. Anything already recorded is skipped. New ones go to Review so you can confirm each.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = !busy && campaign != null,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) scan()
                        else permission.launch(Manifest.permission.READ_SMS)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Scanning…" else "Scan M-Pesa inbox") }
            }

            Text(if (BuildConfig.SMS_CAPTURE) "2. Paste M-Pesa messages or a WhatsApp list" else "Paste M-Pesa messages or a WhatsApp list", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste forwarded M-Pesa confirmations (e.g. from the treasurer's phone), or the contribution list already going round the group — names with ✅ count as paid, names without are pledges.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(text, { text = it }, label = { Text("Paste here") }, minLines = 6, modifier = Modifier.fillMaxWidth())

            val detected = when {
                messages.isNotEmpty() -> "Found ${messages.size} M-Pesa message(s)"
                list != null && list.entries.isNotEmpty() ->
                    "Found a list of ${list.entries.size} names: ${list.entries.count { it.paid }} paid ✅, ${list.entries.count { !it.paid }} pledges"
                text.isNotBlank() -> "Nothing recognised yet"
                else -> null
            }
            detected?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (messages.isNotEmpty()) {
                CollectorPicker(campaign, collectors, collectorId) { collectorId = it }
                Text(
                    "Pasted messages can't be verified as genuine Safaricom messages. Check the codes against the M-Pesa statement if unsure.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                enabled = !busy && campaign != null && (messages.isNotEmpty() || (list?.entries?.isNotEmpty() == true)),
                onClick = {
                    val c = campaign ?: return@Button
                    busy = true
                    scope.launch {
                        result = if (messages.isNotEmpty()) {
                            var summary = ImportSummary()
                            val now = System.currentTimeMillis()
                            messages.forEach { summary += repository.ingestSms(it, now, autoConfirm = true, forcedCampaignId = c.id, collectorId = collectorId) }
                            "Pasted messages: ${summary.describe()}"
                        } else {
                            val s = repository.importWhatsAppList(c.id, list!!)
                            "List imported: ${s.added} paid, ${s.pledges} pledges added · ${s.linked} matched existing entries · ${s.skipped} already there"
                        }
                        text = ""
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import pasted text") }
        }
    }
}

package com.harambee.tracker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.core.Money
import com.harambee.tracker.data.CampaignSummary

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.READ_SMS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun hasAllPermissions(context: Context) = requiredPermissions().all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCampaign: (Long) -> Unit,
    onNewCampaign: () -> Unit,
    onReview: () -> Unit,
    onImport: () -> Unit,
) {
    val container = appContainer()
    val context = LocalContext.current
    val summaries by container.repository.campaignSummaries.collectAsStateWithLifecycle(emptyList())
    val pendingCount by container.repository.pendingCount.collectAsStateWithLifecycle(0)
    val askBeforeAdding by container.settings.askBeforeAdding.collectAsStateWithLifecycle()
    var permitted by remember { mutableStateOf(hasAllPermissions(context)) }
    var menuOpen by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = hasAllPermissions(context)
    }
    LifecycleResumeEffect(Unit) {
        permitted = hasAllPermissions(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Harambee Tracker") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Import M-Pesa messages or a list") }, onClick = { menuOpen = false; onImport() })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewCampaign, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New Harambee") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!permitted) {
                item {
                    InfoCard(
                        title = "Turn on automatic capture",
                        body = "Allow SMS access so M-Pesa confirmations are picked up the moment money arrives, and notifications so you can add them with one tap. Messages never leave your phone.",
                        action = "Allow",
                        onAction = { launcher.launch(requiredPermissions()) },
                    )
                }
            }
            if (pendingCount > 0) {
                item {
                    InfoCard(
                        title = if (pendingCount == 1) "1 payment to review" else "$pendingCount payments to review",
                        body = "Decide whether each M-Pesa payment is a contribution.",
                        action = "Review",
                        onAction = onReview,
                        highlight = true,
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ask before adding", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (askBeforeAdding) "Each M-Pesa payment pops up; you choose Add ✅ or Not a contribution."
                                else "Payments go straight into the active Harambee. Use this only if the number receives nothing else.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = askBeforeAdding, onCheckedChange = { container.settings.setAskBeforeAdding(it) })
                    }
                }
            }
            if (summaries.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No Harambee yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Create one for the fundraiser you are collecting for. Contributions sent to your M-Pesa number will be tallied for you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(summaries, key = { it.campaign.id }) { summary ->
                CampaignCard(summary, onClick = { onOpenCampaign(summary.campaign.id) })
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, action: String, onAction: () -> Unit, highlight: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun CampaignCard(summary: CampaignSummary, onClick: () -> Unit) {
    val c = summary.campaign
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (c.isActive) "Collecting" else "Closed",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (c.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(Money.formatKes(summary.totalCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val details = buildList {
                add("${summary.paidCount} contributions")
                if (summary.pledgedCents > 0) add("${Money.formatKes(summary.pledgedCents)} pledged")
            }.joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val target = c.targetCents
            if (target != null && target > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (summary.totalCents.toFloat() / target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${summary.totalCents * 100 / target}% of ${Money.formatKes(target)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.BuildConfig
import com.harambee.tracker.core.Money
import com.harambee.tracker.data.CampaignSummary

private fun requiredPermissions(): Array<String> = buildList {
    if (BuildConfig.SMS_CAPTURE) {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
    }
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
    onMembers: () -> Unit,
    onSettings: () -> Unit,
    onBackup: () -> Unit,
) {
    val container = appContainer()
    val context = LocalContext.current
    val today = remember { Formats.startOfToday() }
    val summaries by remember(today) { container.repository.campaignSummaries(today) }.collectAsStateWithLifecycle(emptyList())
    val pendingCount by container.repository.pendingCount.collectAsStateWithLifecycle(0)
    val lastBackup by container.settings.lastBackupAt.value.collectAsStateWithLifecycle()
    var permitted by remember { mutableStateOf(hasAllPermissions(context)) }
    var menuOpen by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = hasAllPermissions(context)
    }
    LifecycleResumeEffect(Unit) {
        permitted = hasAllPermissions(context)
        onPauseOrDispose { }
    }
    val active = summaries.filter { it.campaign.isActive }
    val backupDue = summaries.isNotEmpty() && (lastBackup == 0L || System.currentTimeMillis() - lastBackup > 7 * 24 * 3600_000L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Harambee Tracker", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Import M-Pesa messages or a list") }, onClick = { menuOpen = false; onImport() })
                        DropdownMenuItem(text = { Text("Members groups") }, onClick = { menuOpen = false; onMembers() })
                        DropdownMenuItem(text = { Text("Backup & restore") }, onClick = { menuOpen = false; onBackup() })
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
            if (active.isNotEmpty()) item { Overview(active) }
            if (pendingCount > 0) {
                item {
                    Banner(
                        title = if (pendingCount == 1) "1 payment to review" else "$pendingCount payments to review",
                        body = "Decide whether each M-Pesa payment is a contribution.",
                        action = "Review",
                        onAction = onReview,
                        highlight = true,
                    )
                }
            }
            if (!permitted) {
                item {
                    Banner(
                        title = if (BuildConfig.SMS_CAPTURE) "Turn on automatic capture" else "Turn on notifications",
                        body = if (BuildConfig.SMS_CAPTURE) {
                            "Allow SMS access so M-Pesa confirmations are picked up the moment money arrives, and notifications so you can add them with one tap. Messages never leave your phone."
                        } else {
                            "Get a reminder to share the updated list after adding payments."
                        },
                        action = "Allow",
                        onAction = { launcher.launch(requiredPermissions()) },
                    )
                }
            }
            if (backupDue) {
                item {
                    Banner(
                        title = if (lastBackup == 0L) "Protect your records" else "Time for a backup",
                        body = "Save a backup to Google Drive or WhatsApp so a lost or reset phone doesn't lose the contribution records.",
                        action = "Back up now",
                        onAction = onBackup,
                    )
                }
            }
            if (summaries.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🤝", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("No Harambee yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Create one for the fundraiser you're collecting for. Contributions sent to your M-Pesa number are tallied for you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onNewCampaign) { Text("Create a Harambee") }
                    }
                }
            }
            items(summaries, key = { it.campaign.id }) { summary ->
                CampaignCard(summary, onClick = { onOpenCampaign(summary.campaign.id) })
            }
        }
    }
}

/** Big number across everything being collected now, and how today is going. */
@Composable
private fun Overview(active: List<CampaignSummary>) {
    val total = active.sumOf { it.totalCents }
    val todayCents = active.sumOf { it.todayCents }
    val todayCount = active.sumOf { it.todayCount }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(if (active.size == 1) "Collected so far" else "Collected across ${active.size} Harambees", style = MaterialTheme.typography.labelLarge)
            Text(Money.formatKes(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (todayCount == 0) "Nothing yet today" else "Today: +${Money.formatKes(todayCents)} from $todayCount ${if (todayCount == 1) "person" else "people"}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Banner(title: String, body: String, action: String, onAction: () -> Unit, highlight: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            if (highlight) Button(onClick = onAction) { Text(action) } else TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun CampaignCard(summary: CampaignSummary, onClick: () -> Unit) {
    val c = summary.campaign
    val target = c.targetCents?.takeIf { it > 0 }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (c.isActive) "● Collecting" else "Closed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (c.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(Money.formatKes(summary.totalCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val details = buildList {
                    add("${summary.paidCount} contributions")
                    if (summary.pledgedCents > 0) add("${Money.formatKes(summary.pledgedCents)} pledged")
                    if (summary.todayCount > 0) add("+${Money.format(summary.todayCents)} today")
                }.joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                summary.lastPaymentAt?.let {
                    Text("Last payment ${Formats.dateTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (target != null) {
                Spacer(Modifier.size(12.dp))
                ProgressRing(summary.totalCents, target)
            }
        }
    }
}

@Composable
private fun ProgressRing(total: Long, target: Long) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { (total.toFloat() / target).coerceIn(0f, 1f) },
            modifier = Modifier.size(56.dp),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        Text("${(total * 100 / target).coerceAtMost(999)}%", style = MaterialTheme.typography.labelMedium)
    }
}

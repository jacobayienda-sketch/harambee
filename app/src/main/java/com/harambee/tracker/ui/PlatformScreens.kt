package com.harambee.tracker.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.BackupManager
import com.harambee.tracker.BuildConfig
import com.harambee.tracker.Settings
import com.harambee.tracker.data.ActivityEntry
import com.harambee.tracker.data.BackupFile
import com.harambee.tracker.sms.Notifier
import kotlinx.coroutines.launch

// ---------- Lock ----------

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(24.dp))
            Text("Harambee Tracker is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Contribution records are protected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}

// ---------- First run ----------

private data class IntroPage(val emoji: String, val title: String, val body: String)

private val introPages = listOf(
    IntroPage(
        "🤝", "Collect on your own M-Pesa",
        "People send contributions to your normal number. Harambee Tracker reads each M-Pesa confirmation, keeps the running total and writes the WhatsApp list for you.",
    ),
    IntroPage(
        "✅", "Nothing counted twice. Nothing lost.",
        "Every M-Pesa code is recorded once. Only real messages from MPESA are read. Your records live in the app, so deleting SMS later changes nothing, and daily backup copies protect them.",
    ),
    IntroPage(
        "📲", "You stay in control",
        "When money arrives you get a pop-up: Add ✅ or Not a contribution. Then share the updated list to the group in one tap. Everything stays on your phone.",
    ),
)

@Composable
fun OnboardingScreen(onDone: (createFirst: Boolean) -> Unit) {
    val settings = appContainer().settings
    val pager = rememberPagerState { introPages.size }
    val scope = rememberCoroutineScope()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    fun finish(createFirst: Boolean) {
        settings.onboarded.set(true)
        onDone(createFirst)
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { finish(false) }) { Text("Skip") }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                val page = introPages[index]
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (index == 0) AppLogo(112.dp) else Text(page.emoji, fontSize = 72.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(page.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(introPages.size) { i ->
                    Surface(
                        shape = CircleShape,
                        color = if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(4.dp).size(if (pager.currentPage == i) 10.dp else 8.dp),
                    ) {}
                }
            }
            Column(Modifier.padding(24.dp, 0.dp, 24.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pager.currentPage < introPages.lastIndex) {
                    Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                } else {
                    Button(
                        onClick = {
                            permissions.launch(
                                buildList {
                                    if (BuildConfig.SMS_CAPTURE) {
                                        add(Manifest.permission.RECEIVE_SMS)
                                        add(Manifest.permission.READ_SMS)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                                }.toTypedArray(),
                            )
                            finish(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Allow access & create my first Harambee") }
                    OutlinedButton(onClick = { finish(false) }, modifier = Modifier.fillMaxWidth()) { Text("Look around first") }
                }
            }
        }
    }
}

/** The app icon artwork, for the welcome screen. */
@Composable
fun AppLogo(size: androidx.compose.ui.unit.Dp) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(size * 0.24f), color = androidx.compose.ui.graphics.Color(0xFF0B6E4F), modifier = Modifier.size(size)) {
        // The foreground is drawn on the 108-unit adaptive canvas; zoom past its safe-zone margin.
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.harambee.tracker.R.drawable.ic_launcher_foreground),
            contentDescription = "Harambee Tracker",
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.45f, scaleY = 1.45f),
        )
    }
}

// ---------- Settings ----------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun LinkRow(title: String, body: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick) { Text("Open") }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onBackup: () -> Unit, onActivity: () -> Unit) {
    val container = appContainer()
    val settings = container.settings
    val context = LocalContext.current
    val ask by settings.askBeforeAdding.value.collectAsStateWithLifecycle()
    val catchUp by settings.autoCatchUp.value.collectAsStateWithLifecycle()
    val lastScan by settings.lastScanAt.value.collectAsStateWithLifecycle()
    val theme by settings.themeMode.value.collectAsStateWithLifecycle()
    val dynamic by settings.dynamicColor.value.collectAsStateWithLifecycle()
    val lock by settings.appLock.value.collectAsStateWithLifecycle()
    val lastBackup by settings.lastBackupAt.value.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar("Settings", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Incoming payments") {
                SwitchRow("Ask before adding", "Pop up each M-Pesa payment so you choose Add ✅ or Not a contribution. Off: payments go straight into the active Harambee.", ask) { settings.askBeforeAdding.set(it) }
                if (BuildConfig.SMS_CAPTURE) {
                    SwitchRow(
                        "Catch up on missed messages",
                        "When the app opens, check the inbox for M-Pesa payments that arrived while it wasn't running." +
                            if (lastScan > 0) " Last check: ${Formats.dateTime(lastScan)}." else "",
                        catchUp,
                    ) { settings.autoCatchUp.set(it) }
                    if (catchUp) {
                        TextButton(onClick = {
                            container.catchUp(minIntervalMs = 0)
                            Toast.makeText(context, "Checking the inbox…", Toast.LENGTH_SHORT).show()
                        }) { Text("Check now") }
                    }
                }
                LinkRow("Pop-up sound and style", "Android notification settings for incoming payments") {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_PAYMENTS),
                    )
                }
            }

            Section("Data & backup") {
                LinkRow(
                    "Backup & restore",
                    if (lastBackup == 0L) "No backup saved yet. Save one to Google Drive or WhatsApp so a lost phone doesn't mean lost records."
                    else "Last backup: ${Formats.dateTime(lastBackup)}",
                    onBackup,
                )
                LinkRow("Activity history", "Every addition, edit and removal, with the time", onActivity)
            }

            Section("Security") {
                SwitchRow("App lock", "Ask for fingerprint, face or phone PIN when opening the app. Also hides it in the recent-apps view.", lock) { on ->
                    if (on && !context.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
                        Toast.makeText(context, "Set a screen lock on your phone first", Toast.LENGTH_LONG).show()
                    } else {
                        settings.appLock.set(on)
                    }
                }
            }

            Section("Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Settings.THEME_SYSTEM to "Phone setting", Settings.THEME_LIGHT to "Light", Settings.THEME_DARK to "Dark").forEach { (value, label) ->
                        FilterChip(theme == value, { settings.themeMode.set(value) }, label = { Text(label) })
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Wallpaper colours", "Match the app's colours to your phone's wallpaper", dynamic) { settings.dynamicColor.set(it) }
                }
            }

            Section("Message wording") {
                Text("{name}, {amount}, {harambee} and {payto} are filled in for you.", style = MaterialTheme.typography.bodySmall)
                TemplateField("Thank-you message", settings.thankYouTemplate)
                TemplateField("Pledge reminder", settings.pledgeReminderTemplate)
                TemplateField("Member reminder", settings.memberReminderTemplate)
            }

            Section("About") {
                LabelValue("Version", BuildConfig.VERSION_NAME)
                LabelValue("Edition", if (BuildConfig.SMS_CAPTURE) "Automatic M-Pesa capture" else "Play Store (paste / share)")
                Text(
                    "Privacy: your records stay on this phone. The app reads only messages from MPESA and sends nothing anywhere unless you share it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TemplateField(label: String, setting: Settings.TextSetting) {
    val value by setting.value.collectAsStateWithLifecycle()
    var text by remember(value) { mutableStateOf(value) }
    Column {
        OutlinedTextField(text, { text = it }, label = { Text(label) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Row {
            TextButton(enabled = text != value, onClick = { setting.set(text) }) { Text("Save") }
            TextButton(enabled = value != setting.default, onClick = { setting.set(setting.default) }) { Text("Reset") }
        }
    }
}

// ---------- Backup ----------

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val container = appContainer()
    val backups = container.backups
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastBackup by container.settings.lastBackupAt.value.collectAsStateWithLifecycle()
    var snapshots by remember { mutableStateOf<List<BackupManager.Snapshot>>(emptyList()) }
    var pending by remember { mutableStateOf<BackupFile?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { snapshots = backups.snapshots() }

    fun report(block: suspend () -> String) {
        scope.launch {
            message = try {
                block()
            } catch (e: Exception) {
                e.message ?: "Something went wrong"
            }
            refresh++
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) report { backups.exportTo(uri); "Backup saved ✅" }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) report { pending = backups.read(uri); "" }
    }

    Scaffold(topBar = { BackTopBar("Backup & restore", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val stale = lastBackup == 0L || System.currentTimeMillis() - lastBackup > 7 * 24 * 3600_000L
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (stale) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (lastBackup == 0L) "No backup saved yet" else "Last backup ${Formats.dateTime(lastBackup)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Your records are kept inside the app, so deleting M-Pesa SMS later changes nothing. " +
                                "But if the phone is lost, reset or the app is uninstalled, only a backup brings them back.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            message?.takeIf { it.isNotBlank() }?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { saveFile.launch(backups.suggestedFileName()) }, modifier = Modifier.fillMaxWidth()) { Text("Save backup to a file / Google Drive") }
                    OutlinedButton(
                        onClick = { report { Sharing.shareFile(context, backups.exportForSharing(), "application/json", "Send backup"); "" } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Send backup (WhatsApp, email…)") }
                    OutlinedButton(onClick = { openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore from a backup file")
                    }
                }
            }
            item {
                Text("Automatic copies", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Made every day and before any restore, kept on this phone (and in Android's own Google backup if it's on). Use them to undo a mistake.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (snapshots.isEmpty()) item { Text("None yet — the first is made within a day of adding a Harambee.", style = MaterialTheme.typography.bodySmall) }
            items(snapshots, key = { it.file.name }) { snap ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(snap.label)
                        Text(Formats.dateTime(snap.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { report { pending = backups.read(snap); "" } }) { Text("Restore") }
                }
                HorizontalDivider()
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { confirmDelete = true }) { Text("Delete all Harambees and records", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "From ${Formats.dateTime(backup.createdAt)}: ${backup.data.campaigns.size} Harambees, " +
                        "${backup.data.contributions.size} records, ${backup.data.members.size} members.\n\n" +
                        "Everything currently in the app is replaced. A copy of the current data is saved first, so this can be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    report { backups.restore(backup); "Restored ✅" }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All Harambees, contributions, pledges and members are removed. An automatic copy is kept on this phone so you can restore it.")
                    OutlinedTextField(typed, { typed = it }, label = { Text("Type DELETE to confirm") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim().equals("DELETE", ignoreCase = true), onClick = {
                    confirmDelete = false
                    report { backups.deleteEverything(); "All records deleted. Restore the copy \"Before deleting everything\" to undo." }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------- Activity history ----------

@Composable
fun ActivityScreen(campaignId: Long?, onBack: () -> Unit) {
    val repository = appContainer().repository
    val flow = remember(campaignId) { campaignId?.let { repository.activity(it) } ?: repository.allActivity }
    val entries by flow.collectAsStateWithLifecycle(emptyList())
    Scaffold(topBar = { BackTopBar("Activity history", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (entries.isEmpty()) item { Text("Nothing yet.", Modifier.padding(16.dp)) }
            val byDay = entries.groupBy { Formats.date(it.at) }
            byDay.forEach { (day, list) ->
                item(key = "day-$day") {
                    Text(day, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                }
                items(list, key = { it.id }) { ActivityRow(it) }
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
        Text(Formats.time(entry.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
        Column {
            Text(entry.action, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(entry.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

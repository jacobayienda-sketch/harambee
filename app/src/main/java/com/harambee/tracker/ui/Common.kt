package com.harambee.tracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.harambee.tracker.AppContainer
import com.harambee.tracker.container
import com.harambee.tracker.core.MpesaParser
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun appContainer(): AppContainer = LocalContext.current.container

object Formats {
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US).withZone(MpesaParser.NAIROBI)
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).withZone(MpesaParser.NAIROBI)

    fun dateTime(ms: Long): String = dateTime.format(Instant.ofEpochMilli(ms))
    fun date(ms: Long): String = date.format(Instant.ofEpochMilli(ms))
    fun startOfToday(): Long = LocalDate.now(MpesaParser.NAIROBI).atStartOfDay(MpesaParser.NAIROBI).toInstant().toEpochMilli()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = actions,
    )
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

object Sharing {
    private val whatsAppPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Opens WhatsApp's chat picker with the text ready to send; falls back to the share sheet. */
    fun toWhatsApp(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val installed = whatsAppPackages.firstOrNull { isInstalled(context, it) }
        if (installed != null) {
            try {
                context.startActivity(Intent(intent).setPackage(installed))
                return
            } catch (_: Exception) {
                // Fall through to the chooser.
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share update"))
    }

    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Harambee update", text))
        Toast.makeText(context, "Copied — paste it in the WhatsApp group", Toast.LENGTH_SHORT).show()
    }

    fun shareCsv(context: Context, fileName: String, csv: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Export contributions"))
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

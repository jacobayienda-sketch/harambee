package com.harambee.tracker

import android.content.Context
import android.net.Uri
import com.harambee.tracker.data.BackupCodec
import com.harambee.tracker.data.BackupFile
import com.harambee.tracker.data.HarambeeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Keeps the records safe beyond the SMS inbox:
 * - backup files the treasurer saves to Google Drive, WhatsApp or another phone;
 * - automatic daily snapshots inside the app (last 7 kept), which Android's own backup also copies;
 * - a snapshot before every restore or "delete everything", so those can be undone.
 */
class BackupManager(
    private val context: Context,
    private val repository: HarambeeRepository,
    private val settings: Settings,
) {
    private val dir get() = File(context.filesDir, "backups").apply { mkdirs() }
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.US).withZone(ZoneId.of("Africa/Nairobi"))

    data class Snapshot(val file: File, val createdAt: Long, val label: String)

    suspend fun build(): BackupFile = BackupFile(
        createdAt = System.currentTimeMillis(),
        appVersion = BuildConfig.VERSION_NAME,
        settings = settings.export(),
        data = repository.snapshot(),
    )

    fun suggestedFileName(): String = "harambee-backup-${stamp.format(Instant.now())}.json"

    /** Writes a backup to a file the user picked (Drive, Downloads, SD card…). */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = BackupCodec.encode(build())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Could not write to the chosen file")
        settings.lastBackupAt.set(System.currentTimeMillis())
    }

    /** A backup file in the app's cache, for sharing via WhatsApp / email. */
    suspend fun exportForSharing(): File = withContext(Dispatchers.IO) {
        val out = File(File(context.cacheDir, "exports").apply { mkdirs() }, suggestedFileName())
        out.writeText(BackupCodec.encode(build()))
        settings.lastBackupAt.set(System.currentTimeMillis())
        out
    }

    suspend fun read(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Could not open the file")
        BackupCodec.decode(text)
    }

    suspend fun read(snapshot: Snapshot): BackupFile = withContext(Dispatchers.IO) { BackupCodec.decode(snapshot.file.readText()) }

    /** Replaces everything with the backup; the current data is saved as a snapshot first. */
    suspend fun restore(backup: BackupFile) {
        writeSnapshot("before-restore")
        repository.restore(backup.data)
        settings.import(backup.settings)
    }

    suspend fun deleteEverything() {
        writeSnapshot("before-delete")
        repository.deleteEverything()
    }

    suspend fun snapshotIfDue() {
        val now = System.currentTimeMillis()
        if (now - settings.lastSnapshotAt.value.value < 24 * 3600_000L) return
        val data = repository.snapshot()
        if (data.campaigns.isEmpty() && data.contributions.isEmpty()) return
        writeSnapshot("daily")
        settings.lastSnapshotAt.set(now)
    }

    suspend fun snapshots(): List<Snapshot> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .map { Snapshot(it, it.lastModified(), labelFor(it.name)) }
            .sortedByDescending { it.createdAt }
    }

    private suspend fun writeSnapshot(kind: String) = withContext(Dispatchers.IO) {
        val file = File(dir, "$kind-${stamp.format(Instant.now())}.json")
        file.writeText(BackupCodec.encode(build()))
        // Keep the newest 7 of each kind.
        dir.listFiles { f -> f.name.startsWith("$kind-") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(7)
            .forEach { it.delete() }
    }

    private fun labelFor(name: String) = when {
        name.startsWith("daily-") -> "Daily copy"
        name.startsWith("before-restore-") -> "Before a restore"
        name.startsWith("before-delete-") -> "Before deleting everything"
        else -> "Copy"
    }
}

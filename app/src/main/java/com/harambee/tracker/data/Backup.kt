package com.harambee.tracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the app knows, in a form that can be saved to a file and restored on any phone. */
@Serializable
data class BackupData(
    val campaigns: List<Campaign> = emptyList(),
    val contributions: List<Contribution> = emptyList(),
    val aliases: List<ContributorAlias> = emptyList(),
    val collectors: List<Collector> = emptyList(),
    val members: List<Member> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
)

@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val format: Int = FORMAT,
    val createdAt: Long,
    val appVersion: String,
    val settings: Map<String, String> = emptyMap(),
    val data: BackupData,
) {
    companion object {
        const val APP_ID = "harambee-tracker"
        const val FORMAT = 1
    }
}

object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Reads a backup, refusing files from other apps or newer, unknown formats. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This is not a Harambee Tracker backup file.", e)
        }
        require(file.app == BackupFile.APP_ID) { "This is not a Harambee Tracker backup file." }
        require(file.format <= BackupFile.FORMAT) { "This backup was made by a newer version of the app. Update the app first." }
        val campaignIds = file.data.campaigns.map { it.id }.toSet()
        require(file.data.contributions.all { it.campaignId == null || it.campaignId in campaignIds }) { "The backup file is damaged (records point to missing Harambees)." }
        require(file.data.collectors.all { it.campaignId in campaignIds }) { "The backup file is damaged (collectors point to missing Harambees)." }
        val codes = file.data.contributions.mapNotNull { it.mpesaCode }
        require(codes.size == codes.toSet().size) { "The backup file is damaged (duplicate M-Pesa codes)." }
        return file
    }
}

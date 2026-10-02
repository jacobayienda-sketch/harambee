package com.harambee.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "campaigns")
data class Campaign(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Appeal text shown at the top of every WhatsApp update. */
    val intro: String = "",
    val targetCents: Long? = null,
    /** M-Pesa messages received before this moment are not counted. */
    val startAt: Long,
    val endAt: Long? = null,
    /** Collecting now: new M-Pesa receipts are added automatically. */
    val isActive: Boolean = true,
    val payToName: String = "",
    val payToNumber: String = "",
    val footer: String = "Thanks for your generous contribution 🙏",
    val createdAt: Long = System.currentTimeMillis(),
    /** Members group whose contributions are tracked (welfare / church groups), or null. */
    val memberGroup: String? = null,
    /** Amount each member is expected to give, e.g. 1,000 per bereavement. */
    val expectedCents: Long? = null,
    /** How contributor names appear in anything shared (see [com.harambee.tracker.core.NameDisplay]). */
    @ColumnInfo(defaultValue = "FULL") val nameDisplay: String = "FULL",
    /** Show each person's amount in shared lists (totals are always shown). */
    @ColumnInfo(defaultValue = "1") val showAmounts: Boolean = true,
    /** When an update was last shared; "new since last update" counts from here. */
    val lastSharedAt: Long? = null,
)

/** Another committee member whose number also receives contributions. */
@Serializable
@Entity(
    tableName = "collectors",
    indices = [Index("campaignId")],
    foreignKeys = [ForeignKey(entity = Campaign::class, parentColumns = ["id"], childColumns = ["campaignId"], onDelete = ForeignKey.CASCADE)],
)
data class Collector(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val name: String,
    val phone: String = "",
)

/** A member of a welfare / church / workplace group, reused across Harambees. */
@Serializable
@Entity(tableName = "members", indices = [Index("groupName")])
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupName: String,
    val name: String,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object Status {
    /** Just arrived from M-Pesa; waiting for the treasurer to decide. */
    const val PENDING = "PENDING"
    /** Money received and counted in the total (✅). */
    const val COUNTED = "COUNTED"
    /** Promised but not yet received (no ✅). */
    const val PLEDGED = "PLEDGED"
    /** Received on the number but not part of this Harambee (e.g. personal money). */
    const val EXCLUDED = "EXCLUDED"
    /** Safaricom reversed the transaction. */
    const val REVERSED = "REVERSED"
}

object Source {
    const val MPESA_SMS = "MPESA_SMS"
    const val MPESA_MANUAL = "MPESA_MANUAL"
    const val CASH = "CASH"
    const val BANK = "BANK"
    const val WHATSAPP_LIST = "WHATSAPP_LIST"
    const val OTHER = "OTHER"

    fun label(source: String): String = when (source) {
        MPESA_SMS -> "M-Pesa (SMS)"
        MPESA_MANUAL -> "M-Pesa (entered)"
        CASH -> "Cash"
        BANK -> "Bank"
        WHATSAPP_LIST -> "WhatsApp list"
        else -> "Other"
    }
}

@Serializable
@Entity(
    tableName = "contributions",
    indices = [Index(value = ["mpesaCode"], unique = true), Index("campaignId"), Index("contributorKey")],
    foreignKeys = [
        ForeignKey(entity = Campaign::class, parentColumns = ["id"], childColumns = ["campaignId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class Contribution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** For PENDING rows this is only the suggested Harambee. */
    val campaignId: Long?,
    /** The M-Pesa transaction code; unique, which is what makes duplicates impossible. */
    val mpesaCode: String? = null,
    val amountCents: Long,
    /** Name as M-Pesa reported it, or as typed. */
    val senderName: String,
    val senderPhone: String? = null,
    /** Name to show on the list ("CO Peter chesos"); overrides senderName when set. */
    val listName: String? = null,
    val contributorKey: String,
    val receivedAt: Long,
    val source: String,
    val status: String = Status.COUNTED,
    val note: String = "",
    val rawMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Who received the money; null = this phone's number. */
    val collectorId: Long? = null,
    /** The contributor asked not to be named in shared lists. */
    @ColumnInfo(defaultValue = "0") val anonymous: Boolean = false,
    /** When it was counted (✅); used for "new since last update". */
    val countedAt: Long? = null,
)

/** A preferred display name for everyone who pays from the same number. */
@Serializable
@Entity(tableName = "contributor_aliases")
data class ContributorAlias(
    @PrimaryKey val contributorKey: String,
    val displayName: String,
)

data class ContributionRow(
    @Embedded val contribution: Contribution,
    @ColumnInfo(name = "alias") val alias: String?,
) {
    val displayName: String get() = contribution.listName?.takeIf { it.isNotBlank() } ?: alias ?: contribution.senderName
}

data class CampaignSummary(
    @Embedded val campaign: Campaign,
    val totalCents: Long,
    val pledgedCents: Long,
    val paidCount: Int,
    val todayCents: Long,
    val todayCount: Int,
    val lastPaymentAt: Long?,
)

/** Every change, so the committee can see who was added, edited or removed and when. Kept even after deletions. */
@Serializable
@Entity(tableName = "activity_log", indices = [Index("campaignId")])
data class ActivityEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long = System.currentTimeMillis(),
    val campaignId: Long?,
    val contributionId: Long?,
    val action: String,
    val detail: String,
)

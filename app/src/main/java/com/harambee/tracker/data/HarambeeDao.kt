package com.harambee.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HarambeeDao {
    // Campaigns

    @Query(
        """
        SELECT c.*,
            COALESCE(SUM(CASE WHEN k.status = 'COUNTED' THEN k.amountCents END), 0) AS totalCents,
            COALESCE(SUM(CASE WHEN k.status = 'PLEDGED' THEN k.amountCents END), 0) AS pledgedCents,
            COUNT(CASE WHEN k.status = 'COUNTED' THEN 1 END) AS paidCount
        FROM campaigns c LEFT JOIN contributions k ON k.campaignId = c.id
        GROUP BY c.id
        ORDER BY c.isActive DESC, c.createdAt DESC
        """,
    )
    fun observeCampaignSummaries(): Flow<List<CampaignSummary>>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    fun observeCampaign(id: Long): Flow<Campaign?>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun getCampaign(id: Long): Campaign?

    @Query("SELECT * FROM campaigns ORDER BY isActive DESC, createdAt DESC")
    fun observeCampaigns(): Flow<List<Campaign>>

    /** Active Harambees whose collection window covers [time], newest first. */
    @Query("SELECT * FROM campaigns WHERE isActive = 1 AND startAt <= :time AND (endAt IS NULL OR endAt >= :time) ORDER BY createdAt DESC")
    suspend fun activeCampaignsAt(time: Long): List<Campaign>

    @Query("SELECT MIN(startAt) FROM campaigns WHERE isActive = 1")
    suspend fun earliestActiveStart(): Long?

    @Insert
    suspend fun insertCampaign(campaign: Campaign): Long

    @Update
    suspend fun updateCampaign(campaign: Campaign)

    @Delete
    suspend fun deleteCampaign(campaign: Campaign)

    @Query("DELETE FROM contributions WHERE campaignId = :campaignId AND mpesaCode IS NULL")
    suspend fun deleteManualContributions(campaignId: Long)

    /** M-Pesa rows are kept (excluded) when their Harambee is deleted so the code still blocks duplicates. */
    @Query("UPDATE contributions SET status = 'EXCLUDED', campaignId = NULL WHERE campaignId = :campaignId")
    suspend fun detachContributions(campaignId: Long)

    // Contributions

    @Query(
        """
        SELECT k.*, a.displayName AS alias FROM contributions k
        LEFT JOIN contributor_aliases a ON a.contributorKey = k.contributorKey
        WHERE k.campaignId = :campaignId ORDER BY k.receivedAt ASC, k.id ASC
        """,
    )
    fun observeContributions(campaignId: Long): Flow<List<ContributionRow>>

    @Query(
        """
        SELECT k.*, a.displayName AS alias FROM contributions k
        LEFT JOIN contributor_aliases a ON a.contributorKey = k.contributorKey
        WHERE k.status = 'PENDING' ORDER BY k.receivedAt DESC
        """,
    )
    fun observePending(): Flow<List<ContributionRow>>

    @Query("SELECT COUNT(*) FROM contributions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM contributions WHERE id = :id")
    suspend fun getContribution(id: Long): Contribution?

    @Query(
        """
        SELECT k.*, a.displayName AS alias FROM contributions k
        LEFT JOIN contributor_aliases a ON a.contributorKey = k.contributorKey
        WHERE k.id = :id
        """,
    )
    fun observeContribution(id: Long): Flow<ContributionRow?>

    @Query("SELECT * FROM contributions WHERE campaignId = :campaignId")
    suspend fun contributionsFor(campaignId: Long): List<Contribution>

    @Query("SELECT * FROM contributions WHERE mpesaCode = :code")
    suspend fun findByCode(code: String): Contribution?

    /** Returns -1 when the M-Pesa code is already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContribution(contribution: Contribution): Long

    @Update
    suspend fun updateContribution(contribution: Contribution)

    @Delete
    suspend fun deleteContribution(contribution: Contribution)

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM contributions WHERE campaignId = :campaignId AND status = 'COUNTED'")
    suspend fun totalFor(campaignId: Long): Long

    // Aliases

    @Upsert
    suspend fun upsertAlias(alias: ContributorAlias)

    @Query("DELETE FROM contributor_aliases WHERE contributorKey = :key")
    suspend fun deleteAlias(key: String)

    // Collectors

    @Query("SELECT * FROM collectors WHERE campaignId = :campaignId ORDER BY id")
    fun observeCollectors(campaignId: Long): Flow<List<Collector>>

    @Insert
    suspend fun insertCollector(collector: Collector): Long

    @Delete
    suspend fun deleteCollector(collector: Collector)

    @Query("UPDATE contributions SET collectorId = NULL WHERE collectorId = :collectorId")
    suspend fun clearCollector(collectorId: Long)

    // Members

    @Query("SELECT DISTINCT groupName FROM members ORDER BY groupName")
    fun observeGroups(): Flow<List<String>>

    @Query("SELECT * FROM members WHERE groupName = :group ORDER BY name COLLATE NOCASE")
    fun observeMembers(group: String): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE groupName = :group")
    suspend fun membersOf(group: String): List<Member>

    @Insert
    suspend fun insertMembers(members: List<Member>)

    @Update
    suspend fun updateMember(member: Member)

    @Delete
    suspend fun deleteMember(member: Member)
}

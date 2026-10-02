package com.harambee.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Campaign::class, Contribution::class, ContributorAlias::class, Collector::class, Member::class, ActivityEntry::class],
    version = 3,
    exportSchema = true,
)
abstract class HarambeeDatabase : RoomDatabase() {
    abstract fun dao(): HarambeeDao

    companion object {
        fun create(context: Context): HarambeeDatabase =
            Room.databaseBuilder(context, HarambeeDatabase::class.java, "harambee.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** Adds collectors, members groups and the fields that link to them; existing records are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `campaigns` ADD COLUMN `memberGroup` TEXT")
                db.execSQL("ALTER TABLE `campaigns` ADD COLUMN `expectedCents` INTEGER")
                db.execSQL("ALTER TABLE `contributions` ADD COLUMN `collectorId` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collectors` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `campaignId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `phone` TEXT NOT NULL, FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collectors_campaignId` ON `collectors` (`campaignId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupName` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `phone` TEXT, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_groupName` ON `members` (`groupName`)")
            }
        }

        /** Adds the activity log. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, " +
                        "`campaignId` INTEGER, `contributionId` INTEGER, `action` TEXT NOT NULL, `detail` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_log_campaignId` ON `activity_log` (`campaignId`)")
            }
        }
    }
}

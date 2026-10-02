package com.harambee.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Campaign::class, Contribution::class, ContributorAlias::class],
    version = 1,
    exportSchema = false,
)
abstract class HarambeeDatabase : RoomDatabase() {
    abstract fun dao(): HarambeeDao

    companion object {
        fun create(context: Context): HarambeeDatabase =
            Room.databaseBuilder(context, HarambeeDatabase::class.java, "harambee.db").build()
    }
}

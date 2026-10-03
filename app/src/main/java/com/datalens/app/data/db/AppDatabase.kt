package com.datalens.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PinnedAppEntity::class,
        HiddenAppEntity::class,
        LimitConfigEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appCustomizationDao(): AppCustomizationDao
    abstract fun limitConfigDao(): LimitConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2: unlimited-plan flag + the two usage-threshold alerts. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE limit_config ADD COLUMN isUnlimited INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE limit_config ADD COLUMN dailyAlertThresholdBytes INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE limit_config ADD COLUMN perAppAlertThresholdBytes INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "datalens.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

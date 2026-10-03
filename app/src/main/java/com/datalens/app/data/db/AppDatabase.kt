package com.datalens.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PinnedAppEntity::class,
        HiddenAppEntity::class,
        LimitConfigEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appCustomizationDao(): AppCustomizationDao
    abstract fun limitConfigDao(): LimitConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "datalens.db",
                ).build().also { INSTANCE = it }
            }
    }
}

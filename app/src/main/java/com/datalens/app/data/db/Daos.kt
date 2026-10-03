package com.datalens.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppCustomizationDao {

    @Query("SELECT packageName FROM pinned_apps ORDER BY pinnedAt ASC")
    fun pinnedPackages(): Flow<List<String>>

    @Query("SELECT packageName FROM hidden_apps ORDER BY hiddenAt ASC")
    fun hiddenPackages(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pin(entity: PinnedAppEntity)

    @Query("DELETE FROM pinned_apps WHERE packageName = :packageName")
    suspend fun unpin(packageName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entity: HiddenAppEntity)

    @Query("DELETE FROM hidden_apps WHERE packageName = :packageName")
    suspend fun unhide(packageName: String)
}

@Dao
interface LimitConfigDao {

    @Query("SELECT * FROM limit_config WHERE id = 0")
    fun config(): Flow<LimitConfigEntity?>

    @Upsert
    suspend fun save(entity: LimitConfigEntity)
}

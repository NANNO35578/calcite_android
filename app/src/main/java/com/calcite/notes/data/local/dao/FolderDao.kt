package com.calcite.notes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.calcite.notes.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY name")
    fun getAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name")
    suspend fun getAllSync(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY name")
    fun getByParentId(parentId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :folderId LIMIT 1")
    suspend fun getByIdSync(folderId: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<FolderEntity>)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteById(folderId: Long)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

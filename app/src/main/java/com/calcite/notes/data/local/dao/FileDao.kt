package com.calcite.notes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.calcite.notes.data.local.entity.FileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Query("SELECT * FROM files ORDER BY createdAt DESC")
    fun getAll(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :fileId LIMIT 1")
    suspend fun getByIdSync(fileId: Long): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Update
    suspend fun update(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :fileId")
    suspend fun deleteById(fileId: Long)

    @Query("DELETE FROM files")
    suspend fun deleteAll()
}

package com.mdaopay.app.core.datastore

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TxQueueDao {
    @Query("SELECT * FROM tx_queue ORDER BY created_at ASC")
    fun getAllFlow(): Flow<List<TxQueueEntity>>

    @Query("SELECT * FROM tx_queue ORDER BY created_at ASC")
    suspend fun getAll(): List<TxQueueEntity>

    @Query("SELECT COUNT(*) FROM tx_queue")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TxQueueEntity)

    @Query("DELETE FROM tx_queue WHERE idempotency_key = :idempotencyKey")
    suspend fun deleteById(idempotencyKey: String)

    @Query("UPDATE tx_queue SET retry_count = retry_count + 1, last_error = :error WHERE idempotency_key = :idempotencyKey")
    suspend fun incrementRetry(idempotencyKey: String, error: String)

    @Query("DELETE FROM tx_queue")
    suspend fun clearAll()
}

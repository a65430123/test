package com.twstock.ta.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StockInfoEntity>)

    @Query("SELECT COUNT(*) FROM stock_info")
    suspend fun count(): Int

    @Query("SELECT * FROM stock_info WHERE code = :code")
    suspend fun get(code: String): StockInfoEntity?

    @Query(
        """
        SELECT * FROM stock_info
        WHERE code LIKE :query || '%' OR name LIKE '%' || :query || '%'
        ORDER BY CASE WHEN code = :query THEN 0 WHEN code LIKE :query || '%' THEN 1 ELSE 2 END, code
        LIMIT 50
        """
    )
    fun search(query: String): Flow<List<StockInfoEntity>>
}

@Dao
interface WatchlistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE code = :code")
    suspend fun delete(code: String)

    @Query("SELECT * FROM watchlist ORDER BY addedAt")
    suspend fun getAll(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlist WHERE code = :code")
    suspend fun get(code: String): WatchlistEntity?

    @Query("UPDATE watchlist SET lastNotifiedEpochDay = :epochDay WHERE code = :code")
    suspend fun setLastNotified(code: String, epochDay: Long)

    @Query(
        """
        SELECT w.code AS code, w.name AS name, w.market AS market,
               w.lastNotifiedEpochDay AS lastNotifiedEpochDay,
               (SELECT c.epochDay FROM candles c WHERE c.code = w.code ORDER BY c.epochDay DESC LIMIT 1) AS lastEpochDay,
               (SELECT c.close FROM candles c WHERE c.code = w.code ORDER BY c.epochDay DESC LIMIT 1) AS lastClose,
               (SELECT c.close FROM candles c WHERE c.code = w.code ORDER BY c.epochDay DESC LIMIT 1 OFFSET 1) AS prevClose
        FROM watchlist w
        ORDER BY w.addedAt
        """
    )
    fun observeRows(): Flow<List<WatchlistRow>>
}

@Dao
interface CandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CandleEntity>)

    @Query("SELECT * FROM candles WHERE code = :code ORDER BY epochDay")
    suspend fun getAll(code: String): List<CandleEntity>

    @Query("SELECT * FROM candles WHERE code = :code ORDER BY epochDay")
    fun observe(code: String): Flow<List<CandleEntity>>

    @Query("SELECT MAX(epochDay) FROM candles WHERE code = :code")
    suspend fun latestEpochDay(code: String): Long?

    @Query("DELETE FROM candles WHERE code = :code")
    suspend fun deleteFor(code: String)
}

@Dao
interface ExRightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ExRightEntity>)

    @Query("SELECT * FROM ex_rights WHERE code = :code ORDER BY epochDay")
    suspend fun getFor(code: String): List<ExRightEntity>

    @Query("SELECT * FROM ex_rights WHERE code = :code ORDER BY epochDay")
    fun observeFor(code: String): Flow<List<ExRightEntity>>

    /** 資料表變動通知（同步完成後觸發重新計算）。 */
    @Query("SELECT COUNT(*) FROM ex_rights")
    fun observeCount(): Flow<Int>

    @Query("SELECT MAX(epochDay) FROM ex_rights WHERE market = :market")
    suspend fun latestEpochDay(market: Market): Long?
}

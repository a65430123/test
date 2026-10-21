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

    /** 最近 n 個有 K 棒資料的交易日（新到舊）。 */
    @Query("SELECT DISTINCT epochDay FROM candles ORDER BY epochDay DESC LIMIT :n")
    suspend fun recentTradingDays(n: Int): List<Long>
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

@Dao
interface ChipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstitutional(items: List<InstitutionalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMargin(items: List<MarginEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markSynced(item: ChipSyncEntity)

    @Query("SELECT epochDay FROM chip_sync WHERE market = :market AND dataset = :dataset")
    suspend fun syncedDays(market: Market, dataset: String): List<Long>

    @Query("SELECT * FROM institutional WHERE code = :code ORDER BY epochDay")
    suspend fun getInstitutional(code: String): List<InstitutionalEntity>

    @Query("SELECT * FROM margin WHERE code = :code ORDER BY epochDay")
    suspend fun getMargin(code: String): List<MarginEntity>

    @Query("SELECT * FROM institutional WHERE code = :code ORDER BY epochDay")
    fun observeInstitutional(code: String): Flow<List<InstitutionalEntity>>

    @Query("SELECT * FROM margin WHERE code = :code ORDER BY epochDay")
    fun observeMargin(code: String): Flow<List<MarginEntity>>

    /** 資料表變動通知（同步完成後觸發重新計算）。 */
    @Query("SELECT COUNT(*) FROM chip_sync")
    fun observeSyncCount(): Flow<Int>

    @Query("DELETE FROM institutional WHERE epochDay < :epochDay")
    suspend fun pruneInstitutional(epochDay: Long)

    @Query("DELETE FROM margin WHERE epochDay < :epochDay")
    suspend fun pruneMargin(epochDay: Long)

    @Query("DELETE FROM chip_sync WHERE epochDay < :epochDay")
    suspend fun pruneSync(epochDay: Long)
}

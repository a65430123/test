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

    @Query("SELECT * FROM stock_info")
    suspend fun getAll(): List<StockInfoEntity>

    @Query(
        """
        SELECT * FROM stock_info
        WHERE code LIKE :query || '%' OR name LIKE '%' || :query || '%'
        ORDER BY CASE
            WHEN code = :query THEN 0
            WHEN code LIKE :query || '%' THEN 1
            WHEN name LIKE :query || '%' THEN 2
            ELSE 3 END, length(code), code
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

    @Query("SELECT MIN(epochDay) FROM candles WHERE code = :code")
    suspend fun earliestEpochDay(code: String): Long?

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

    @Query("SELECT * FROM ex_rights")
    suspend fun getAll(): List<ExRightEntity>

    @Query("SELECT MAX(epochDay) FROM ex_rights WHERE market = :market")
    suspend fun latestEpochDay(market: Market): Long?
}

@Dao
interface MarketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandles(items: List<MarketCandleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(item: MarketDayEntity)

    @Query("SELECT * FROM market_days WHERE market = :market")
    suspend fun days(market: Market): List<MarketDayEntity>

    @Query("SELECT * FROM market_candles ORDER BY code, epochDay")
    suspend fun allCandles(): List<MarketCandleEntity>

    @Query("SELECT * FROM market_candles WHERE code = :code ORDER BY epochDay")
    suspend fun candlesFor(code: String): List<MarketCandleEntity>

    /** 指定股票自某日起的全市場日 K（搜尋結果顯示最新收盤用）。 */
    @Query("SELECT * FROM market_candles WHERE code IN (:codes) AND epochDay >= :since ORDER BY code, epochDay")
    suspend fun candlesSince(codes: List<String>, since: Long): List<MarketCandleEntity>

    @Query("SELECT MAX(epochDay) FROM market_days WHERE hasData = 1")
    suspend fun latestTradingDay(): Long?

    /** 資料變動通知（同步進度、完成後重新掃描）。 */
    @Query("SELECT COUNT(*) FROM market_days")
    fun observeDayCount(): Flow<Int>

    @Query("DELETE FROM market_candles WHERE epochDay < :epochDay")
    suspend fun pruneCandles(epochDay: Long)

    @Query("DELETE FROM market_days WHERE epochDay < :epochDay")
    suspend fun pruneDays(epochDay: Long)
}

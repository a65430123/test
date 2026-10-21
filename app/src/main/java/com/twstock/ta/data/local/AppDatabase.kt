package com.twstock.ta.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun marketToString(m: Market): String = m.name

    @TypeConverter
    fun stringToMarket(s: String): Market = Market.valueOf(s)
}

@Database(
    entities = [
        StockInfoEntity::class, WatchlistEntity::class, CandleEntity::class, ExRightEntity::class,
        MarketCandleEntity::class, MarketDayEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockInfoDao(): StockInfoDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun candleDao(): CandleDao
    abstract fun exRightDao(): ExRightDao
    abstract fun marketDao(): MarketDao

    companion object {
        /** v2：新增除權息資料表 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ex_rights` (`code` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`market` TEXT NOT NULL, `closeBefore` REAL NOT NULL, `referencePrice` REAL NOT NULL, " +
                        "PRIMARY KEY(`code`, `epochDay`))"
                )
            }
        }

        /** v3：新增選股用的全市場日 K 與市場日期表 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `market_candles` (`code` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`open` REAL NOT NULL, `high` REAL NOT NULL, `low` REAL NOT NULL, `close` REAL NOT NULL, " +
                        "`volume` INTEGER NOT NULL, PRIMARY KEY(`code`, `epochDay`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `market_days` (`market` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`hasData` INTEGER NOT NULL, PRIMARY KEY(`market`, `epochDay`))"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "twstock.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}

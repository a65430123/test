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
        InstitutionalEntity::class, MarginEntity::class, ChipSyncEntity::class,
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
    abstract fun chipDao(): ChipDao

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

        /** v3：新增籌碼資料表（三大法人、融資融券、同步紀錄） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `institutional` (`code` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`foreignNet` INTEGER NOT NULL, `trustNet` INTEGER NOT NULL, `dealerNet` INTEGER NOT NULL, " +
                        "`totalNet` INTEGER NOT NULL, PRIMARY KEY(`code`, `epochDay`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `margin` (`code` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`marginBalance` INTEGER, `marginChange` INTEGER, `shortBalance` INTEGER, `shortChange` INTEGER, " +
                        "PRIMARY KEY(`code`, `epochDay`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chip_sync` (`market` TEXT NOT NULL, `dataset` TEXT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, PRIMARY KEY(`market`, `dataset`, `epochDay`))"
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

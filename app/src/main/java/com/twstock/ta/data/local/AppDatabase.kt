package com.twstock.ta.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun marketToString(m: Market): String = m.name

    @TypeConverter
    fun stringToMarket(s: String): Market = Market.valueOf(s)
}

@Database(
    entities = [StockInfoEntity::class, WatchlistEntity::class, CandleEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockInfoDao(): StockInfoDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun candleDao(): CandleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "twstock.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

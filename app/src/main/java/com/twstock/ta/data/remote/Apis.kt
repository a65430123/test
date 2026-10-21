package com.twstock.ta.data.remote

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface TwseApi {
    /** @param date yyyyMMdd，取該日所在月份 */
    @GET("rwd/zh/afterTrading/STOCK_DAY")
    suspend fun stockDay(
        @Query("date") date: String,
        @Query("stockNo") stockNo: String,
        @Query("response") response: String = "json",
    ): TwseTableResponse

    /** 上市每日收盤行情（全部，不含權證、牛熊證），日期格式 yyyyMMdd */
    @GET("rwd/zh/afterTrading/MI_INDEX")
    suspend fun dailyQuotes(
        @Query("date") date: String,
        @Query("type") type: String = "ALLBUT0999",
        @Query("response") response: String = "json",
    ): TwseTableResponse

    /** 上市除權除息計算結果表（全市場），日期格式 yyyyMMdd */
    @GET("rwd/zh/exRight/TWT49U")
    suspend fun exRights(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("response") response: String = "json",
    ): TwseTableResponse

    companion object {
        const val BASE_URL = "https://www.twse.com.tw/"
    }
}

interface TpexApi {
    /** @param date yyyy/MM/dd（西元），取該日所在月份 */
    @GET("www/zh-tw/afterTrading/tradingStock")
    suspend fun tradingStock(
        @Query("code") code: String,
        @Query("date") date: String,
        @Query("response") response: String = "json",
    ): TpexTableResponse

    /** 上櫃股票每日收盤行情（全部），日期格式 yyyy/MM/dd */
    @GET("www/zh-tw/afterTrading/otc")
    suspend fun dailyQuotes(
        @Query("date") date: String,
        @Query("type") type: String = "EW",
        @Query("response") response: String = "json",
    ): TpexTableResponse

    /** 上櫃除權除息計算結果表（全市場），日期格式 yyyy/MM/dd */
    @GET("www/zh-tw/bulletin/exDailyQ")
    suspend fun exRights(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("response") response: String = "json",
    ): TpexTableResponse

    companion object {
        const val BASE_URL = "https://www.tpex.org.tw/"
    }
}

interface TwseOpenApi {
    /** 上市個股日成交資訊（用於建立股票代號清單） */
    @GET("v1/exchangeReport/STOCK_DAY_ALL")
    suspend fun stockDayAll(): List<JsonObject>

    companion object {
        const val BASE_URL = "https://openapi.twse.com.tw/"
    }
}

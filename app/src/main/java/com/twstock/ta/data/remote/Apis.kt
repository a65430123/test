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
    ): TwseStockDayResponse

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
    ): TpexTradingStockResponse

    /** 上櫃股票每日收盤行情（用於建立股票代號清單） */
    @GET("openapi/v1/tpex_mainboard_daily_close_quotes")
    suspend fun mainboardQuotes(): List<JsonObject>

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

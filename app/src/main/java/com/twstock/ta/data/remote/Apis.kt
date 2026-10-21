package com.twstock.ta.data.remote

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TwseApi {
    /** @param date yyyyMMdd，取該日所在月份 */
    @GET("rwd/zh/afterTrading/STOCK_DAY")
    suspend fun stockDay(
        @Query("date") date: String,
        @Query("stockNo") stockNo: String,
        @Query("response") response: String = "json",
    ): TwseTableResponse

    /** 上市三大法人買賣超日報（全市場，單位：股），日期格式 yyyyMMdd */
    @GET("rwd/zh/fund/T86")
    suspend fun institutional(
        @Query("date") date: String,
        @Query("selectType") selectType: String = "ALLBUT0999",
        @Query("response") response: String = "json",
    ): TwseTableResponse

    /** 上市融資融券餘額（全市場，單位：張），日期格式 yyyyMMdd */
    @GET("rwd/zh/marginTrading/MI_MARGN")
    suspend fun margin(
        @Query("date") date: String,
        @Query("selectType") selectType: String = "ALL",
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

    /** 上櫃三大法人買賣明細（全市場，單位：股），日期格式 yyyy/MM/dd */
    @GET("www/zh-tw/insti/dailyTrade")
    suspend fun institutional(
        @Query("date") date: String,
        @Query("type") type: String = "Daily",
        @Query("sect") sect: String = "EW",
        @Query("response") response: String = "json",
    ): TpexTableResponse

    /** 上櫃融資融券餘額（全市場，單位：張），日期格式 yyyy/MM/dd */
    @GET("www/zh-tw/margin/balance")
    suspend fun margin(
        @Query("date") date: String,
        @Query("response") response: String = "json",
    ): TpexTableResponse

    /** 上櫃除權除息計算結果表（全市場），日期格式 yyyy/MM/dd */
    @GET("www/zh-tw/bulletin/exDailyQ")
    suspend fun exRights(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("response") response: String = "json",
    ): TpexTableResponse

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

/** FinMind 開放資料 API；分點券商資料需使用者自行申請 token。 */
interface FinMindApi {
    /** 個股分點券商買賣日報（每家券商每個價位一列，單位：股） */
    @GET("api/v4/data")
    suspend fun data(
        @Header("Authorization") authorization: String,
        @Query("dataset") dataset: String,
        @Query("data_id") dataId: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
    ): FinMindResponse

    companion object {
        const val BASE_URL = "https://api.finmindtrade.com/"
        const val BROKER_DATASET = "TaiwanStockTradingDailyReport"
    }
}

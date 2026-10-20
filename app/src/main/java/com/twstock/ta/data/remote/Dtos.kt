package com.twstock.ta.data.remote

import com.google.gson.annotations.SerializedName

/**
 * 證交所 個股日成交資訊 (STOCK_DAY)，一次回傳一個月。
 * fields: 日期, 成交股數, 成交金額, 開盤價, 最高價, 最低價, 收盤價, 漲跌價差, 成交筆數
 */
data class TwseStockDayResponse(
    @SerializedName("stat") val stat: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("fields") val fields: List<String>?,
    @SerializedName("data") val data: List<List<String>>?,
)

/**
 * 櫃買中心 個股日成交資訊，一次回傳一個月。
 * fields: 日期, 成交張數, 成交仟元, 開盤, 最高, 最低, 收盤, 漲跌, 筆數
 */
data class TpexTradingStockResponse(
    @SerializedName("stat") val stat: String?,
    @SerializedName("tables") val tables: List<TpexTable>?,
    // 舊版 st43_result.php 格式
    @SerializedName("aaData") val aaData: List<List<String>>?,
)

data class TpexTable(
    @SerializedName("title") val title: String?,
    @SerializedName("fields") val fields: List<String>?,
    @SerializedName("data") val data: List<List<String>>?,
)

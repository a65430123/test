package com.twstock.ta.data.remote

import com.google.gson.annotations.SerializedName

/**
 * 證交所表格型回應（STOCK_DAY 個股日成交資訊、TWT49U 除權除息計算結果表共用）。
 * STOCK_DAY fields: 日期, 成交股數, 成交金額, 開盤價, 最高價, 最低價, 收盤價, 漲跌價差, 成交筆數
 */
data class TwseTableResponse(
    @SerializedName("stat") val stat: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("fields") val fields: List<String>?,
    @SerializedName("data") val data: List<List<String>>?,
    // rwd 版 MI_INDEX 等以多個表格回傳
    @SerializedName("tables") val tables: List<DataTable>?,
)

/**
 * 櫃買中心表格型回應（個股日成交資訊、除權除息計算結果表共用）。
 * 個股日成交 fields: 日期, 成交張數, 成交仟元, 開盤, 最高, 最低, 收盤, 漲跌, 筆數
 */
data class TpexTableResponse(
    @SerializedName("stat") val stat: String?,
    @SerializedName("tables") val tables: List<DataTable>?,
    // 舊版 st43_result.php 格式
    @SerializedName("aaData") val aaData: List<List<String>>?,
)

/** 回應中的一個表格（證交所 rwd 與櫃買中心共用）。 */
data class DataTable(
    @SerializedName("title") val title: String?,
    @SerializedName("fields") val fields: List<String>?,
    @SerializedName("data") val data: List<List<String>>?,
)

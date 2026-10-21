package com.twstock.ta

import android.content.Context
import com.twstock.ta.data.local.AppDatabase
import com.twstock.ta.data.remote.FinMindApi
import com.twstock.ta.data.remote.TpexApi
import com.twstock.ta.data.remote.TwseApi
import com.twstock.ta.data.remote.TwseOpenApi
import com.twstock.ta.data.repository.StockRepository
import com.twstock.ta.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** 簡易手動 DI。 */
class AppContainer(context: Context) {
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // 證交所/櫃買中心會擋掉沒有 User-Agent 的請求
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) TwStockTA/0.1")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val database: AppDatabase = AppDatabase.build(context)

    val stockRepository = StockRepository(
        db = database,
        twse = retrofit(TwseApi.BASE_URL).create(TwseApi::class.java),
        tpex = retrofit(TpexApi.BASE_URL).create(TpexApi::class.java),
        twseOpen = retrofit(TwseOpenApi.BASE_URL).create(TwseOpenApi::class.java),
        finMind = retrofit(FinMindApi.BASE_URL).create(FinMindApi::class.java),
    )

    val settingsRepository = SettingsRepository(context)
}

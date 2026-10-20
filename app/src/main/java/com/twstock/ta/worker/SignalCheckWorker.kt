package com.twstock.ta.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.twstock.core.TechnicalAnalyzer
import com.twstock.ta.TaStockApp
import com.twstock.ta.data.repository.StockRepository
import com.twstock.ta.notification.SignalNotifier
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 每日收盤資料公布後（台北時間約 14:30）更新自選股日K，
 * 若最新一根 K 棒出現進出場訊號且尚未推播過，則發出通知。
 */
class SignalCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TaStockApp).container
        val repo = container.stockRepository
        val settings = container.settingsRepository.settings.first()
        val watchlist = repo.getWatchlist()
        if (watchlist.isEmpty()) return Result.success()

        var failures = 0
        for (item in watchlist) {
            try {
                repo.refreshCandles(item.code, item.market)
            } catch (e: Exception) {
                failures++
                continue
            }
            if (!settings.notificationsEnabled) continue
            val candles = repo.getCandles(item.code)
            if (candles.isEmpty()) continue
            val signal = TechnicalAnalyzer.analyze(candles, settings.params).signalOnLastBar ?: continue
            val latest = repo.getWatchlistItem(item.code) ?: continue
            if (signal.date.toEpochDay() > latest.lastNotifiedEpochDay) {
                SignalNotifier.notify(applicationContext, item.code, item.name, signal)
                repo.markNotified(item.code, signal.date)
            }
        }
        return if (failures == watchlist.size && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "daily_signal_check"
        private const val ONE_SHOT_NAME = "manual_signal_check"
        private val RUN_AT: LocalTime = LocalTime.of(14, 30)

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val now = ZonedDateTime.now(StockRepository.TAIPEI)
            var next = now.with(RUN_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<SignalCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** 立即執行一次（設定頁的「立即檢查」）。 */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SignalCheckWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

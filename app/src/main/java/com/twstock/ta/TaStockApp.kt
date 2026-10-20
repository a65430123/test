package com.twstock.ta

import android.app.Application
import com.twstock.ta.notification.SignalNotifier
import com.twstock.ta.worker.SignalCheckWorker

class TaStockApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SignalNotifier.createChannel(this)
        SignalCheckWorker.schedule(this)
    }
}

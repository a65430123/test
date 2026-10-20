package com.twstock.ta.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.twstock.ta.TaStockApp
import com.twstock.ta.ui.detail.StockDetailViewModel
import com.twstock.ta.ui.settings.SettingsViewModel
import com.twstock.ta.ui.watchlist.WatchlistViewModel

object AppViewModelProvider {
    private fun CreationExtras.app(): TaStockApp = this[APPLICATION_KEY] as TaStockApp

    val Factory = viewModelFactory {
        initializer {
            val c = app().container
            WatchlistViewModel(c.stockRepository, c.settingsRepository)
        }
        initializer {
            val c = app().container
            StockDetailViewModel(createSavedStateHandle(), c.stockRepository, c.settingsRepository)
        }
        initializer {
            SettingsViewModel(app(), app().container.settingsRepository)
        }
    }
}

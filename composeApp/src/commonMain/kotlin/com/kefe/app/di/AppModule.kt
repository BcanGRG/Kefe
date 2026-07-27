package com.kefe.app.di

import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.data.remote.SamplePriceRemoteDataSource
import com.kefe.app.data.remote.createKefeHttpClient
import com.kefe.app.data.repository.DefaultPriceRepository
import com.kefe.app.data.repository.InMemoryPortfolioRepository
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.ui.screens.account.ActivityViewModel
import com.kefe.app.ui.screens.account.LoginViewModel
import com.kefe.app.ui.screens.account.SettingsViewModel
import com.kefe.app.ui.screens.account.ShareViewModel
import com.kefe.app.ui.screens.assets.AssetDetailViewModel
import com.kefe.app.ui.screens.assets.AssetsViewModel
import com.kefe.app.ui.screens.goals.GoalDetailViewModel
import com.kefe.app.ui.screens.goals.GoalsViewModel
import com.kefe.app.ui.screens.market.MarketViewModel
import com.kefe.app.ui.screens.summary.SummaryViewModel
import com.kefe.app.ui.screens.transaction.AddTransactionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {

    // Ktor istemcisi tek ornek: baglanti havuzu ve motor paylasilir.
    single { createKefeHttpClient() }

    single<PriceRemoteDataSource> { SamplePriceRemoteDataSource() }

    // Uygulamanin "bugun"u tek yerden gelir - getiri ve projeksiyon hesaplari
    // ayni gune gore calissin diye.
    single<KefeClock> { FixedKefeClock() }

    single<PortfolioRepository> { InMemoryPortfolioRepository() }
    single<PriceRepository> { DefaultPriceRepository(get()) }

    viewModelOf(::SummaryViewModel)
    viewModelOf(::AssetsViewModel)
    viewModelOf(::GoalsViewModel)
    viewModelOf(::MarketViewModel)
    viewModelOf(::AddTransactionViewModel)
    viewModelOf(::ActivityViewModel)
    viewModelOf(::ShareViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LoginViewModel)

    // Detay ekranlari hedef/pozisyon kimligini calisma aninda alir.
    viewModel { (positionId: String) -> AssetDetailViewModel(get(), get(), positionId) }
    viewModel { (goalId: String) -> GoalDetailViewModel(get(), goalId) }
}

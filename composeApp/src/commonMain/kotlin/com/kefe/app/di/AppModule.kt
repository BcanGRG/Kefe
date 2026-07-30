package com.kefe.app.di

import com.kefe.app.data.remote.FreeMarketApi
import com.kefe.app.data.remote.LivePriceRemoteDataSource
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.data.remote.AuthApi
import com.kefe.app.data.remote.SupabaseAuthApi
import com.kefe.app.data.remote.TcmbApi
import com.kefe.app.data.remote.TefasApi
import com.kefe.app.data.remote.createKefeHttpClient
import com.kefe.app.data.backup.FileTransfer
import com.kefe.app.data.repository.SqlDelightAuthRepository
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.data.repository.SqlDelightPriceRepository
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.SystemKefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.security.BiometricGate
import com.kefe.app.ui.screens.account.ActivityViewModel
import com.kefe.app.ui.screens.account.LoginViewModel
import com.kefe.app.ui.screens.account.ProfileSetupViewModel
import com.kefe.app.ui.screens.account.SettingsViewModel
import com.kefe.app.ui.screens.account.ProfilesViewModel
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

/**
 * Ornek portfoyu (13 pozisyon, 21 islem) veritabanina tohumlar - GELISTIRME BAYRAGI.
 *
 * Varsayilan KAPALI: uygulama bos acilir ve kullanici kendi verisini girer.
 * Acmak icin bayragi true yapmak yetmez, veritabani dosyasinin da silinmesi
 * gerekir - tohum tek seferlik bir bayrakla korunuyor ki kullanici her seyi
 * silince ornek veri geri gelmesin.
 */
const val SeedSampleData: Boolean = false

val appModule = module {

    // Ktor istemcisi tek ornek: baglanti havuzu ve motor paylasilir.
    single { createKefeHttpClient() }

    single { FreeMarketApi(get()) }
    single { TcmbApi(get()) }
    single { TefasApi(get()) }
    single<PriceRemoteDataSource> { LivePriceRemoteDataSource(get(), get(), get()) }

    // Uygulamanin "bugun"u tek yerden gelir - getiri ve projeksiyon hesaplari
    // ayni gune gore calissin diye. Kayitlar diske yazildigi icin cihazin
    // gercek takvimi kullanilir; sabit saat yalniz testlerde.
    single<KefeClock> { SystemKefeClock() }

    // Veritabani surec omru boyunca tek: nesneyi Koin degil KefePlatform tutar,
    // Koin grafigi Compose icinde yeniden kurulsa bile ayni baglanti dondurulur.
    single { KefePlatform.database(get<KefeClock>().today(), SeedSampleData) }

    // Fiyat deposu ONCE: portfoy pozisyonlari okurken guncel fiyatla degerler.
    single<PriceRepository> { SqlDelightPriceRepository(get(), get(), get()) }
    single<PortfolioRepository> { SqlDelightPortfolioRepository(get(), get(), get()) }
    single<PreferencesRepository> { SqlDelightPreferencesRepository(get()) }

    // Kimlik: Supabase auth ucu + oturumu cihazda tutan depo.
    single<AuthApi> { SupabaseAuthApi(get()) }
    single<AuthRepository> { SqlDelightAuthRepository(get(), get(), get()) }

    // Dosya paylasimi/secimi platforma iner; Android tarafi Activity ister.
    single { FileTransfer() }

    // Cihaz kilidi. Masaustunde karsiligi yok; oradaki actual Unsupported doner.
    single { BiometricGate() }

    viewModelOf(::SummaryViewModel)
    viewModelOf(::AssetsViewModel)
    viewModelOf(::GoalsViewModel)
    viewModelOf(::MarketViewModel)
    viewModelOf(::AddTransactionViewModel)
    viewModelOf(::ActivityViewModel)
    viewModelOf(::ProfilesViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::ProfileSetupViewModel)

    // Detay ekranlari hedef/pozisyon kimligini calisma aninda alir.
    viewModel { (positionId: String) -> AssetDetailViewModel(get(), get(), get(), positionId) }
    viewModel { (goalId: String) -> GoalDetailViewModel(get(), get(), goalId) }
}

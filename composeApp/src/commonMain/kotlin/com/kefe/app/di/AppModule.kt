package com.kefe.app.di

import com.kefe.app.data.remote.DefaultFundCodes
import com.kefe.app.data.remote.FreeMarketApi
import com.kefe.app.data.remote.LivePriceRemoteDataSource
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.data.remote.AuthApi
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.remote.SupabaseAuthApi
import com.kefe.app.data.remote.SupabasePostgrestApi
import com.kefe.app.data.remote.TcmbApi
import com.kefe.app.data.remote.TefasApi
import com.kefe.app.data.remote.createKefeHttpClient
import com.kefe.app.data.backup.FileTransfer
import com.kefe.app.db.KefeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kefe.app.data.repository.SqlDelightAuthRepository
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.data.repository.SqlDelightPriceRepository
import com.kefe.app.data.sync.PullEngine
import com.kefe.app.data.sync.PushEngine
import com.kefe.app.data.sync.SyncCoordinator
import com.kefe.app.data.sync.SyncLocalSink
import com.kefe.app.data.sync.SyncLocalSource
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.SystemKefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.security.BiometricGate
import com.kefe.app.security.SecureStore
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
    // Cekilecek fonlar CALISMA ANINDA portfoyden gelir: kullanicinin tuttugu
    // fonlar (miktar > 0) gunluk tazelenir, satilan fon bosuna cekilmez. Portfoy
    // deposu fiyat deposuna bagli oldugu icin DONGUYE girmemek adina kodlar
    // dogrudan veritabanindan okunur.
    single<PriceRemoteDataSource> {
        val database = get<KefeDatabase>()
        LivePriceRemoteDataSource(get(), get(), get(), fundCodes = { heldFundCodes(database) })
    }

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
    // SecureStore jetonu cihaza sertlestirerek yazar (Android Keystore).
    single { SecureStore() }
    single<AuthApi> { SupabaseAuthApi(get()) }
    single<AuthRepository> { SqlDelightAuthRepository(get(), get(), get(), get()) }

    // Senkron/push: yereldeki degisiklikleri Supabase'e iten yon. Kordinator
    // girisliyken yerel degisimleri dinler; PostgrestApi kullanicinin jetonuyla
    // upsert eder (RLS o hesaba kilitler).
    single<PostgrestApi> { SupabasePostgrestApi(get()) }
    single { SyncLocalSource(get()) }
    single { SyncLocalSink(get()) }
    single { PushEngine(get(), get(), get(), get(), get()) }
    single { PullEngine(get(), get(), get()) }
    single { SyncCoordinator(get(), get(), get(), get()) }

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

/**
 * Kullanicinin HALA TUTTUGU fon kodlari - gunluk tazeleme bunlari ceker.
 * Portfoy deposunu DEGIL dogrudan veritabanini okur: portfoy deposu fiyat
 * deposuna baglidir, tersine bagimlilik donguye sokar. "pos_fund_mac" -> "MAC".
 */
private suspend fun heldFundCodes(database: KefeDatabase): List<String> =
    withContext(Dispatchers.Default) {
        database.positionQueries.selectHeldFundIds().executeAsList()
            .map { it.removePrefix("pos_fund_").uppercase() }
    }

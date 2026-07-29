package com.kefe.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.kefe.app.data.db.toDomain
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fiyat deposu.
 *
 * IKI AYRI TABLO: elle girilen degerler uzak fiyatlarla ayni satirda dursaydi
 * bir yenileme onlari ezerdi - "elle girilen deger otomatik yenilemede EZILMEZ"
 * sozlesmesinin tam tersi. Bindirme okuma sirasinda yapilir, tek geri donus
 * [clearManualPrice].
 *
 * Uzak fiyatlar ayrica onbellege yazilir: uygulama cevrimdisi acildiginda ekran
 * bos kalmasin. Bellek ici surumde acilista hicbir fiyat yoktu ve Islem Ekle
 * ekrani birim fiyati 0 gosteriyordu.
 */
class SqlDelightPriceRepository(
    private val database: KefeDatabase,
    private val remote: PriceRemoteDataSource,
    private val clock: KefeClock,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : PriceRepository {

    private val priceQueries = database.priceQueries

    /**
     * Bu oturumda cekilen liste. Onbellek yalniz SOGUK ACILIS icin: tablodan
     * okunan satirlarin sirasi kaynagin sirasi degildir, ekranda ise liste
     * kaynaktaki sirayla (once altin, sonra doviz, sonra fon) bekleniyor.
     */
    private val fetched = MutableStateFlow<List<Price>?>(null)

    /**
     * Son yenileme denemesi basarisiz miydi.
     *
     * Tazelik artik ONBELLEGIN YASINDAN hesaplaniyor, oturum bayragindan degil:
     * uygulama her acilista "Çevrimdışı" ile basliyordu, cunku bayrak Offline
     * baslayip yalniz basarili bir cekmeyle donuyordu. Bes dakika once alinmis
     * fiyatlarla acilan bir uygulamanin cevrimdisi oldugunu soylemesi yanlisti.
     *
     * Bu bayrak yalnizca su isi yapar: yenileme DENENDI ve basarisiz olduysa,
     * onbellek hala taze olsa bile kullaniciya bagli olmadigimizi soyleriz.
     */
    private val lastRefreshFailed = MutableStateFlow(false)

    override fun observePrices(): Flow<PriceBoard> = combine(
        priceQueries.selectCachedPrices().asFlow().mapToList(dispatcher),
        priceQueries.selectManualPrices().asFlow().mapToList(dispatcher),
        fetched,
        lastRefreshFailed,
    ) { cached, manual, session, failed ->
        val base = session ?: cached.map { it.toDomain() }
        val overrides = manual.associate { it.assetKey to it.price }
        val merged = base.map { price ->
            val override = overrides[price.assetKey]
            if (override == null) {
                price
            } else {
                // Elle girilen deger TEK fiyattir: alis tarafi eski onbellekten
                // kalirsa portfoy degeri (satis fiyatini kullanir) kullanicinin
                // girdigi rakami yok sayardi.
                price.copy(
                    bid = override,
                    ask = override,
                    source = PriceSource.Manual,
                    isManual = true,
                )
            }
        }
        PriceBoard(
            prices = merged,
            updatedAtLabel = merged.firstOrNull { !it.isManual }?.timestamp ?: "—",
            freshness = freshnessOf(cached.maxOfOrNull { it.fetchedAtEpochSeconds }, failed),
        )
    }

    /**
     * Onbellegin yasindan tazelik.
     *
     * Hic fiyat yoksa cevrimdisiyiz - gosterecek bir sey de yok. Son yenileme
     * patladiysa yine cevrimdisi: onbellek taze olsa bile kullanici bagli
     * olmadigimizi bilmeli. Aksi halde yas karar verir; [StaleAfterSeconds]
     * tasarimdaki "2 saatten eski" kurali.
     */
    private fun freshnessOf(newestFetchSeconds: Long?, failed: Boolean): PriceFreshness {
        // Elde hicbir fiyat yok. Bu tek basina "ag yok" DEMEK DEGILDIR - ilk
        // cekme yolda olabilir. Ancak denenip basarisiz olduysa cevrimdisiyiz.
        if (newestFetchSeconds == null || newestFetchSeconds <= 0L) {
            return if (failed) PriceFreshness.Offline else PriceFreshness.Loading
        }
        if (failed) return PriceFreshness.Offline
        val ageSeconds = clock.nowEpochMillis() / 1000L - newestFetchSeconds
        return if (ageSeconds > StaleAfterSeconds) PriceFreshness.Stale else PriceFreshness.Fresh
    }

    override fun observePriceHistory(assetKey: String): Flow<List<Double>> =
        priceQueries.selectPriceHistory(assetKey).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.price } }

    /**
     * Ayni anda tek cekme. Her dokunus kendi coroutine'ini baslatiyordu: arka
     * arkaya basilan yenile dugmesi ust uste binen istekler aciyor, kaynak da
     * bir noktada cevap vermeyi birakiyordu - ekrandaki "güncellenemedi" uyarisi
     * buydu. Kilit istekleri siraya alir, [MinRefreshSeconds] de sirada bekleyeni
     * bos yere aga cikarmaz.
     */
    private val refreshMutex = Mutex()

    /** Son BASARILI cekmenin saniyesi. Sifir ise henuz cekilmedi. */
    private var lastFetchAtSeconds = 0L

    override suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        val now = clock.nowEpochMillis() / 1000L

        // Az once BASARIYLA cekildiyse tekrar cikmayiz: fiyatlar bu arada kurus
        // oynar, istek ise kaynagin sinirina yaklastirir. Elde olan zaten taze.
        //
        // Son deneme PATLADIYSA kisitlama uygulanmaz. Aksi halde ag geri geldigi
        // anda yenileye basan kullanici otuz saniye boyunca sessizce reddediliyor,
        // ustelik "basarili" cevabi aliyordu: ekran cevrimdisi kalirken hicbir sey
        // olmuyordu ve tek yapabildigi tekrar basmakti.
        val recentlySucceeded = lastFetchAtSeconds > 0L &&
            now - lastFetchAtSeconds < MinRefreshSeconds
        if (recentlySucceeded && !lastRefreshFailed.value) {
            return@withLock Result.success(Unit)
        }
        fetchAndStore(now)
    }

    private suspend fun fetchAndStore(nowSeconds: Long): Result<Unit> = runCatching {
        val prices = remote.fetchPrices()
        fetched.value = prices
        val today = clock.today()
        withContext(dispatcher) {
            database.transaction {
                prices.forEach { price ->
                    priceQueries.upsertCachedPrice(
                        assetKey = price.assetKey,
                        label = price.label,
                        bid = price.bid,
                        ask = price.ask,
                        changePercent = price.changePercent,
                        timestamp = price.timestamp,
                        source = price.source,
                        assetClass = price.assetClass,
                        // Tazelik bu damgadan hesaplaniyor; 0 yazildigi surece
                        // "2 saatten eski" kurali isletilemiyordu.
                        fetchedAtEpochSeconds = nowSeconds,
                    )
                    // Gunun fiyati AYRICA gecmise yazilir: onbellek uzerine
                    // yazildigi icin gecmisi tutamaz, gecmis fiyat da sonradan
                    // ogrenilemez. Bugun yazilmazsa bugun kayiptir.
                    priceQueries.upsertPriceHistory(
                        assetKey = price.assetKey,
                        dateYear = today.year.toLong(),
                        dateMonth = today.month.toLong(),
                        dateDay = today.day.toLong(),
                        price = price.ask,
                    )
                }
            }
        }
        lastRefreshFailed.value = false
        lastFetchAtSeconds = nowSeconds
    }.onFailure {
        // Ag yoksa son bilinen fiyatlar ekranda kalir; yalniz tazelik etiketi duser.
        lastRefreshFailed.value = true
    }

    override suspend fun setManualPrice(assetKey: String, value: Double) {
        withContext(dispatcher) {
            priceQueries.upsertManualPrice(
                assetKey = assetKey,
                price = value,
                updatedAtEpochSeconds = 0L,
            )
        }
    }

    override suspend fun clearManualPrice(assetKey: String) {
        withContext(dispatcher) {
            priceQueries.deleteManualPrice(assetKey)
        }
    }
}

/** Tasarimin "2 saatten eski" esigi. */
private const val StaleAfterSeconds = 2L * 60 * 60

/**
 * Iki cekme arasindaki en kisa sure.
 *
 * Kaynak dakikada bir damga atiyor; bundan sik cekmek yeni bir sey getirmez,
 * yalniz kaynagi zorlar.
 */
private const val MinRefreshSeconds = 30L

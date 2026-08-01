package com.kefe.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.kefe.app.data.db.toDomain
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PeriodChanges
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PricePoint
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.periodChangesOf
import com.kefe.app.domain.model.plusMonths
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.domain.repository.RefreshOutcome
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
        // Haftalik/aylik degisimin kaynagi. Pencere aylik hesabin ihtiyacindan
        // genis tutuldu: sinir yalniz alt sinirdir, fazlasi zarar vermez ama
        // uygulama gece yarisini gecerse pencere daralmasin.
        priceQueries.selectRecentPriceHistory(dateKeyOf(clock.today().plusMonths(-2)))
            .asFlow().mapToList(dispatcher),
    ) { cached, manual, session, failed, history ->
        // Oturumdaki cekim onbellegin UZERINE BINDIRILIR, yerine gecmez.
        //
        // Once `session ?: cached` idi ve tek bir kaynagin dusmesi yenilemenin
        // tamamini dusurdugu icin sorun cikmiyordu. Kaynaklar birbirinden
        // bagimsiz hale gelince (bkz. LivePriceRemoteDataSource) kismi bir cekim
        // tabloyu KIRPIYORDU: serbest piyasa tokezleyip doviz TCMB'den gelince
        // ekranda yalniz doviz kaliyor, altin satirlari tahtadan dusuyor ve
        // toplam birikim bir anda ucuruma iniyordu (₺946k -> ₺693k).
        val cachedPrices = cached.map { it.toDomain() }
        val base = if (session == null) {
            cachedPrices
        } else {
            val fresh = session.associateBy { it.assetKey }
            cachedPrices.filterNot { it.assetKey in fresh } + session
        }
        // Gecmis varlik anahtarina gore gruplanir; sorgu zaten tarihe gore sirali.
        val historyByKey = history
            .groupBy { it.assetKey }
            .mapValues { (_, rows) ->
                rows.map { PricePoint(KefeDate(it.dateYear.toInt(), it.dateMonth.toInt(), it.dateDay.toInt()), it.price) }
            }
        val today = clock.today()

        val overrides = manual.associate { it.assetKey to it.price }
        val merged = base.map { price ->
            val override = overrides[price.assetKey]
            if (override != null) {
                // Elle girilen deger TEK fiyattir: alis tarafi eski onbellekten
                // kalirsa portfoy degeri (satis fiyatini kullanir) kullanicinin
                // girdigi rakami yok sayardi.
                //
                // Donemsel degisim ELLE FIYATTA YOK: gecmisteki satirlar kaynagin
                // fiyatlari, bugunku ise kullanicinin girdigi rakam - ikisini
                // kiyaslamak "haftalik degisim" degil, iki ayri olcunun farki
                // olurdu.
                price.copy(
                    bid = override,
                    ask = override,
                    source = PriceSource.Manual,
                    isManual = true,
                    weekChangePercent = null,
                    monthChangePercent = null,
                )
            } else {
                val changes = historyByKey[price.assetKey]
                    ?.let { periodChangesOf(it, price.ask, today) }
                    ?: PeriodChanges.Unknown
                price.copy(
                    weekChangePercent = changes.week,
                    monthChangePercent = changes.month,
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

    override fun observePriceHistory(assetKey: String): Flow<List<PricePoint>> =
        priceQueries.selectPriceHistory(assetKey).asFlow().mapToList(dispatcher)
            .map { rows ->
                rows.map {
                    PricePoint(
                        KefeDate(it.dateYear.toInt(), it.dateMonth.toInt(), it.dateDay.toInt()),
                        it.price,
                    )
                }
            }

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

    override suspend fun refresh(): Result<RefreshOutcome> = refreshMutex.withLock {
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
            // Kalan sure kullaniciya soylenir. En az bir saniye: "0 sn sonra
            // deneyin" demek, hemen denenebilecegini ama denenemedigini soylemek
            // olurdu.
            val remaining = (MinRefreshSeconds - (now - lastFetchAtSeconds))
                .toInt()
                .coerceAtLeast(1)
            return@withLock Result.success(RefreshOutcome.Throttled(remaining))
        }
        fetchAndStore(now).map { RefreshOutcome.Fetched }
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
                    // Kaynak GECMIS de verdiyse (TEFAS bir aylik seri donuyor)
                    // o da yazilir. "Gecmis fiyat sonradan ogrenilemez" kurali
                    // burada delinmiyor: bu, kaynagin kendi kaydi - biz
                    // uydurmuyoruz. Fonlarda haftalik/aylik degisimi ilk gunden
                    // gercek yapan sey budur.
                    price.history.forEach { point ->
                        priceQueries.upsertPriceHistory(
                            assetKey = price.assetKey,
                            dateYear = point.date.year.toLong(),
                            dateMonth = point.date.month.toLong(),
                            dateDay = point.date.day.toLong(),
                            price = point.price,
                        )
                    }
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

/**
 * Tarihi karsilastirilabilir tek sayiya cevirir: 2026-07-31 -> 20260731.
 *
 * `price_history` tarihi uc kolonda tutuyor (SQLDelight lehcesinde tarih tipi
 * yok); "su gunden yenisi" sorgusu ancak boyle yazilabiliyor.
 */
private fun dateKeyOf(date: KefeDate): Long =
    date.year * 10_000L + date.month * 100L + date.day

/** Tasarimin "2 saatten eski" esigi. */
private const val StaleAfterSeconds = 2L * 60 * 60

/**
 * Iki cekme arasindaki en kisa sure.
 *
 * Kaynak dakikada bir damga atiyor; bundan sik cekmek yeni bir sey getirmez,
 * yalniz kaynagi zorlar.
 */
private const val MinRefreshSeconds = 30L

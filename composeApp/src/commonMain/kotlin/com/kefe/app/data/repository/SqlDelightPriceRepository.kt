package com.kefe.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.kefe.app.data.db.toDomain
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
     * Tazelik diske yazilmaz: gercek zaman damgasi uretecek bir saat yok
     * (KefeClock yalniz gun veriyor). Bu yuzden uygulama her acilista "cevrimdisi"
     * baslar ve ilk basarili yenilemede tazelenir - bellek ici surumun davranisi.
     */
    private val freshness = MutableStateFlow(PriceFreshness.Offline)

    override fun observePrices(): Flow<PriceBoard> = combine(
        priceQueries.selectCachedPrices().asFlow().mapToList(dispatcher),
        priceQueries.selectManualPrices().asFlow().mapToList(dispatcher),
        fetched,
        freshness,
    ) { cached, manual, session, state ->
        val base = session ?: cached.map { it.toDomain() }
        val overrides = manual.associate { it.assetKey to it.price }
        val merged = base.map { price ->
            val override = overrides[price.assetKey]
            if (override == null) {
                price
            } else {
                price.copy(ask = override, source = PriceSource.Manual, isManual = true)
            }
        }
        PriceBoard(
            prices = merged,
            updatedAtLabel = merged.firstOrNull { !it.isManual }?.timestamp ?: "—",
            freshness = state,
        )
    }

    override suspend fun refresh(): Result<Unit> = runCatching {
        val prices = remote.fetchPrices()
        fetched.value = prices
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
                        // Gercek saat kaynagi gelene kadar 0: "2 saatten eski"
                        // kurali (PriceFreshness.Stale) bugun de isletilmiyor.
                        fetchedAtEpochSeconds = 0L,
                    )
                }
            }
        }
        freshness.value = PriceFreshness.Fresh
    }.onFailure {
        // Ag yoksa son bilinen fiyatlar ekranda kalir; yalniz tazelik etiketi duser.
        freshness.value = PriceFreshness.Offline
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

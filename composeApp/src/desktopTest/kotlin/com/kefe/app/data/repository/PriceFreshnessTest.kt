package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.RefreshOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Fiyat tazeligi ve yenileme kisitlamasinin kenar durumlari.
 *
 * Ikisi de "iyi gunde" dogru, kotu gunde yanlisti:
 *   - Tazelik EN YENI satirdan hesaplaniyordu; kismi cekmede tek taze satir
 *     butun bayat satirlarin uzerini ortuyor, tahta "Guncel" kaliyordu.
 *   - Kisitlama gecen sureyi isaretsiz varsayiyordu; saat geriye giderse
 *     (saat dilimi, elle duzeltme, ag saatiyle senkron) gecen sure negatif
 *     cikiyor, yenileme kilitleniyor ve ekrana kocaman bir bekleme suresi
 *     yaziliyordu.
 */
class PriceFreshnessTest {

    /** Gercekci bir damga: saniyeye bolununce uc saat oncesi de pozitif kalmali. */
    private val TestNow = 1_786_518_905_000L

    private class MovableClock(var millis: Long) : KefeClock {
        override fun today(): KefeDate = KefeDate(2026, 8, 12)
        override fun nowEpochMillis(): Long = millis
    }

    private class StubRemote(var prices: List<Price>) : PriceRemoteDataSource {
        override suspend fun fetchPrices(): List<Price> = prices
    }

    private fun price(key: String, ask: Double = 100.0) = Price(
        assetKey = key,
        label = key,
        bid = ask,
        ask = ask,
        changePercent = null,
        timestamp = "",
        source = PriceSource.FreeMarket,
        assetClass = AssetClass.Gold,
    )

    private fun newRepository(
        clock: MovableClock,
        remote: StubRemote,
    ): Pair<SqlDelightPriceRepository, KefeDatabase> {
        val driver = JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val db = createKefeDatabase(driver)
        return SqlDelightPriceRepository(db, remote, clock) to db
    }

    /** Iki satir taze cekildi: tahta taze. */
    @Test
    fun hepsiTazeyseTAZE() = runTest {
        val clock = MovableClock(millis = TestNow)
        val (repo, _) = newRepository(clock, StubRemote(listOf(price("gold_gram"), price("usd_try"))))

        repo.refresh()

        assertEquals(PriceFreshness.Fresh, repo.observePrices().first().freshness)
    }

    /**
     * ASIL HATA: tek satir taze, digeri uc saatlik. En yeni satira bakan surum
     * "Guncel" diyordu.
     */
    @Test
    fun TEKTazeSatirBayatlariMASKELEMEZ() = runTest {
        val clock = MovableClock(millis = TestNow)
        val (repo, db) = newRepository(clock, StubRemote(listOf(price("gold_gram"))))
        repo.refresh()

        // Ikinci varlik uc saat once cekilmis gibi yazilir - kismi cekmenin izi.
        val threeHoursAgo = clock.millis / 1000L - 3 * 60 * 60
        db.priceQueries.upsertCachedPrice(
            assetKey = "usd_try",
            label = "Dolar",
            bid = 47.0,
            ask = 47.0,
            changePercent = 0.0,
            timestamp = "",
            source = PriceSource.FreeMarket,
            assetClass = AssetClass.Fx,
            fetchedAtEpochSeconds = threeHoursAgo,
            nativePrice = null,
            nativeCurrency = null,
            quoteDateKey = null,
        )

        assertEquals(
            PriceFreshness.Stale,
            repo.observePrices().first().freshness,
            "tek taze satir butun bayat satirlari maskeledi",
        )
    }

    /** Saat geriye giderse yenileme KILITLENMEZ. */
    @Test
    fun saatGERIYEGiderseYenilemeKilitlenmez() = runTest {
        val clock = MovableClock(millis = TestNow)
        val (repo, _) = newRepository(clock, StubRemote(listOf(price("gold_gram"))))
        repo.refresh()

        // Bir gun geriye: gecen sure -86.400 saniye.
        clock.millis -= 24 * 60 * 60 * 1000L

        val outcome = repo.refresh().getOrNull()

        assertTrue(
            outcome is RefreshOutcome.Fetched,
            "saat geriye gidince yenileme reddedildi: $outcome",
        )
    }

    /** Ileri saatte kisitlama normal calismaya devam eder. */
    @Test
    fun ayniAndaIkinciYenilemeKISITLANIR() = runTest {
        val clock = MovableClock(millis = TestNow)
        val (repo, _) = newRepository(clock, StubRemote(listOf(price("gold_gram"))))
        repo.refresh()

        val outcome = repo.refresh().getOrNull()

        assertTrue(outcome is RefreshOutcome.Throttled, "kisitlama calismadi: $outcome")
        assertTrue(
            (outcome as RefreshOutcome.Throttled).retryInSeconds in 1..60,
            "anlamsiz bekleme suresi: ${outcome.retryInSeconds}",
        )
    }
}

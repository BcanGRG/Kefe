package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kaynak gunluk degisimi vermezse gecmisten hesaplanir.
 *
 * SEBEP OLCULDU, tahmin edilmedi. Serbest piyasa ucu (`today.json`) yalnizca
 * gram altin, has altin ve gumus icin `Change` dolduruyor; ceyrek, yarim, tam,
 * ata ve butun ayar kalemlerine duz SIFIR yaziyor - fiyatlari gun icinde
 * oynadigi halde. Portfoyunun %95'i altin olan bir kullanicinin "bugunku
 * getiri" satiri bu yuzden neredeyse bostu ve Piyasa tablosunda o satirlar
 * %0,00 goruyordu.
 *
 * Kural: KAYNAK ONCE, gecmis YEDEK.
 */
private class StubRemote(private val prices: List<Price>) : PriceRemoteDataSource {
    override suspend fun fetchPrices(): List<Price> = prices
}

private val Bugun = KefeDate(2026, 8, 3)
private val Dun = KefeDate(2026, 8, 2)

private class StubClock : KefeClock {
    override fun today(): KefeDate = Bugun
    override fun nowEpochMillis(): Long = 1_000_000L
}

private fun price(
    assetKey: String,
    ask: Double,
    changePercent: Double,
) = Price(
    assetKey = assetKey,
    label = assetKey,
    bid = ask,
    ask = ask,
    changePercent = changePercent,
    timestamp = "10:00",
    source = PriceSource.FreeMarket,
    assetClass = AssetClass.Gold,
    quoteDate = Bugun,
)

class DailyChangeFallbackTest {

    private fun harness(prices: List<Price>): Pair<SqlDelightPriceRepository, KefeDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val database = createKefeDatabase(driver)
        return SqlDelightPriceRepository(database, StubRemote(prices), StubClock()) to database
    }

    private fun KefeDatabase.history(assetKey: String, date: KefeDate, value: Double) {
        priceQueries.upsertPriceHistory(
            assetKey = assetKey,
            dateYear = date.year.toLong(),
            dateMonth = date.month.toLong(),
            dateDay = date.day.toLong(),
            price = value,
        )
    }

    /** ASIL DUZELTME: kaynak sifir yaziyor, gecmiste dunun fiyati var. */
    @Test
    fun kaynakSusuncaGecmistenHesaplanir() = runTest {
        // Ceyrek altin: kaynak Change=0 gonderiyor ama fiyat 10.000 -> 10.200.
        val (repo, db) = harness(listOf(price("gold_quarter", 10_200.0, 0.0)))
        db.history("gold_quarter", Dun, 10_000.0)
        repo.refresh()

        val board = repo.observePrices().first()
        val quarter = board.prices.single { it.assetKey == "gold_quarter" }
        assertEquals(2.0, quarter.changePercent, 1e-9)
    }

    /**
     * Kaynagin rakami varsa ONA DOKUNULMAZ. Gram altinin Change'i gercek ve
     * gun ici; gecmisten hesaplanan (gunde tek nokta) daha kaba olurdu.
     */
    @Test
    fun kaynakKonusuncaGecmiseBAKILMAZ() = runTest {
        val (repo, db) = harness(listOf(price("gold_gram", 6_400.0, 2.14)))
        // Gecmis bambaska bir rakam verirdi: 6.000 -> 6.400 = %6,67.
        db.history("gold_gram", Dun, 6_000.0)
        repo.refresh()

        val board = repo.observePrices().first()
        val gram = board.prices.single { it.assetKey == "gold_gram" }
        assertEquals(2.14, gram.changePercent, 1e-9)
    }

    /**
     * Gecmis de yoksa sifir kalir. Uydurulacak bir rakam yok; gunluk degisim
     * haftalik/aylik gibi null olamiyor (Position.dailyChangePercent non-null),
     * ama sifir katki vermek dogru cevap.
     */
    @Test
    fun gecmisYoksaSifirKALIR() = runTest {
        val (repo, _) = harness(listOf(price("gold_full", 40_000.0, 0.0)))
        repo.refresh()

        val board = repo.observePrices().first()
        assertEquals(0.0, board.prices.single { it.assetKey == "gold_full" }.changePercent, 1e-9)
    }

    /** Gercekten oynamayan varlikta yedek yol da sifir verir - bozmuyor. */
    @Test
    fun gercektenOynamayanSifirKALIR() = runTest {
        val (repo, db) = harness(listOf(price("gold_half", 20_000.0, 0.0)))
        db.history("gold_half", Dun, 20_000.0)
        repo.refresh()

        val board = repo.observePrices().first()
        assertEquals(0.0, board.prices.single { it.assetKey == "gold_half" }.changePercent, 1e-9)
    }
}

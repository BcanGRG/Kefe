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
 * Gunluk degisimin hangi kaynaktan geldigi.
 *
 * Kural: ONCE "OYNADI MI", sonra KAYNAK, en son GECMIS.
 *
 * Yedek yol su sebeple konmustu: serbest piyasa ucu (`today.json`) yalnizca
 * gram/has altin ve gumus icin `Change` dolduruyor, ceyrek/yarim/tam/ata ve
 * butun ayar kalemlerine duz SIFIR yaziyordu - portfoyunun %95'i altin olan bir
 * kullanicinin "bugunku getiri" satiri bu yuzden neredeyse bostu.
 *
 * 9 AGUSTOS 2026'DA UC YENIDEN OLCULDU ve o varsayim artik gecerli degil:
 * CEYREKALTIN 2.09, YARIMALTIN 2.09, TAMALTIN 2.09, ATAALTIN 2.09, YIA 2.09,
 * 18AYARALTIN 2.09, 14AYARALTIN 2.09, GRA 2.59, HAS 2.59, GUMUS 3.57 - 86
 * semboldan yalnizca biri sifir. Yedek yol duruyor ama artik nadiren tetikleniyor.
 *
 * AYNI OLCUMDE ASIL SORUN CIKTI: uc, piyasa KAPALIYKEN de Update_Date'i her
 * dakika ilerletiyor (10:04:01 -> 10:05:01) ve Change'i cumadan donmus halde
 * tutuyor. Damga "bugun kotasyon var" gibi gorundugu icin cumanin hareketi
 * pazar gununun "bugunku getiri"sine giriyordu.
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

    /**
     * PIYASA KAPALIYKEN kaynagin BAYAT rakami "bugun" sayilmaz.
     *
     * 9 Agustos 2026 Pazar gunu olculdu: uc, Update_Date'i her dakika
     * ilerletiyor (10:04:01 -> 10:05:01) ama butun altin fiyatlari ve Change
     * alanlari cumadan donmus halde duruyor. Damga "bugun kotasyon var" gibi
     * gorundugu icin cumanin +%2,09'u "bugunku getiri"ye giriyordu; oysa o gun
     * hicbir sey islem gormemisti.
     *
     * Fiyatin kendisi dogruyu soyluyor: deger onceki gunun kaydiyla AYNI.
     */
    @Test
    fun piyasaKapaliykenKaynaginBAYATRakamiSayilmaz() = runTest {
        // Cuma kapanisindan donmus ceyrek: fiyat ayni, Change hala +%2,09.
        val (repo, db) = harness(listOf(price("gold_quarter", 10_887.46, 2.09)))
        db.history("gold_quarter", Dun, 10_887.46)
        repo.refresh()

        val board = repo.observePrices().first()
        assertEquals(
            0.0,
            board.prices.single { it.assetKey == "gold_quarter" }.changePercent,
            1e-9,
        )
    }

    /** Fiyat oynadiysa kaynagin rakami AYNEN gecer - kural yalniz bayati susturur. */
    @Test
    fun fiyatOynadiysaKaynakAYNENGecer() = runTest {
        val (repo, db) = harness(listOf(price("gold_quarter", 10_900.0, 2.09)))
        db.history("gold_quarter", Dun, 10_887.46)
        repo.refresh()

        val board = repo.observePrices().first()
        assertEquals(
            2.09,
            board.prices.single { it.assetKey == "gold_quarter" }.changePercent,
            1e-9,
        )
    }

    /**
     * Haftalik ve aylik BU KURALDAN ETKILENMEZ: onlar zaten gecmis serisinden
     * ve tolerans penceresiyle hesaplaniyor, kapali gunlere dayanikli.
     */
    @Test
    fun haftalikKapaliGundenETKILENMEZ() = runTest {
        val (repo, db) = harness(listOf(price("gold_quarter", 10_887.46, 2.09)))
        db.history("gold_quarter", Dun, 10_887.46)
        db.history("gold_quarter", KefeDate(2026, 7, 27), 10_000.0)
        repo.refresh()

        val board = repo.observePrices().first()
        val quarter = board.prices.single { it.assetKey == "gold_quarter" }
        assertEquals(0.0, quarter.changePercent, 1e-9)
        assertEquals(8.8746, quarter.weekChangePercent ?: 0.0, 1e-4)
    }
}

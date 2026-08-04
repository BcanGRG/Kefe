package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Tutulmayan fon/hisse kotasyonu Piyasa tahtasinda kalmaz.
 *
 * Fon ve hisse satirlari YALNIZCA kullanici o araci tuttugu icin var - hangi
 * kodlarin cekilecegi de portfoyden turuyor. Varlik silininde ya da tamami
 * satilinca kotasyonun tahtada durmasi icin bir sebep kalmiyor; Piyasa listesi
 * elde olmayan bir sembolu gostermeye devam ediyordu.
 *
 * Altin, gumus ve doviz bu kuralin DISINDA: onlar piyasanin standart kalemleri
 * ve tutuluyor olmalari gerekmiyor. Testin asil isi bu ayrimi korumak - fazla
 * hevesli bir temizlik, tahtayi altinsiz birakirdi.
 */
class UnheldPriceCleanupTest {

    private val today = KefeDate(2026, 8, 3)

    private fun harness(): Pair<SqlDelightPortfolioRepository, KefeDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val database = createKefeDatabase(driver)
        return SqlDelightPortfolioRepository(
            database,
            FixedKefeClock(millis = 1_000L),
            NoPrices(),
        ) to database
    }

    private fun KefeDatabase.cache(assetKey: String, assetClass: AssetClass) {
        priceQueries.upsertCachedPrice(
            assetKey = assetKey,
            label = assetKey,
            bid = null,
            ask = 100.0,
            changePercent = 0.0,
            timestamp = "10:00",
            source = PriceSource.FreeMarket,
            assetClass = assetClass,
            fetchedAtEpochSeconds = 0L,
            nativePrice = null,
            nativeCurrency = null,
            quoteDateKey = null,
        )
    }

    private fun KefeDatabase.cachedKeys(): Set<String> =
        priceQueries.selectCachedPrices().executeAsList().map { it.assetKey }.toSet()

    private fun position(id: String, assetClass: AssetClass) = Position(
        id = id,
        name = id,
        assetClass = assetClass,
        quantity = 0.0,
        unit = QuantityUnit.Piece,
        unitPrice = 100.0,
        value = 0.0,
        cost = 0.0,
    )

    private fun buy(id: String, positionId: String, quantity: Double) = Transaction(
        id = id,
        positionId = positionId,
        date = today,
        side = TradeSide.Buy,
        quantity = quantity,
        unitPrice = 100.0,
        addedByMemberId = "member_owner",
    )

    @Test
    fun silinenFonunKotasyonuTahtadanDUSER() = runTest {
        val (repo, db) = harness()
        db.cache("fund_afa", AssetClass.Fund)
        repo.upsertPosition(position("pos_fund_afa", AssetClass.Fund))
        repo.addTransaction(buy("tx-1", "pos_fund_afa", 5.0))

        // Tutulurken durur.
        assertTrue("fund_afa" in db.cachedKeys())

        repo.deletePosition("pos_fund_afa")

        assertTrue("fund_afa" !in db.cachedKeys(), "silinen fon tahtada kaldi")
    }

    @Test
    fun silinenHisseninKotasyonuTahtadanDUSER() = runTest {
        val (repo, db) = harness()
        db.cache("stock_asels.is", AssetClass.Stock)
        repo.upsertPosition(position("pos_stock_asels.is", AssetClass.Stock))
        repo.addTransaction(buy("tx-1", "pos_stock_asels.is", 40.0))

        repo.deletePosition("pos_stock_asels.is")

        assertTrue("stock_asels.is" !in db.cachedKeys(), "silinen hisse tahtada kaldi")
    }

    /**
     * ASIL AYRIM. Temizlik fon ve hisseye ozeldir; altin/gumus/doviz tahtada
     * her zaman durur, hic tutulmasalar bile. Piyasa ekrani "bugun ne kadar"
     * sorusunu bu satirlarla yanitliyor.
     */
    @Test
    fun altinVeDovizTahtadaKALIR() = runTest {
        val (repo, db) = harness()
        db.cache("gold_quarter", AssetClass.Gold)
        db.cache("usd_try", AssetClass.Fx)
        db.cache("silver_gram", AssetClass.Silver)
        db.cache("fund_afa", AssetClass.Fund)

        repo.upsertPosition(position("pos_gold_quarter", AssetClass.Gold))
        repo.addTransaction(buy("tx-1", "pos_gold_quarter", 3.0))
        repo.deletePosition("pos_gold_quarter")

        val keys = db.cachedKeys()
        assertTrue("gold_quarter" in keys, "altin tahtadan dustu")
        assertTrue("usd_try" in keys, "doviz tahtadan dustu")
        assertTrue("silver_gram" in keys, "gumus tahtadan dustu")
        // Hic tutulmamis fon da temizlenir: tahtada olmasinin sebebi yok.
        assertTrue("fund_afa" !in keys)
    }

    /**
     * Tamami SATILAN fon da tutulmuyor sayilir - olcu selectHeldFundIds ile
     * ayni: miktar sifirsa tutulmuyordur. Pozisyon satiri duruyor olabilir
     * ("daha once tanimlanmis miydi" kontrolu icin) ama tahtada yeri yok.
     */
    @Test
    fun tamamiSatilanFonTahtadanDUSER() = runTest {
        val (repo, db) = harness()
        db.cache("fund_afa", AssetClass.Fund)
        repo.upsertPosition(position("pos_fund_afa", AssetClass.Fund))
        repo.addTransaction(buy("tx-1", "pos_fund_afa", 5.0))
        assertTrue("fund_afa" in db.cachedKeys())

        repo.addTransaction(
            buy("tx-2", "pos_fund_afa", 5.0).copy(side = TradeSide.Sell),
        )

        // Miktar sifirlandi: varlik listesinden de duser.
        assertTrue(repo.observePositions().first().isEmpty())
        assertTrue("fund_afa" !in db.cachedKeys(), "sifirlanan fon tahtada kaldi")
    }

    /** Tutuluyorken hicbir sey silinmez - portfoy yazmasi tahtayi bozmamali. */
    @Test
    fun tutulanFonYazmalardaKORUNUR() = runTest {
        val (repo, db) = harness()
        db.cache("fund_afa", AssetClass.Fund)
        repo.upsertPosition(position("pos_fund_afa", AssetClass.Fund))
        repo.addTransaction(buy("tx-1", "pos_fund_afa", 5.0))
        repo.addTransaction(buy("tx-2", "pos_fund_afa", 2.0))
        repo.deleteTransaction("tx-2")

        assertTrue("fund_afa" in db.cachedKeys(), "tutulan fon tahtadan dustu")
    }
}

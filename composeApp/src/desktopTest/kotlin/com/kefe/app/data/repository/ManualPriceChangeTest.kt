package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.todayChangePercent
import com.kefe.app.domain.model.valuedAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * ELLE GIRILEN fiyatin gunluk hareketi YOKTUR.
 *
 * Kural ILERLEME §35'te acik. Ama elle fiyat bindirmesi yalniz hafta/ay
 * degisimini null'luyor, `changePercent` ile `quoteDate`'i onbellekteki KAYNAK
 * satirindan aynen birakiyordu. Onbellek o gun tazelendiyse - uygulama acilinca
 * olagan - quoteDate == bugun oluyor, Valuation'daki kapi aciliyor ve KAYNAGIN
 * yuzdesi kullanicinin girdigi degere uygulanip "bugunku getiri"ye giriyordu.
 *
 * 100 gr altin elle ₺6.500 girildiginde +₺13.618 sahte gunluk getiri.
 */
private class ManualStubRemote(private val prices: List<Price>) : PriceRemoteDataSource {
    override suspend fun fetchPrices(): List<Price> = prices
}

private val ManualBugun = KefeDate(2026, 8, 3)

private class ManualStubClock : KefeClock {
    override fun today(): KefeDate = ManualBugun
    override fun nowEpochMillis(): Long = 1_000_000L
}

class ManualPriceChangeTest {

    private fun harness(): SqlDelightPriceRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        // Kaynak BUGUNE ait bir kotasyon veriyor: +%2,14 ile.
        val source = Price(
            assetKey = "gold_gram",
            label = "Gram Altın",
            bid = 6_400.0,
            ask = 6_400.0,
            changePercent = 2.14,
            timestamp = "10:00",
            source = PriceSource.FreeMarket,
            assetClass = AssetClass.Gold,
            quoteDate = ManualBugun,
        )
        return SqlDelightPriceRepository(createKefeDatabase(driver), ManualStubRemote(listOf(source)), ManualStubClock())
    }

    /** ASIL KURAL: elle fiyat kaynagin yuzdesini TASIMAZ. */
    @Test
    fun elleFiyatKaynaginYUZDESINITasimaz() = runTest {
        val repo = harness()
        repo.refresh()
        repo.setManualPrice("gold_gram", 6_500.0)

        val price = repo.observePrices().first().prices.single { it.assetKey == "gold_gram" }
        assertEquals(6_500.0, price.ask, 1e-9)
        assertNull(price.changePercent, "kaynagin yuzdesi elle fiyata tasindi")
        assertNull(price.quoteDate, "kaynagin kotasyon gunu elle fiyatta kaldi")
    }

    /** Portfoy katkisi SIFIR olmali - kural budur. */
    @Test
    fun elleFiyatliPozisyonunGunlukKatkisiSIFIR() = runTest {
        val repo = harness()
        repo.refresh()
        repo.setManualPrice("gold_gram", 6_500.0)

        val price = repo.observePrices().first().prices.single { it.assetKey == "gold_gram" }
        val position = Position(
            id = "pos_gold_gram",
            name = "Gram",
            assetClass = AssetClass.Gold,
            subtype = GoldSubtype.Gram,
            quantity = 100.0,
            unit = QuantityUnit.Gram,
            unitPrice = 6_400.0,
            value = 640_000.0,
            cost = 500_000.0,
        ).valuedAt(price, ManualBugun)

        assertEquals(650_000.0, position.value, 1e-9)
        assertEquals(0.0, position.dailyChangePercent!!, 1e-9)
        assertEquals(0.0, price.todayChangePercent(ManualBugun)!!, 1e-9)
    }

    /** Elle fiyat KALDIRILINCA kaynagin rakami geri gelir. */
    @Test
    fun elleFiyatKalkincaKaynakGERIGelir() = runTest {
        val repo = harness()
        repo.refresh()
        repo.setManualPrice("gold_gram", 6_500.0)
        repo.clearManualPrice("gold_gram")

        val price = repo.observePrices().first().prices.single { it.assetKey == "gold_gram" }
        assertEquals(6_400.0, price.ask, 1e-9)
        assertEquals(2.14, price.changePercent!!, 1e-9)
        assertEquals(ManualBugun, price.quoteDate)
    }
}

package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Testlerde tekrar eden alanlari gizleyen kisa yardimci. */
private fun tx(
    id: String,
    date: KefeDate,
    side: TradeSide,
    quantity: Double,
    unitPrice: Double,
    fee: Double = 0.0,
): Transaction = Transaction(
    id = id,
    positionId = "pos-1",
    date = date,
    side = side,
    quantity = quantity,
    unitPrice = unitPrice,
    fee = fee,
    addedByMemberId = "uye-1",
)

private const val EPS = 1e-9

class CostBasisTest {

    @Test
    fun bosListeEmptyDoner() {
        assertEquals(CostBasis.Empty, emptyList<Transaction>().costBasis())
    }

    @Test
    fun siraliAlimlardaAgirlikliOrtalama() {
        // 2 x 100 = 200, 3 x 200 = 600 -> 5 adet, 800 maliyet, 160 ortalama
        val result = listOf(
            tx("1", KefeDate(2025, 1, 1), TradeSide.Buy, quantity = 2.0, unitPrice = 100.0),
            tx("2", KefeDate(2025, 2, 1), TradeSide.Buy, quantity = 3.0, unitPrice = 200.0),
        ).costBasis()

        assertEquals(5.0, result.quantity, EPS)
        assertEquals(800.0, result.totalCost, EPS)
        assertEquals(160.0, result.averageCost, EPS)
        assertEquals(0.0, result.realizedProfit, EPS)
        assertEquals(800.0, result.grossInvested, EPS)
    }

    @Test
    fun iscilikAlistaMaliyeteEklenir() {
        val result = listOf(
            tx("1", KefeDate(2025, 1, 1), TradeSide.Buy, quantity = 1.0, unitPrice = 100.0, fee = 20.0),
        ).costBasis()

        assertEquals(1.0, result.quantity, EPS)
        assertEquals(120.0, result.totalCost, EPS)
        assertEquals(120.0, result.averageCost, EPS)
        assertEquals(120.0, result.grossInvested, EPS)
    }

    @Test
    fun iscilikSatistaHasilattanDuser() {
        // 2 x 100 al (maliyet 200) -> 1 adet 150'ye sat, 10 iscilik
        // kesinlesen kar = (150 - 10) - 100 = 40
        val result = listOf(
            tx("1", KefeDate(2025, 1, 1), TradeSide.Buy, quantity = 2.0, unitPrice = 100.0),
            tx("2", KefeDate(2025, 2, 1), TradeSide.Sell, quantity = 1.0, unitPrice = 150.0, fee = 10.0),
        ).costBasis()

        assertEquals(40.0, result.realizedProfit, EPS)
        assertEquals(1.0, result.quantity, EPS)
        assertEquals(100.0, result.totalCost, EPS)
        assertEquals(100.0, result.averageCost, EPS)
    }

    @Test
    fun tamCikistaMiktarVeMaliyetSifirlanir() {
        // Kayan nokta artigi kalmamali: 0.1 + 0.2 gibi degerlerle bile tam sifir
        val result = listOf(
            tx("1", KefeDate(2025, 1, 1), TradeSide.Buy, quantity = 0.1, unitPrice = 1000.0),
            tx("2", KefeDate(2025, 2, 1), TradeSide.Buy, quantity = 0.2, unitPrice = 1100.0),
            tx("3", KefeDate(2025, 3, 1), TradeSide.Sell, quantity = 0.3, unitPrice = 1200.0),
        ).costBasis()

        assertEquals(0.0, result.quantity)
        assertEquals(0.0, result.totalCost)
        assertEquals(0.0, result.averageCost)
    }

    @Test
    fun islemlerTarihSirasinaGoreIslenir() {
        val karisik = listOf(
            tx("3", KefeDate(2025, 3, 1), TradeSide.Sell, quantity = 1.0, unitPrice = 150.0, fee = 10.0),
            tx("1", KefeDate(2025, 1, 1), TradeSide.Buy, quantity = 2.0, unitPrice = 100.0),
            tx("2", KefeDate(2025, 2, 1), TradeSide.Buy, quantity = 3.0, unitPrice = 200.0),
        )
        val sirali = karisik.sortedBy { it.date.toEpochDay() }

        // Sirali islenince: 800 maliyet / 5 adet -> ortalama 160
        // 1 adet 150'ye satilir, 10 iscilik: 140 - 160 = -20 zarar
        val result = karisik.costBasis()

        assertEquals(sirali.costBasis(), result)
        assertEquals(4.0, result.quantity, EPS)
        assertEquals(640.0, result.totalCost, EPS)
        assertEquals(160.0, result.averageCost, EPS)
        assertEquals(-20.0, result.realizedProfit, EPS)
    }

    @Test
    fun firstBuyIlkAlimTarihiniVerir() {
        val result = listOf(
            tx("3", KefeDate(2025, 5, 20), TradeSide.Sell, quantity = 1.0, unitPrice = 150.0),
            tx("2", KefeDate(2025, 4, 10), TradeSide.Buy, quantity = 3.0, unitPrice = 200.0),
            tx("1", KefeDate(2025, 2, 15), TradeSide.Buy, quantity = 2.0, unitPrice = 100.0),
        ).costBasis()

        assertEquals(KefeDate(2025, 2, 15), result.firstBuy)
        assertEquals(KefeDate(2025, 5, 20), result.lastTransaction)
    }

    @Test
    fun sadeceSatisVarsaFirstBuyNull() {
        val result = listOf(
            tx("1", KefeDate(2025, 2, 15), TradeSide.Sell, quantity = 1.0, unitPrice = 150.0),
        ).costBasis()

        assertNull(result.firstBuy)
    }

    @Test
    fun unrealizedProfitOrtalamaFarkiCarpiMiktar() {
        val result = listOf(
            tx("1", KefeDate(2025, 1, 1), TradeSide.Buy, quantity = 2.0, unitPrice = 100.0),
            tx("2", KefeDate(2025, 2, 1), TradeSide.Buy, quantity = 3.0, unitPrice = 200.0),
        ).costBasis()

        // ortalama 160, miktar 5 -> (200 - 160) * 5 = 200
        assertEquals(200.0, result.unrealizedProfit(200.0), EPS)
        assertEquals((150.0 - result.averageCost) * result.quantity, result.unrealizedProfit(150.0), EPS)
        assertEquals(1000.0, result.currentValue(200.0), EPS)
    }
}

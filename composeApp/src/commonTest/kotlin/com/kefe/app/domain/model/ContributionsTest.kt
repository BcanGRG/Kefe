package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EPS = 1e-9

private fun pos(id: String, assetClass: AssetClass): Position = Position(
    id = id,
    name = id,
    assetClass = assetClass,
    quantity = 1.0,
    unit = QuantityUnit.Piece,
    unitPrice = 0.0,
    value = 0.0,
    cost = 0.0,
)

private fun tx(
    id: String,
    positionId: String,
    date: KefeDate,
    side: TradeSide,
    quantity: Double,
    unitPrice: Double,
    fee: Double = 0.0,
): Transaction = Transaction(
    id = id,
    positionId = positionId,
    date = date,
    side = side,
    quantity = quantity,
    unitPrice = unitPrice,
    fee = fee,
    addedByMemberId = "uye-1",
)

private val today = KefeDate(2026, 7, 28)

class ContributionsTest {

    @Test
    fun pencereBoyuncaHerAyDoner() {
        val months = monthlyContributions(emptyList(), emptyList(), months = 12, today = today)

        assertEquals(12, months.size)
        // En eski Ağustos 2025, en yeni Temmuz 2026.
        assertEquals(KefeDate(2025, 8, 1), months.first().date.copy(day = 1))
        assertEquals(2026, months.last().date.year)
        assertEquals(7, months.last().date.month)
        // Islem yoksa her ay bostur - grafikte bosluk olarak gorunur.
        assertTrue(months.all { it.isEmpty })
    }

    @Test
    fun alimKomisyonuylaBirlikteSayilir() {
        // Cebinden cikan para 3 x 10.000 + 250 komisyon.
        val months = monthlyContributions(
            transactions = listOf(
                tx("1", "p1", KefeDate(2026, 7, 5), TradeSide.Buy, 3.0, 10_000.0, fee = 250.0),
            ),
            positions = listOf(pos("p1", AssetClass.Gold)),
            months = 12,
            today = today,
        )

        assertEquals(30_250.0, months.last().total, EPS)
    }

    @Test
    fun satisHasilatiKatkidanDuser() {
        // Ayni ay 30.000 alim, 12.000 satis (100 komisyon hasilattan duser)
        // -> net katki 30.000 - 11.900 = 18.100
        val months = monthlyContributions(
            transactions = listOf(
                tx("1", "p1", KefeDate(2026, 7, 5), TradeSide.Buy, 3.0, 10_000.0),
                tx("2", "p1", KefeDate(2026, 7, 20), TradeSide.Sell, 1.2, 10_000.0, fee = 100.0),
            ),
            positions = listOf(pos("p1", AssetClass.Gold)),
            months = 12,
            today = today,
        )

        assertEquals(18_100.0, months.last().total, EPS)
    }

    @Test
    fun netCikislaKapananSinifKatkiSayilmaz() {
        // Altin net eksi, fon arti: yalniz fon dilimi kalir. Eksi bir dilim
        // "bu kadar biriktirdim" tablosunda yer alamaz.
        val months = monthlyContributions(
            transactions = listOf(
                tx("1", "p1", KefeDate(2026, 7, 5), TradeSide.Sell, 2.0, 10_000.0),
                tx("2", "p2", KefeDate(2026, 7, 6), TradeSide.Buy, 1.0, 5_000.0),
            ),
            positions = listOf(pos("p1", AssetClass.Gold), pos("p2", AssetClass.Fund)),
            months = 12,
            today = today,
        )

        val last = months.last()
        assertEquals(1, last.slices.size)
        assertEquals(AssetClass.Fund, last.slices.single().assetClass)
        assertEquals(5_000.0, last.total, EPS)
    }

    @Test
    fun pencereDisindakiIslemSayilmaz() {
        // 3 ay pencere: Mayıs-Temmuz 2026. Ocak'taki alim disarida kalir.
        val months = monthlyContributions(
            transactions = listOf(
                tx("1", "p1", KefeDate(2026, 1, 5), TradeSide.Buy, 1.0, 99_000.0),
                tx("2", "p1", KefeDate(2026, 6, 5), TradeSide.Buy, 1.0, 4_000.0),
            ),
            positions = listOf(pos("p1", AssetClass.Gold)),
            months = 3,
            today = today,
        )

        assertEquals(3, months.size)
        assertEquals(0.0, months[0].total, EPS)
        assertEquals(4_000.0, months[1].total, EPS)
        assertEquals(0.0, months[2].total, EPS)
    }

    @Test
    fun bilinmeyenPozisyonunIslemiAtlanir() {
        // Silinmis pozisyona ait islem sinifsiz kalir; toplama katilamaz.
        val months = monthlyContributions(
            transactions = listOf(
                tx("1", "silinmis", KefeDate(2026, 7, 5), TradeSide.Buy, 1.0, 10_000.0),
            ),
            positions = emptyList(),
            months = 12,
            today = today,
        )

        assertTrue(months.all { it.isEmpty })
    }

    @Test
    fun yilSiniriniAsanPencere() {
        // Aralik -> Ocak gecisi: ay sirasi yil degisiminde kirilmamali.
        val months = monthlyContributions(
            transactions = emptyList(),
            positions = emptyList(),
            months = 3,
            today = KefeDate(2026, 1, 15),
        )

        assertEquals(listOf(11, 12, 1), months.map { it.date.month })
        assertEquals(listOf(2025, 2025, 2026), months.map { it.date.year })
    }
}

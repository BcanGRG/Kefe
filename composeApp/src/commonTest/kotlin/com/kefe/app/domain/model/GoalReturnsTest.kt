package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EPS = 1e-9

private fun position(
    id: String,
    assetClass: AssetClass = AssetClass.Gold,
    quantity: Double = 10.0,
    unitPrice: Double = 100.0,
    cost: Double = 800.0,
    dailyChangePercent: Double = 0.0,
): Position = Position(
    id = id,
    name = id,
    assetClass = assetClass,
    quantity = quantity,
    unit = QuantityUnit.Piece,
    unitPrice = unitPrice,
    value = quantity * unitPrice,
    cost = cost,
    dailyChangePercent = dailyChangePercent,
)

private fun asset(position: Position, quantity: Double = GoalAssignment.WholePosition) =
    GoalAsset(position, GoalAssignment("goal_ev", quantity))

/**
 * Hedefe ozel getiri rakamlari.
 *
 * Ozet ekranindakiyle AYNI tanimlar, farkli kapsam: yalniz hedefe ATANAN
 * kisimlar sayilir. Kismi atamada hem deger hem maliyet ayni oranda alinmali -
 * biri tam biri kismi olursa hedefin getirisi uydurma cikar.
 */
class GoalReturnsTest {

    // --- Atanan kismin maliyeti ---

    @Test
    fun tamAtamadaMaliyetPozisyonunAYNISI() {
        val a = asset(position("pos_gold", quantity = 10.0, cost = 800.0))
        assertEquals(800.0, a.cost, EPS)
        assertEquals(1_000.0, a.value, EPS)
        assertEquals(200.0, a.profit, EPS)
    }

    /**
     * ASIL INCELIK. 10 ceyregin 6'si hedefteyse maliyet de 6/10'udur. Tam
     * maliyeti kullanmak hedefin getirisini oldugundan DUSUK gosterirdi:
     * ₺600 deger ile ₺800 maliyet, gercekte kazandiran bir hedefi zararda
     * gosterir.
     */
    @Test
    fun kismiAtamadaMaliyetDE_orantilanir() {
        val a = asset(position("pos_gold", quantity = 10.0, cost = 800.0), quantity = 6.0)
        assertEquals(600.0, a.value, EPS)
        assertEquals(480.0, a.cost, EPS)
        assertEquals(120.0, a.profit, EPS)
    }

    @Test
    fun miktarsizPozisyondaSifiraBOLUNMEZ() {
        val a = asset(position("pos_gold", quantity = 0.0, cost = 800.0))
        assertEquals(0.0, a.cost, EPS)
        assertEquals(0.0, a.value, EPS)
    }

    // --- Toplam getiri ---

    @Test
    fun toplamGetiriMALIYETE_oranlanir() {
        val assets = listOf(
            asset(position("pos_a", quantity = 10.0, unitPrice = 100.0, cost = 800.0)),
            asset(position("pos_b", quantity = 5.0, unitPrice = 100.0, cost = 400.0)),
        )
        val total = assets.totalReturn()!!
        // Deger 1.500, maliyet 1.200 -> +300, %25.
        assertEquals(300.0, total.amount, EPS)
        assertEquals(25.0, total.percent, EPS)
    }

    /** Maliyeti olmayan hedefte oran YOK - sifira bolmek yerine "—". */
    @Test
    fun maliyetsizHedefNullDoner() {
        assertNull(listOf(asset(position("pos_a", cost = 0.0))).totalReturn())
        assertNull(emptyList<GoalAsset>().totalReturn())
    }

    // --- Bugunku degisim ---

    /**
     * Yuzdeler ORTALANMAZ, degere gore agirliklanir: kucuk bir kalemin %10'u
     * ile buyuk bir kalemin %1'i ayni agirlikta degildir.
     */
    @Test
    fun bugunkuDegisimDEGERE_gore_agirliklanir() {
        val assets = listOf(
            // 10.000 TL, +%1 -> gun basi 9.900,99 ; fark +99,01
            asset(position("pos_buyuk", quantity = 100.0, unitPrice = 100.0, dailyChangePercent = 1.0)),
            // 100 TL, +%10 -> gun basi 90,909 ; fark +9,09
            asset(position("pos_kucuk", quantity = 1.0, unitPrice = 100.0, dailyChangePercent = 10.0)),
        )
        val change = assets.todayChange()!!
        // Duz ortalama %5,5 olurdu; agirlikli sonuc %1'e cok daha yakin.
        assertTrue(change.percent < 1.2, "beklenen ~%1, gelen ${change.percent}")
        assertEquals(108.10, change.amount, 0.01)
    }

    /**
     * Piyasa kapaliyken butun yuzdeler sifirdir (bkz. Price.todayChangePercent)
     * ve hedefin bugunku degisimi de sifir cikar. Bu bir eksiklik DEGIL,
     * dogru cevap: hedefin degeri bugun oynamadi.
     */
    @Test
    fun kapaliGunHedefDegisimiSIFIR() {
        val assets = listOf(asset(position("pos_a", dailyChangePercent = 0.0)))
        val change = assets.todayChange()!!
        assertEquals(0.0, change.amount, EPS)
        assertEquals(0.0, change.percent, EPS)
    }

    @Test
    fun bosHedefNullDoner() {
        assertNull(emptyList<GoalAsset>().todayChange())
    }

    // --- Dagilim ---

    @Test
    fun dagilimATANAN_kisimlardan_turer() {
        val assets = listOf(
            // 10 adetin 6'si atanmis -> hedefe 600 TL duser, 1.000 degil.
            asset(position("pos_gold", AssetClass.Gold, quantity = 10.0, unitPrice = 100.0), 6.0),
            asset(position("pos_fund", AssetClass.Fund, quantity = 4.0, unitPrice = 100.0)),
        )
        val allocation = assets.allocation()

        assertEquals(2, allocation.size)
        // Buyukten kucuge sirali.
        assertEquals(AssetClass.Gold, allocation[0].assetClass)
        assertEquals(600.0, allocation[0].value, EPS)
        assertEquals(60.0, allocation[0].percent, EPS)
        assertEquals(400.0, allocation[1].value, EPS)
        assertEquals(40.0, allocation[1].percent, EPS)
    }

    @Test
    fun ayniSinifTEK_dilimde_toplanir() {
        val assets = listOf(
            asset(position("pos_ceyrek", AssetClass.Gold, quantity = 1.0, unitPrice = 100.0)),
            asset(position("pos_gram", AssetClass.Gold, quantity = 1.0, unitPrice = 300.0)),
        )
        val allocation = assets.allocation()
        assertEquals(1, allocation.size)
        assertEquals(400.0, allocation[0].value, EPS)
        assertEquals(100.0, allocation[0].percent, EPS)
    }

    @Test
    fun degersizHedefteDagilimBOS() {
        assertTrue(emptyList<GoalAsset>().allocation().isEmpty())
        assertTrue(listOf(asset(position("pos_a", quantity = 0.0))).allocation().isEmpty())
    }
}

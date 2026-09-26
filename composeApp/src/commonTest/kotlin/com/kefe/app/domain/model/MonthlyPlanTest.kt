package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aylik planin defterden turetilen durumu.
 *
 * Plan saklanan tek seydir; "yapildi mi" her seferinde islemlerden hesaplanir.
 * Buradaki testler o hesabin kurallarini sabitliyor: brut alim, satisin orani
 * dusurmemesi, %100 tavan, TL agirligi ve ay sinirlari.
 */
class MonthlyPlanTest {

    private val october = YearMonth(2026, 10)
    private val midOctober = KefeDate(2026, 10, 15)

    @Test
    fun miktarSatiriAlimMiktariylaOlculur() {
        val p = progress(
            items = listOf(gramItem(10.0)),
            txs = listOf(planBuy(gram, 6.0, 6700.0)),
        )
        val line = p.items.single()
        assertEquals(6.0, line.actual)
        assertEquals(0.6, line.ratio, 1e-9)
        assertEquals(4.0, line.remaining, 1e-9)
        assertEquals(PlanItemStatus.Partial, line.status)
    }

    /** Satis orani DUSURMEZ - "10 gr alacaktim, aldim mi" sorusunun cevabi degismez. */
    @Test
    fun satisPlaniGeriAlmaz() {
        val p = progress(
            items = listOf(gramItem(10.0)),
            txs = listOf(planBuy(gram, 10.0, 6700.0), planSell(gram, 1.0, 6600.0)),
        )
        assertEquals(PlanItemStatus.Done, p.items.single().status)
        assertEquals(1.0, p.items.single().soldQuantity)
        assertEquals(6600.0, p.soldTl)
    }

    /** Tutar satiri komisyon DAHIL olculur: cepten cikan para. */
    @Test
    fun tutarSatiriKomisyonuSayar() {
        val item = PlanItem("i", october, "fund_afa", "AFA", PlanTargetMode.Amount, 3000.0)
        val p = progress(listOf(item), listOf(planBuy(fund, 100.0, 29.5, fee = 50.0)))
        assertEquals(3000.0, p.items.single().actual, 1e-9)
        assertEquals(PlanItemStatus.Done, p.items.single().status)
    }

    /** Kayan nokta: 0.7+0.1+0.1+0.1 < 1.0 olabilir; bu "Tamam"i kacirmamali. */
    @Test
    fun kesirToplamiTamamiKACIRMAZ() {
        val p = progress(
            listOf(gramItem(1.0)),
            listOf(planBuy(gram, 0.7, 1.0), planBuy(gram, 0.1, 1.0), planBuy(gram, 0.1, 1.0), planBuy(gram, 0.1, 1.0)),
        )
        assertEquals(PlanItemStatus.Done, p.items.single().status)
    }

    @Test
    fun asimOraniTasirmazEkstraOlurGorunur() {
        val p = progress(listOf(gramItem(10.0)), listOf(planBuy(gram, 12.0, 6700.0)))
        val line = p.items.single()
        assertEquals(1.0, line.ratio)
        assertEquals(2.0, line.over, 1e-9)
        val extra = p.extras.single()
        assertEquals(ExtraKind.OverPlan, extra.kind)
        assertEquals(2.0, extra.quantity!!, 1e-9)
        assertEquals(13400.0, extra.tl, 1e-6)
    }

    /** Tam eslesme: gram plani varken alinan ceyrek plan DISIDIR. */
    @Test
    fun farkliFormPlanDisi() {
        val p = progress(listOf(gramItem(10.0)), listOf(planBuy(quarter, 2.0, 11000.0)))
        assertEquals(PlanItemStatus.Waiting, p.items.single().status)
        val extra = p.extras.single()
        assertEquals(ExtraKind.Unplanned, extra.kind)
        assertEquals("gold_quarter", extra.assetKey)
        // Eldeki pozisyonun adi - kullanicinin gordugu ad odur.
        assertEquals("Çeyrek", extra.name)
    }

    /** Eski kimlikli pozisyonun alimi da dogru satira sayilir. */
    @Test
    fun eskiKimlikliPozisyonPlanaSayilir() {
        val legacy = planPosition("pos_gumus", AssetClass.Silver, name = "Gram Gümüş")
        val item = PlanItem("i", october, SilverAssetKey, "Gram Gümüş", PlanTargetMode.Quantity, 5.0)
        val p = monthPlanProgress(october, listOf(item), listOf(planBuy(legacy, 5.0, 80.0)), listOf(legacy), midOctober)
        assertEquals(PlanItemStatus.Done, p.items.single().status)
    }

    /** Baska aya dusen alim bu ayi etkilemez; ay isleme tarihine gore secilir. */
    @Test
    fun baskaAyinAlimiSAYILMAZ() {
        val p = progress(listOf(gramItem(10.0)), listOf(planBuy(gram, 10.0, 6700.0, date = KefeDate(2026, 9, 30))))
        assertEquals(0.0, p.items.single().actual)
    }

    @Test
    fun durumlarAyinZamaninaGore() {
        val item = gramItem(10.0)
        val past = monthPlanProgress(october, listOf(item), emptyList(), positions, KefeDate(2026, 11, 2))
        val now = monthPlanProgress(october, listOf(item), emptyList(), positions, midOctober)
        val future = monthPlanProgress(october, listOf(item), emptyList(), positions, KefeDate(2026, 9, 20))
        assertEquals(PlanItemStatus.Missed, past.items.single().status)
        assertEquals(MonthVerdict.Missed, past.verdict)
        assertEquals(PlanItemStatus.Waiting, now.items.single().status)
        assertEquals(MonthVerdict.InProgress, now.verdict)
        assertEquals(PlanItemStatus.Upcoming, future.items.single().status)
        assertEquals(MonthVerdict.Future, future.verdict)
    }

    /**
     * Skor TL agirlikli: 10 gr altinin (67.000) yarisi ile 500 TL fonun tamami
     * esit agirlikta olamaz. Duz ortalama %75 derdi; dogrusu ~%50,4.
     */
    @Test
    fun skorTLAgirlikli() {
        val items = listOf(
            gramItem(10.0, price = 6700.0),
            PlanItem("f", october, "fund_afa", "AFA", PlanTargetMode.Amount, 500.0),
        )
        val p = progress(items, listOf(planBuy(gram, 5.0, 6700.0), planBuy(fund, 10.0, 50.0)))
        val expected = (67000.0 * 0.5 + 500.0 * 1.0) / 67500.0
        assertEquals(expected, p.score!!, 1e-9)
        assertEquals(67500.0, p.plannedTl, 1e-6)
    }

    /** Anlik fiyat goruntusu yoksa eldeki varligin guncel fiyati agirliktir. */
    @Test
    fun anlikFiyatYoksaGuncelFiyat() {
        val p = progress(listOf(gramItem(10.0, price = null)), emptyList())
        assertEquals(10.0 * gram.unitPrice, p.items.single().plannedTl!!, 1e-6)
    }

    /** Hic fiyat bulunamayan satir agirliksiz kalmaz - yoksa ay yanlislikla "tam" cikardi. */
    @Test
    fun fiyatsizSatirSkorDISINDAKALMAZ() {
        val items = listOf(
            PlanItem("x", october, "gold_ata", "Ata", PlanTargetMode.Quantity, 2.0),
            PlanItem("f", october, "fund_afa", "AFA", PlanTargetMode.Amount, 1000.0),
        )
        val p = progress(items, listOf(planBuy(fund, 10.0, 100.0)))
        assertNull(p.items.first().plannedTl)
        assertEquals(0.5, p.score!!, 1e-9)
        assertEquals(MonthVerdict.InProgress, p.verdict)
    }

    @Test
    fun yuzdeSeksenDuzenliSayilir() {
        val p = monthPlanProgress(
            october,
            listOf(gramItem(10.0)),
            listOf(planBuy(gram, 8.0, 6700.0)),
            positions,
            KefeDate(2026, 11, 3),
        )
        assertEquals(MonthVerdict.Partial, p.verdict)
        assertTrue(p.isRegular)
    }

    @Test
    fun planYoksaSkorYok() {
        val p = progress(emptyList(), listOf(planBuy(gram, 1.0, 6700.0)))
        assertEquals(MonthVerdict.NoPlan, p.verdict)
        assertNull(p.score)
        assertEquals(1, p.extras.size)
    }

    @Test
    fun onerilenMiktarKalanKadar() {
        val p = progress(listOf(gramItem(10.0)), listOf(planBuy(gram, 6.0, 6700.0)))
        assertEquals(4.0, p.items.single().prefillQuantity()!!, 1e-9)
        val fundLine = progress(
            listOf(PlanItem("f", october, "fund_afa", "AFA", PlanTargetMode.Amount, 3000.0)),
            emptyList(),
        ).items.single()
        assertNull(fundLine.prefillQuantity(), "fon payi uydurulmaz")
    }

    @Test
    fun seceneklerOnceEldekilerSonraKatalog() {
        val options = planAssetOptions(positions)
        assertEquals(true, options.first().held)
        assertEquals(1, options.count { it.assetKey == "gold_gram" })
        assertTrue(options.any { it.assetKey == "usd_try" && !it.held })
    }

    // --- yardimcilar ---------------------------------------------------------

    private val gram = planPosition("pos_gold_gram", AssetClass.Gold, GoldSubtype.Gram, Karat.K24, unitPrice = 6650.0)
    private val quarter = planPosition("pos_gold_quarter", AssetClass.Gold, GoldSubtype.Quarter, name = "Çeyrek")
    private val fund = planPosition("pos_fund_afa", AssetClass.Fund, name = "AFA · Ak Portföy Altın")
    private val positions = listOf(gram, quarter, fund)

    private fun progress(items: List<PlanItem>, txs: List<Transaction>) =
        monthPlanProgress(october, items, txs, positions, midOctober)

    private fun gramItem(target: Double, price: Double? = 6700.0) = PlanItem(
        id = planItemId(october, "gold_gram"),
        month = october,
        assetKey = "gold_gram",
        assetName = "Gram Altın",
        mode = PlanTargetMode.Quantity,
        target = target,
        unitPriceAtPlan = price,
    )
}

internal fun planPosition(
    id: String,
    assetClass: AssetClass,
    subtype: GoldSubtype? = null,
    karat: Karat? = null,
    name: String = id,
    quantity: Double = 10.0,
    unitPrice: Double = 100.0,
) = Position(
    id = id,
    name = name,
    assetClass = assetClass,
    subtype = subtype,
    karat = karat,
    quantity = quantity,
    unit = QuantityUnit.Gram,
    unitPrice = unitPrice,
    value = quantity * unitPrice,
    cost = quantity * unitPrice,
)

private var txSeq = 0

internal fun planBuy(
    position: Position,
    quantity: Double,
    unitPrice: Double,
    fee: Double = 0.0,
    date: KefeDate = KefeDate(2026, 10, 10),
) = Transaction(
    id = "tx${txSeq++}",
    positionId = position.id,
    date = date,
    side = TradeSide.Buy,
    quantity = quantity,
    unitPrice = unitPrice,
    fee = fee,
    addedByMemberId = "member_owner",
)

internal fun planSell(
    position: Position,
    quantity: Double,
    unitPrice: Double,
    date: KefeDate = KefeDate(2026, 10, 12),
) = planBuy(position, quantity, unitPrice, date = date).copy(side = TradeSide.Sell)

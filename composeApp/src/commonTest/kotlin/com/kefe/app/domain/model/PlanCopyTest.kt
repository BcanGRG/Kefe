package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Gecen ayi kopyala": devir yalniz secilen satirlara, kendiliginden degil. */
class PlanCopyTest {

    private val gram = planPosition("pos_gold_gram", AssetClass.Gold, GoldSubtype.Gram, Karat.K24)
    private val sep = YearMonth(2026, 9)
    private val oct = YearMonth(2026, 10)
    private val today = KefeDate(2026, 10, 2)

    private fun item(month: YearMonth, key: String, target: Double, mode: PlanTargetMode = PlanTargetMode.Quantity) =
        PlanItem(planItemId(month, key), month, key, catalogName(key), mode, target, goalId = "goal_ev")

    private val sepItems = listOf(item(sep, "gold_gram", 10.0), item(sep, SilverAssetKey, 5.0))
    private val sepProgress = monthPlanProgress(
        sep,
        sepItems,
        listOf(planBuy(gram, 6.0, 100.0, date = KefeDate(2026, 9, 10))),
        listOf(gram),
        today,
    )

    @Test
    fun kaynakEnYakinOncekiAy() {
        assertEquals(sep, copySourceFor(oct, sepItems + item(YearMonth(2026, 7), "gold_gram", 1.0)))
    }

    /** Varsayilan: devir YOK - hedefler aynen kopyalanir. */
    @Test
    fun devirSecilmezseHedefAynen() {
        val out = planCopyDraft(sepProgress, oct, emptyList(), today).toItems(oct, emptySet()) { null }
        assertEquals(10.0, out.first { it.assetKey == "gold_gram" }.target)
        assertEquals(5.0, out.first { it.assetKey == SilverAssetKey }.target)
        assertTrue(out.all { it.month == oct && it.id.startsWith("pi_2026_10_") })
        assertEquals("goal_ev", out.first().goalId)
    }

    @Test
    fun secilenEksikTasinir() {
        val draft = planCopyDraft(sepProgress, oct, emptyList(), today)
        assertEquals(4.0, draft.first { it.assetKey == "gold_gram" }.shortfall, 1e-9)
        val out = draft.toItems(oct, setOf("gold_gram")) { null }
        assertEquals(14.0, out.first { it.assetKey == "gold_gram" }.target, 1e-9)
    }

    /** Hedef ayda zaten planli satir devir secilmedikce DOKUNULMAZ. */
    @Test
    fun zatenPlanliSatiraDokunulmaz() {
        val existing = listOf(item(oct, "gold_gram", 20.0))
        val draft = planCopyDraft(sepProgress, oct, existing, today)
        assertEquals(1, draft.toItems(oct, emptySet()) { null }.size)
        val carried = draft.toItems(oct, setOf("gold_gram")) { null }.first { it.assetKey == "gold_gram" }
        assertEquals(24.0, carried.target, 1e-9)
    }

    @Test
    fun kaynakAyBitmediyseGecici() {
        val octProgress = monthPlanProgress(oct, listOf(item(oct, "gold_gram", 1.0)), emptyList(), listOf(gram), today)
        assertTrue(planCopyDraft(octProgress, YearMonth(2026, 11), emptyList(), today).all { it.provisional })
    }

    @Test
    fun guncelFiyatAnlikGoruntuOlur() {
        val out = planCopyDraft(sepProgress, oct, emptyList(), today).toItems(oct, emptySet()) { 6800.0 }
        assertTrue(out.all { it.unitPriceAtPlan == 6800.0 })
    }
}

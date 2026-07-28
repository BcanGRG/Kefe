package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EPS = 1e-9

private fun position(id: String, value: Double): Position = Position(
    id = id,
    name = id,
    assetClass = AssetClass.Gold,
    quantity = 1.0,
    unit = QuantityUnit.Piece,
    unitPrice = value,
    value = value,
    cost = value,
)

private fun goal(id: String): Goal = Goal(
    id = id,
    name = id,
    iconKey = "home",
    amount = 100_000.0,
    unit = GoalUnit.Try,
    targetDate = KefeDate(2028, 12, 1),
    monthlyContribution = 5_000.0,
)

private val ev = goal("ev")
private val araba = goal("araba")
private val positions = listOf(
    position("altin", 20_000.0),
    position("euro", 5_000.0),
    position("fon", 3_000.0),
)

class GoalAssetsTest {

    @Test
    fun atamaYoksaTumBirikimSayilir() {
        // Eski davranis korunur: atamayla ugrasmak istemeyen kullanici
        // hedeflerinin bir anda %0'a dusmesini gormez.
        assertEquals(28_000.0, goalWealth(ev, positions, emptyMap()), EPS)
    }

    @Test
    fun atamaVarsaYalnizOnlarSayilir() {
        val assignments = mapOf("altin" to "ev")
        assertEquals(20_000.0, goalWealth(ev, positions, assignments), EPS)
    }

    @Test
    fun birdenFazlaVarlikToplanir() {
        val assignments = mapOf("altin" to "ev", "fon" to "ev")
        assertEquals(23_000.0, goalWealth(ev, positions, assignments), EPS)
    }

    @Test
    fun baskaHedefinAtamasiBunuEtkilemez() {
        // Araba'ya atama yapilmis ama Ev'e yapilmamis: Ev hala tum birikimi
        // sayar. Aksi halde bir hedefe atama yapmak digerini sifirlardi.
        val assignments = mapOf("altin" to "araba")
        assertEquals(28_000.0, goalWealth(ev, positions, assignments), EPS)
        assertEquals(20_000.0, goalWealth(araba, positions, assignments), EPS)
    }

    @Test
    fun atanmisVarlikSilinirseHedefSifirlanir() {
        // Atama duruyor ama varlik listede yok: hedef 0 gorur, tum birikime
        // geri DONMEZ. Donseydi varligi silmek ilerlemeyi yukseltirdi.
        val assignments = mapOf("silinmis" to "ev")
        assertEquals(0.0, goalWealth(ev, positions, assignments), EPS)
    }

    @Test
    fun atanmislarListelenir() {
        val assignments = mapOf("altin" to "ev", "euro" to "araba")
        val mine = assetsOf(ev, positions, assignments)

        assertEquals(listOf("altin"), mine.map { it.id })
    }

    @Test
    fun atamaYoksaListeBos() {
        // Bos liste "tum birikim sayiliyor" demek; ekran bunu yaziyla soyler.
        assertTrue(assetsOf(ev, positions, emptyMap()).isEmpty())
    }

    @Test
    fun baskaHedefUyarisi() {
        val assignments = mapOf("altin" to "araba")

        assertEquals("araba", otherGoalOf("altin", ev, assignments))
        // Zaten bu hedefteyse uyari yok.
        assertNull(otherGoalOf("altin", araba, assignments))
        // Hic atanmamissa da yok.
        assertNull(otherGoalOf("euro", ev, assignments))
    }
}

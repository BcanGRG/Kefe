package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EPS = 1e-9

private val bugun = KefeDate(2026, 7, 28)

private fun goal(
    amount: Double,
    monthlyContribution: Double,
    targetDate: KefeDate = KefeDate(2027, 7, 1),
): Goal = Goal(
    id = "goal-1",
    name = "Ev",
    iconKey = "home",
    amount = amount,
    unit = GoalUnit.Try,
    targetDate = targetDate,
    monthlyContribution = monthlyContribution,
)

class ProjectionTest {

    /**
     * Projeksiyon YALNIZ hedefin kendi rakamlarindan turer.
     *
     * Once gerceklesen seri olarak portfoy geneli fotograflar veriliyordu ve
     * tahmin hedefin birikiminden basliyordu: iki egri farkli tabandaydi ve
     * "bugun"de birlesmeleri gerekirken birlesmiyordu. Hedefin gecmisi
     * tutulmadigi icin dogrusu turetilemiyor; portfoy egrisini hedefin gecmisi
     * diye cizmektense hic cizmemek dogru. Tip artik fotograf ALMIYOR - yanlis
     * seriyi gecmek derlenmiyor.
     */
    @Test
    fun projeksiyonYALNIZHedefinRakamlarindanTurer() {
        val result = goalProjection(goal(1_000.0, 100.0), 120.0, bugun)

        // Tek zaman serisi tahmindir ve bugunku HEDEF birikiminden baslar.
        assertEquals(120.0, result.forecast.first(), EPS)
    }

    @Test
    fun tahminBugunkuBirikimdenBaslar() {
        // Iki egri kesintisiz birlesmeli: tahminin ilk noktasi bugunku deger.
        val result = goalProjection(goal(1_000.0, 250.0), 500.0, bugun)

        assertEquals(500.0, result.forecast.first(), EPS)
        assertEquals(listOf(500.0, 750.0, 1_000.0), result.forecast)
    }

    @Test
    fun varisAyiKatkiyaGoreHesaplanir() {
        // 500 birikim, hedef 1.000, ayda 250 -> 2 ay.
        val result = goalProjection(goal(1_000.0, 250.0), 500.0, bugun)

        assertEquals(bugun.plusMonths(2), result.arrival)
    }

    @Test
    fun getiriVarsayilmaz() {
        // Tahmin dogrusal: her adim tam olarak aylik katki kadar artar.
        val result = goalProjection(goal(10_000.0, 1_000.0), 0.0, bugun)

        result.forecast.zipWithNext { a, b -> assertEquals(1_000.0, b - a, EPS) }
    }

    @Test
    fun katkiYoksaTahminUretilmez() {
        // Duz bir cizgi cizmek "hicbir sey yapmasan da varirsin" demek olurdu.
        val result = goalProjection(goal(1_000.0, 0.0), 100.0, bugun)

        assertTrue(result.forecast.isEmpty())
        assertNull(result.arrival)
    }

    @Test
    fun hedefKarsilandiysaTahminYok() {
        val result = goalProjection(goal(1_000.0, 250.0), 1_200.0, bugun)

        assertTrue(result.forecast.isEmpty())
        assertEquals(bugun, result.arrival)
    }

    @Test
    fun cokUzakHedefteVarisNull() {
        // Ayda 1 TL ile 1 milyara varmak 40 yili asar; sinira takilir.
        val result = goalProjection(goal(1_000_000_000.0, 1.0), 0.0, bugun)

        assertNull(result.arrival)
    }

    // --- Kilometre taslari ---

    @Test
    fun gecilenDurakIsaretlenir() {
        // 600 birikim, 1.000 hedef -> %25 ve %50 gecildi.
        val milestones = goalMilestones(goal(1_000.0, 250.0), 600.0, emptyList(), bugun)

        assertEquals(listOf(true, true, false, false), milestones.map { it.reached })
        assertEquals("geçildi", milestones[0].label)
    }

    @Test
    fun tutarlarHedeftenTurer() {
        val milestones = goalMilestones(goal(4_000.0, 250.0), 0.0, emptyList(), bugun)

        assertEquals(listOf(1_000.0, 2_000.0, 3_000.0, 4_000.0), milestones.map { it.amount })
    }

    @Test
    fun gelecekDurakTarihiTahmindenOkunur() {
        // 0 birikim, ayda 250, hedef 1.000 -> %50 (500 TL) ikinci ayda.
        val projection = goalProjection(goal(1_000.0, 250.0), 0.0, bugun)
        val milestones = goalMilestones(goal(1_000.0, 250.0), 0.0, projection.forecast, bugun)

        val half = milestones.single { it.percent == 50 }
        assertEquals("≈ " + bugun.plusMonths(2).formatMonthYear(), half.label)
    }

    @Test
    fun tahminYoksaDurakTarihiTireOlur() {
        // Uydurma bir tarih yerine tire: bilinmiyorsa bilinmiyor yazar.
        val milestones = goalMilestones(goal(1_000.0, 0.0), 0.0, emptyList(), bugun)

        assertTrue(milestones.all { it.label == "—" })
        assertNotNull(milestones.firstOrNull())
    }
}

package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "VADESI GECTI" TURETILIR, SAKLANMAZ.
 *
 * Saklaniyordu ve hicbir zaman yazilmiyordu: `GoalStatus.Overdue`'yu uretecek
 * tek sorgu (`updateGoalStatus`) hicbir yerden cagrilmiyor, yalniz ornek veri
 * elle isaretliyordu. Yani tarihi gecmis GERCEK bir hedef ekranda hic "gecikti"
 * gorunmuyordu; gorunen tek gecikme uydurma ornek hedefindi.
 *
 * Saklamak zaten yanlisti: gun donunce bayatlar ve tazelemek icin bir arka plan
 * isi gerekirdi. Enum degeri kaldirildi (bkz. 10.sqm).
 */
class GoalOverdueTest {

    private fun goal(
        target: KefeDate,
        status: GoalStatus = GoalStatus.Active,
    ) = Goal(
        id = "goal-1",
        name = "Tatil",
        iconKey = "tatil",
        amount = 100_000.0,
        unit = GoalUnit.Try,
        targetDate = target,
        monthlyContribution = 5_000.0,
        status = status,
    )

    private val bugun = KefeDate(2026, 8, 12)

    @Test
    fun gecmisTarihliHedefGECIKMISTIR() {
        assertTrue(goal(KefeDate(2026, 7, 1)).isOverdue(bugun))
    }

    @Test
    fun ileriTarihliHedefGecikmisDEGILDIR() {
        assertFalse(goal(KefeDate(2027, 1, 1)).isOverdue(bugun))
    }

    /** Bugun hedef tarihiyse sure DOLMAMISTIR - o gun hala hedefin gunudur. */
    @Test
    fun bugunHedefTarihiyseGecikmeYOK() {
        assertFalse(goal(bugun).isOverdue(bugun))
    }

    /**
     * OLCU AYDIR. Hedef tarihi kullaniciya yalniz ay-yil olarak gosteriliyor ve
     * secici de o incelikte; gun alanindaki deger (varsayilan: ayin 1'i) hic
     * secilmiyor. Gun bazinda kiyaslayinca icinde bulunulan aya kurulan bir
     * hedef DOGAR DOGMAZ gecikmis sayiliyordu.
     */
    @Test
    fun ICINDEBULUNULANAyaKurulanHedefGecikmisDEGILDIR() {
        // 12 Agustos'ta "Agustos 2026" secmek 1 Agustos yaziyor.
        assertFalse(goal(KefeDate(2026, 8, 1)).isOverdue(bugun), "hedef dogar dogmaz gecikti")
        assertFalse(goal(KefeDate(2026, 8, 31)).isOverdue(bugun))
    }

    @Test
    fun tamamlanmisHedefGecikmisSAYILMAZ() {
        val tamamlanan = goal(KefeDate(2026, 7, 1), status = GoalStatus.Completed)

        assertFalse(tamamlanan.isOverdue(bugun), "tamamlanan hedef gecikmis gosterildi")
    }

    /** Ay donunce cevap KENDILIGINDEN degisir - saklansa bayat kalirdi. */
    @Test
    fun cevapAYAGoreDegisir() {
        val hedef = goal(KefeDate(2026, 8, 12))

        // Agustos boyunca hedefin ayi devam ediyor.
        assertFalse(hedef.isOverdue(KefeDate(2026, 8, 13)))
        assertFalse(hedef.isOverdue(KefeDate(2026, 8, 31)))
        // Eylul girince gecikti.
        assertTrue(hedef.isOverdue(KefeDate(2026, 9, 1)))
    }
}

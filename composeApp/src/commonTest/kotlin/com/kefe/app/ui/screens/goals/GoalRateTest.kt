package com.kefe.app.ui.screens.goals

import com.kefe.app.domain.model.GoalUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hedef tutarinin birim cevrimi, kur HENUZ GELMEMISKEN.
 *
 * `rateOf` bilinmeyen kur icin 1.0 donuyordu ("bolme hatasi olmasin") ve bu,
 * 1 gram altini 1 TL'ye esitliyordu:
 *
 *  - cevrimdisi ilk acilista "400 gram" yazan biri ₺2.000.000 degil ₺400
 *    kaydediyordu ve hedef bir daha 0,08 gram olarak aciliyordu;
 *  - ters yonde daha agiri: kur 0 iken acilan bir gram hedefi alanda
 *    "2.000.000 gr" gorunuyor, kur sonradan gelince ayni sayi gercek kurla
 *    carpiliyor ve hedef milyarlara ciktiyordu.
 *
 * Kurun gelmeyebilecegini kodun kendisi zaten kabul ediyordu; eksik olan onu
 * bir CEVAP saymamakti.
 */
class GoalRateTest {

    private val noRates = GoalEditorState(name = "Ev", amountText = "400")
    private val withRates = noRates.copy(goldGramPrice = 5_000.0, usdPrice = 41.0)

    @Test
    fun kurYokkenORANBilinmiyor() {
        assertNull(noRates.rateOrNull(GoalUnit.GoldGram))
        assertNull(noRates.rateOrNull(GoalUnit.Usd))
        // TL'nin kuru her zaman bellidir.
        assertEquals(1.0, noRates.rateOrNull(GoalUnit.Try))
    }

    @Test
    fun kurGelinceORANBilinir() {
        assertEquals(5_000.0, withRates.rateOrNull(GoalUnit.GoldGram))
        assertEquals(41.0, withRates.rateOrNull(GoalUnit.Usd))
    }

    /**
     * Asil kural: kur yokken TL karsiligi HESAPLANAMAZ.
     *
     * Eskiden 400 gram icin 400.0 donuyordu - yani ₺400. Dogru cevap
     * 400 x 5.000 = ₺2.000.000, ve o bilinmiyorsa cevap yoktur.
     */
    @Test
    fun kurYokkenTLKarsiligiHESAPLANMAZ() {
        assertNull(noRates.copy(unit = GoalUnit.GoldGram).amountInTryOrNull())
        assertNull(noRates.copy(unit = GoalUnit.Usd).amountInTryOrNull())
    }

    @Test
    fun kurVarkenTLKarsiligiDOGRU() {
        assertEquals(2_000_000.0, withRates.copy(unit = GoalUnit.GoldGram).amountInTryOrNull())
        assertEquals(16_400.0, withRates.copy(unit = GoalUnit.Usd).amountInTryOrNull())
    }

    /** TL hedefinde kur beklenmez - cevrimdisi de olsa kaydedilebilmeli. */
    @Test
    fun TLHedefiKURBeklemez() {
        assertEquals(400.0, noRates.copy(unit = GoalUnit.Try).amountInTryOrNull())
    }

    /** Ekran, kuru gelmemis birim cipini kilitler. */
    @Test
    fun kurYokkenBirimCipiKILITLI() {
        assertTrue(noRates.rateKnown(GoalUnit.Try))
        assertFalse(noRates.rateKnown(GoalUnit.GoldGram))
        assertFalse(noRates.rateKnown(GoalUnit.Usd))

        assertTrue(withRates.rateKnown(GoalUnit.GoldGram))
        assertTrue(withRates.rateKnown(GoalUnit.Usd))
    }

    /** Kurun yalniz biri gelmis olabilir; digeri hala kilitli kalmali. */
    @Test
    fun kurlarBAGIMSIZDegerlendirilir() {
        val onlyGold = noRates.copy(goldGramPrice = 5_000.0)
        assertTrue(onlyGold.rateKnown(GoalUnit.GoldGram))
        assertFalse(onlyGold.rateKnown(GoalUnit.Usd))
    }

    /** Sifir ya da negatif kur "bilinmiyor" demektir, gecerli bir kur degil. */
    @Test
    fun sifirKurBILINMIYORSayilir() {
        assertNull(noRates.copy(goldGramPrice = 0.0).rateOrNull(GoalUnit.GoldGram))
        assertNull(noRates.copy(goldGramPrice = -1.0).rateOrNull(GoalUnit.GoldGram))
    }
}

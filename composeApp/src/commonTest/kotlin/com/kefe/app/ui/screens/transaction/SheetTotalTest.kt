package com.kefe.app.ui.screens.transaction

import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.TradeSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Alt bloktaki IKI SATIR birbirini tutmali.
 *
 * "Toplam" kutusu satista isciligi hasilattan dusuyor (defterin yaptigi da bu)
 * ama hemen altindaki acilim satiri her durumda "+ ₺500 işçilik" yaziyordu:
 * kutu ₺19.626, acilim ₺20.626 okunuyordu. Ayni blogun iki yarisi celisiyordu.
 */
class SheetTotalTest {

    private fun state(side: TradeSide) = AddTransactionUiState(
        step = AddTransactionStep.Amount,
        side = side,
        quantityText = "2",
        unitPriceText = "10063",
        feeText = "500",
    )

    @Test
    fun alistaIscilikEKLENIR() {
        val s = state(TradeSide.Buy)

        assertEquals(20_626.0, s.total, EPS)
        assertTrue(" + " in s.footNote, "acilim eksi yazdi: ${s.footNote}")
    }

    @Test
    fun satistaIscilikDUSULUR() {
        val s = state(TradeSide.Sell)

        assertEquals(19_626.0, s.total, EPS)
        assertTrue(" − " in s.footNote, "acilim hala arti yaziyor: ${s.footNote}")
    }

    /** Isciliksiz islemde acilim yalniz carpimi yazar. */
    @Test
    fun isciliksizIslemdeIsaretYok() {
        val s = state(TradeSide.Sell).copy(feeText = "")

        assertTrue("işçilik" !in s.footNote, s.footNote)
    }

    // --- Gram / adet ---------------------------------------------------------

    /**
     * Gram kutusuna yazilan deger miktara da yansiyor. Alt tur ADETLE olculene
     * gecince o sayi ADET olarak kalamaz: 62,4 gram bilezik yazip Ceyrek'e
     * gecen kullanici 2. adimi "62,4 adet ceyrek" ile aciyordu.
     */
    @Test
    fun gramdanADETEGecerkenMiktarSifirlanir() {
        val bilezik = AddTransactionUiState(
            selectedSubtype = GoldSubtype.Jewelry,
            gramText = "62,4",
            quantityText = "62,4",
        )
        val ceyrek = bilezik.withSubtype(GoldSubtype.Quarter)

        // Birim gercekten degisiyor - testin dayanagi bu.
        assertTrue(bilezik.quantityUnit != ceyrek.quantityUnit)
        assertEquals("", ceyrek.quantityText, "gram sayisi adet olarak kaldi")
    }

    @Test
    fun ayniBIRIMDEKalinincaMiktarKorunur() {
        val bilezik = AddTransactionUiState(
            selectedSubtype = GoldSubtype.Jewelry,
            quantityText = "62,4",
        )
        val gram = bilezik.withSubtype(GoldSubtype.Gram)

        assertEquals(bilezik.quantityUnit, gram.quantityUnit)
        assertEquals("62,4", gram.quantityText, "ayni birimde miktar silindi")
    }

    private companion object {
        const val EPS = 1e-9
    }
}

package com.kefe.app.ui.screens.transaction

import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.Karat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Altin pozisyonunun KIMLIK anahtari.
 *
 * Kimlik ayari tasimiyordu: butun gram formlari `gold_gram`a dusuyordu. Ayar
 * eslesmedigi icin 22 ayar gram secimi mevcut 24 ayar pozisyonuna eslesmiyor,
 * ama uretilen yeni kimlik yine ONUN kimligi oluyordu; iki ayri varligin
 * defteri tek pozisyonda birlesiyor ve eski miktar da yeni ayarin kotasyonuyla
 * degerleniyordu.
 *
 * ValuationTest bunu yakalayamiyordu cunku o `Position.priceKey()`'i, yani
 * varligin NEYLE FIYATLANDIGINI olcuyor; burada olculen HANGI SATIR oldugu.
 */
class GoldAssetKeyTest {

    /** Asil kural: farkli ayardaki gram altin AYRI satirdir. */
    @Test
    fun gramAltindaAyarKIMLIGIAyirir() {
        val k24 = goldAssetKey(GoldSubtype.Gram, Karat.K24)
        val k22 = goldAssetKey(GoldSubtype.Gram, Karat.K22)
        val k18 = goldAssetKey(GoldSubtype.Gram, Karat.K18)
        val k14 = goldAssetKey(GoldSubtype.Gram, Karat.K14)

        assertEquals(4, setOf(k24, k22, k18, k14).size, "ayarlar ayni kimlige dusuyor")
        assertNotEquals(k24, k22)
    }

    /**
     * 24 ayar gramin kimligi DEGISMEMELI: mevcut kullanicilarin pozisyonu
     * `pos_gold_gram` ve kimlik degisirse defteri oksuz kalir.
     */
    @Test
    fun yirmiDortAyarGramESKIKimligiKorur() {
        assertEquals("gold_gram", goldAssetKey(GoldSubtype.Gram, Karat.K24))
    }

    /**
     * Has/Kulce gram altinla ayni kotasyondan fiyatlanir ama ayri bir varliktir;
     * kimligi 24 ayar gramla cakisiyordu.
     */
    @Test
    fun hasKulceGramAltindanAYRIDIR() {
        assertNotEquals(
            goldAssetKey(GoldSubtype.Gram, Karat.K24),
            goldAssetKey(GoldSubtype.Bullion, Karat.K24),
        )
    }

    /** Adetle satilan formlarda ayar sorulmaz; kimlik ayardan ETKILENMEZ. */
    @Test
    fun adetliFormlardaAyarKimligiDEGISTIRMEZ() {
        listOf(GoldSubtype.Quarter, GoldSubtype.Half, GoldSubtype.Full, GoldSubtype.Ata)
            .forEach { subtype ->
                assertEquals(
                    goldAssetKey(subtype, Karat.K22),
                    goldAssetKey(subtype, Karat.K24),
                    "$subtype ayardan etkilenmemeli",
                )
            }
    }

    /** Bilezik ayara gore ayrilmaya devam eder - bu zaten dogruydu. */
    @Test
    fun bilezikAyaraGoreAYRILIR() {
        assertEquals("gold_jewelry_916", goldAssetKey(GoldSubtype.Jewelry, Karat.K22))
        assertEquals("gold_jewelry_585", goldAssetKey(GoldSubtype.Jewelry, Karat.K14))
    }

    /** Butun (form, ayar) ciftleri arasinda hicbir cakisma kalmamali. */
    @Test
    fun hicbirFormAyarCiftiCAKISMAZ() {
        val seen = mutableMapOf<String, String>()
        GoldSubtype.entries.forEach { subtype ->
            Karat.entries.forEach { karat ->
                // Ayar sorulmayan formda tek bir temsilci yeter.
                if (!subtype.usesKarat() && karat != Karat.K22) return@forEach
                val key = goldAssetKey(subtype, karat)
                val label = "$subtype/$karat"
                val previous = seen.put(key, label)
                assertEquals(null, previous, "$label ile $previous ayni kimlige dusuyor: $key")
            }
        }
    }
}

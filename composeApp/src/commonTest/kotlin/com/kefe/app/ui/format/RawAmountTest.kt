package com.kefe.app.ui.format

import com.kefe.app.ui.screens.goals.parseAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tutar ALANINA yazilan ham metin ve onun geri okunmasi.
 *
 * [rawAmount] kesirli degerde `Double.toString()` kullaniyordu ve JVM/Android'de
 * bu, mutlak deger 10^7'yi asinca ya da 10^-3'un altina inince BILIMSEL
 * GOSTERIM doner. Hedef editorunun ayristiricisi ise tanimadigi karakteri
 * sessizce atiyordu; ikisi birlesince tutar hataya dusmek yerine BASKA bir
 * sayiya donusuyordu:
 *
 *   2.000 gr x ₺6.242,71 = ₺12.485.419,999999998
 *   -> "1,2485419999999998E7" -> 'E' atilir -> 1,2485... TL
 *
 * Yani ₺12,5 milyonluk hedef, kullanici tutara hic dokunmadan kaydedince
 * yaklasik 1000 kat kuculuyordu.
 */
class RawAmountTest {

    /** Alan metninde bilimsel gosterim ASLA olmamali. */
    @Test
    fun buyukTutarBILIMSELGosterimUretmez() {
        listOf(12_485_419.999999998, 10_000_000.5, 99_999_999.25, 1.5e7 + 0.5)
            .forEach { value ->
                val text = rawAmount(value)
                assertTrue('E' !in text && 'e' !in text, "bilimsel gosterim: $value -> $text")
            }
    }

    @Test
    fun kucukTutarBILIMSELGosterimUretmez() {
        listOf(0.0002, 0.00015, 0.000999).forEach { value ->
            val text = rawAmount(value)
            assertTrue('E' !in text && 'e' !in text, "bilimsel gosterim: $value -> $text")
        }
    }

    /**
     * Hatanin kendisi: hedefi acip hic dokunmadan kaydetmek tutari korumali.
     *
     * Kayan nokta artigi (…,999999998) kurusun cok altinda oldugu icin metin
     * tam sayiya oturur; onemli olan geri okunan degerin AYNI tutar olmasi.
     */
    @Test
    fun hedefTutariYAZOKUAyniKalir() {
        listOf(12_485_419.999999998, 2_000_000.0, 7_800_000.0, 10_055_218.14, 1_234.56)
            .forEach { amount ->
                val readBack = rawAmount(amount).parseAmount()
                assertTrue(
                    kotlin.math.abs(readBack - amount) < 0.01,
                    "tutar bozuldu: $amount -> \"${rawAmount(amount)}\" -> $readBack",
                )
            }
    }

    /** Gram cinsine cevrilmis kucuk tutar da geri okunabilmeli. */
    @Test
    fun gramCevrimiYAZOKUAyniKalir() {
        listOf(0.0002, 2.0408, 320.5, 2_000.0).forEach { grams ->
            val readBack = rawAmount(grams).parseAmount()
            assertTrue(
                kotlin.math.abs(readBack - grams) < 1e-6,
                "gram bozuldu: $grams -> \"${rawAmount(grams)}\" -> $readBack",
            )
        }
    }

    /** Tam sayi tutarda ondalik yazilmaz - alan "3000000,00" gostermemeli. */
    @Test
    fun tamSayidaOndalikYAZILMAZ() {
        assertEquals("3000000", rawAmount(3_000_000.0))
        assertEquals("1", rawAmount(1.0))
    }

    /** Sifir ve altinda alan BOS acilir. */
    @Test
    fun sifirVeAltiBOSDoner() {
        assertEquals("", rawAmount(0.0))
        assertEquals("", rawAmount(-5.0))
    }

    /**
     * Cevrimden kalan kayan nokta kuyrugu alana TASINMAZ: miktar adim butonu
     * "0,30000000000000004" yaziyordu.
     */
    @Test
    fun kayanNoktaKuyruguALANAYazilmaz() {
        assertEquals("0,3", rawAmount(0.2 + 0.1))
        assertEquals("0,8", rawAmount(0.7 + 0.1))
    }

    /** Fon payi alti haneye kadar korunur - hassasiyet kaybi olmamali. */
    @Test
    fun fonPayiHassasiyetiniKORUR() {
        assertEquals("9,213847", rawAmount(9.213847))
        assertEquals("108,394521", rawAmount(108.394521))
    }

    /**
     * Ayristirici tanimadigi metni BASKA bir sayiya cevirmemeli; artik
     * cozulemeyen metin 0.0'a duser ve `amount <= 0` kontrolu yakalar.
     */
    @Test
    fun cozulemeyenMetinSIFIRDoner() {
        assertEquals(0.0, "".parseAmount())
        assertEquals(0.0, "abc".parseAmount())
        assertEquals(0.0, "12,3,4".parseAmount())
    }

    /** Binlik noktali metin de dogru okunur - hedef alani bunu tasiyabiliyor. */
    @Test
    fun binlikNoktaliMetinDOGRUOkunur() {
        assertEquals(3_000_000.0, "3.000.000".parseAmount())
        assertEquals(2.5, "2,5".parseAmount())
    }
}

package com.kefe.app.ui.format

import com.kefe.app.ui.screens.transaction.parseTrNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * tr-TR tutar girisinin ayristirilmasi.
 *
 * Bu ayristirici bir zamanlar iki ayri kopya halinde duruyordu ve kopyalar
 * ayristi: islem sayfasindaki dogru, Piyasa ekranindaki yanlisti. Yanlis olan
 * "virgul yoksa metne dokunma" diyordu ve binlik noktasini ondalik ayirici
 * sayiyordu - ata altini ₺41.457,30 yerine ₺41,46 kaydediliyordu.
 *
 * Buradaki kural tek cumle: NOKTA HER ZAMAN BINLIK AYRACIDIR.
 */
class TrAmountTest {

    /** Hatanin ta kendisi: binlik noktasi ondalik sayilirsa deger bin kat kuculur. */
    @Test
    fun binlikNoktasiONDALIKSayilmaz() {
        assertEquals(41_457.0, "41.457".parseTrAmountOrNull())
        assertEquals(10_027.0, "10.027".parseTrAmountOrNull())
        assertEquals(1_234_567.0, "1.234.567".parseTrAmountOrNull())
    }

    @Test
    fun ondalikVirgulCozulur() {
        assertEquals(6_175.37, "6175,37".parseTrAmountOrNull())
        assertEquals(0.5, "0,5".parseTrAmountOrNull())
    }

    @Test
    fun binlikVeOndalikBirlikte() {
        assertEquals(1_234_567.89, "1.234.567,89".parseTrAmountOrNull())
        assertEquals(41_457.30, "41.457,30".parseTrAmountOrNull())
    }

    /** Simge ve bosluklar girisin parcasi degil. */
    @Test
    fun simgeVeBoslukAtilir() {
        assertEquals(41_457.0, "₺ 41.457".parseTrAmountOrNull())
        assertEquals(6_175.37, " 6175,37 ".parseTrAmountOrNull())
    }

    /**
     * Cozulemeyen metin NULL doner, sifir degil.
     *
     * Sifira dusmek "kullanici 0 yazdi" ile "anlayamadim"i ayni sey yapardi ve
     * Piyasa ekrani gecersiz girisi hata olarak isaretleyemezdi.
     */
    @Test
    fun cozulemeyenMetinNULLDoner() {
        assertNull("".parseTrAmountOrNull())
        assertNull("   ".parseTrAmountOrNull())
        assertNull("₺".parseTrAmountOrNull())
        assertNull("abc".parseTrAmountOrNull())
        assertNull("12,3,4".parseTrAmountOrNull())
    }

    /**
     * Islem sayfasi ayni mantigi kullanir - kopyalarin yeniden ayrismasina
     * karsi kilit. Tek fark bos metinde: orada alanlar bos baslar ve 0.0
     * beklenir.
     */
    @Test
    fun islemSayfasiAyniSonucuVerir() {
        listOf("41.457", "6175,37", "1.234.567,89", "0,5").forEach { text ->
            assertEquals(
                text.parseTrAmountOrNull(),
                text.parseTrNumber(),
                "iki ayristirici ayristi: $text",
            )
        }
    }

    /**
     * [Money.plain] duzenlenebilir alani doldurur ve alandaki metin ayni sayiya
     * geri cozulmelidir. Gruplama yapilirsa bu ozdeslik bozulur - hatanin
     * kaynagi tam olarak buydu.
     */
    @Test
    fun plainYAZILANIAynenGeriOkur() {
        listOf(41_457.30 to 0, 10_027.02 to 0, 6_175.37 to 2, 87.9 to 2, 3.714523 to 6)
            .forEach { (value, decimals) ->
                val text = Money.plain(value, decimals)
                assertTrue('.' !in text, "alan metninde binlik ayraci var: $text")
                assertEquals(
                    Money.plain(value, decimals),
                    Money.plain(text.parseTrAmountOrNull()!!, decimals),
                    "yaz-oku ozdesligi bozuldu: $value -> $text",
                )
            }
    }

    /** Gruplama yalniz GOSTERIM icindir; alan metnine sizmamali. */
    @Test
    fun numberGruplarPlainGruplamaz() {
        assertEquals("41.457", Money.number(41_457.30, 0))
        assertEquals("41457", Money.plain(41_457.30, 0))
        assertEquals("1.234.567,89", Money.number(1_234_567.89, 2))
        assertEquals("1234567,89", Money.plain(1_234_567.89, 2))
    }
}

package com.kefe.app.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [Money]'nin kenar davranislari.
 *
 * Dordu de gunluk rakamlarda dogru, kenarda yanlisti - ve hicbiri yazili
 * degildi. Bu test sozlesmeyi kayda geciriyor.
 */
class MoneyEdgeTest {

    // --- Yuvarlama ----------------------------------------------------------

    /**
     * YARIM YUKARI, yarim cifte degil. `kotlin.math.round` banker's yuvarlama
     * yapiyor (`round(12.5) = 12`), yani ₺0,125 "₺0,12" cikiyordu. TR finans
     * geleneginde 0,13.
     */
    @Test
    fun yarimYUKARIYuvarlanir() {
        // IKILI TABANDA TAM YARIM olan ve iki kuralin AYRISTIGI degerler:
        // banker's cift tarafa yuvarladigi icin asagi, yarim-yukari yukari.
        assertEquals("₺0,13", Money.tl(0.125, decimals = 2))   // banker's: 0,12
        assertEquals("₺3", Money.tl(2.5))                      // banker's: 2
        assertEquals("₺5", Money.tl(4.5))                      // banker's: 4
        // 0,5 buraya girmez: 1 TL altindaki tutar zaten kurusla yazilir
        // ("₺0,50"), yani yuvarlama kuralina hic ulasmaz.

        // 0,145 gibi bir sayi bellekte 0,14499999... olarak durur, yani zaten
        // yarim DEGILDIR; hangi kural uygulanirsa uygulansin 0,14'e iner. Bu
        // kuralin degil, kayan noktanin sonucu.
        assertEquals("₺0,14", Money.tl(0.145, decimals = 2))
    }

    @Test
    fun eksideDeSIFIRDANUZAGA() {
        assertEquals("−₺0,13", Money.tl(-0.125, decimals = 2))
    }

    @Test
    fun yuvarlamaGerekmeyenDegerAYNIKalir() {
        assertEquals("₺1.234,56", Money.tl(1234.56, decimals = 2))
        assertEquals("₺3.180.400", Money.tl(3_180_400.0))
    }

    // --- Kurus toleransi ----------------------------------------------------

    /**
     * ASIL HATA. Tolerans `1e-9 x deger` idi ve 10 milyonda 0,01'e ulasiyordu:
     * gercek kurus "yeterince yakin" sayilip yutuluyordu. ILERLEME §24 "kurus
     * hep gorunur" diyor.
     */
    @Test
    fun BUYUKTutardaKurusYutulmaz() {
        assertEquals(2, Money.decimals(10_000_000.01, max = 2), "10 milyonda kurus yutuldu")
        assertEquals(2, Money.decimals(99_999_999.99, max = 2))
        assertTrue("," in Money.tlExact(10_000_000.01))
    }

    @Test
    fun kurussuzTutarKURUSSUZKalir() {
        assertEquals(0, Money.decimals(10_000_000.0, max = 2))
        assertEquals("₺10.000.000", Money.tlExact(10_000_000.0))
    }

    /** Kayan nokta artigi hala yutulur - toleransin varlik sebebi bu. */
    @Test
    fun kayanNoktaArtigiYUTULUR() {
        // 108,39 diskte 108.38999999999999 olabiliyor.
        assertEquals(2, Money.decimals(108.38999999999999, max = 6))
        assertEquals(0, Money.decimals(40_359.000000000007, max = 2))
    }

    // --- Isaretli tutar -----------------------------------------------------

    /**
     * 1 TL altindaki GERCEK bir degisim "+₺0" yazilamaz: bir degisim oldugunu
     * soyleyip buyuklugunu sifir gosteren bir satir olurdu. [Money.tl] ayni
     * kurali zaten uyguluyordu.
     */
    @Test
    fun birLiraAltiDegisimSIFIRYazilmaz() {
        assertEquals("+₺0,40", Money.tlSigned(0.4))
        assertEquals("−₺0,40", Money.tlSigned(-0.4))
    }

    @Test
    fun gercekSifirISARETSIZdir() {
        assertEquals("₺0", Money.tlSigned(0.0))
    }

    @Test
    fun birLiraUstuTutarTAMKalir() {
        assertEquals("+₺12.400", Money.tlSigned(12_400.0))
        assertEquals("+₺1", Money.tlSigned(1.0))
    }

    // --- Kisaltma -----------------------------------------------------------

    /**
     * ASIL HATA: kademe yuvarlamadan ONCE seciliyordu. 999.950 bin kademesine
     * giriyor, 999,95 sifir haneye yuvarlanip 1.000 oluyor ve ekranda "1.000B"
     * yaziyordu - var olmayan bir birim.
     */
    @Test
    fun binSinirindaMILYONAGecer() {
        assertEquals("1M", Money.compact(999_950.0))
        assertEquals("1M", Money.compact(1_000_000.0))
        assertEquals("999B", Money.compact(999_400.0))
    }

    /** Tam milyonda ",0" yazilmaz - bin kademesindeki kuralin aynisi. */
    @Test
    fun tamMilyondaONDALIKYazilmaz() {
        assertEquals("4M", Money.compact(4_000_000.0))
        assertEquals("4,2M", Money.compact(4_200_000.0))
    }

    @Test
    fun tamBindeOndalikYazilmaz() {
        assertEquals("100B", Money.compact(100_000.0))
        assertEquals("19,6B", Money.compact(19_587.0, thousandDecimals = 1))
    }

    /**
     * Tamlik testi ile bicimlendirici AYNI kurali kullanir.
     *
     * Kullanmiyordu: tamlik `round` ile (yarim-cifte) olculuyor, yazma
     * yarim-yukari yapiyordu. 1,05M tam yarim oldugu icin asagi inip "tam
     * milyon" sayiliyor ve "1M" yaziliyordu - tek ifadede iki kural.
     */
    @Test
    fun tamlikTestiBICIMLENDIRICIYLEAyniYuvarlar() {
        assertEquals("1,1M", Money.compact(1_050_000.0))
        assertEquals("2,1M", Money.compact(2_050_000.0))
        // Tam milyon hala ondaliksiz.
        assertEquals("2M", Money.compact(2_000_000.0))
    }

    @Test
    fun eksiKisaltmaISARETTasir() {
        assertEquals("−4,2M", Money.compact(-4_200_000.0))
    }
}

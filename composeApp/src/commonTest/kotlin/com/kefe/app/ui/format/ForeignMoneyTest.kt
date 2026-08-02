package com.kefe.app.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Yabanci borsa fiyatinin yazimi.
 *
 * Hesap her yerde TL ile yapilir; bu bicim yalniz "peki dolar olarak kaca
 * gelmis" sorusunu yanitlayan ikinci satir icindir.
 */
class ForeignMoneyTest {

    @Test
    fun bilinenBirimlerSimgeyleYazilir() {
        assertEquals("\$308,91", Money.foreign(308.91, "USD"))
        assertEquals("€157,68", Money.foreign(157.68, "EUR"))
        assertEquals("£33,84", Money.foreign(33.835, "GBP"))
    }

    /** Sayi tr-TR: kullanici Turkce okuyor, "1.294,50" bekliyor. */
    @Test
    fun binlikVeOndalikTurkceBicimde() {
        assertEquals("\$1.294,50", Money.foreign(1294.5, "USD"))
    }

    /**
     * Bilmedigimiz bir kod gelirse KODUN KENDISI yazilir. Uydurma bir simge
     * basmak ya da satiri bos birakmak, yanlis para birimi gostermek olurdu.
     */
    @Test
    fun tanimadigimizKodDuzYazilir() {
        assertEquals("CHF 42,10", Money.foreign(42.1, "CHF"))
    }

    /** Eksi isareti U+2212 - uygulamanin her yerinde ayni tire. */
    @Test
    fun eksiIsaretiUygulamaninTiresi() {
        val text = Money.foreign(-12.5, "USD")
        assertTrue(text.startsWith(Money.MINUS), "beklenen U+2212 ile baslamasi, gelen: $text")
        assertEquals("${Money.MINUS}\$12,50", text)
    }

    /**
     * "GBp" BURAYA GELMEZ - cevrim katmani onu sterline dondurur. Gelseydi
     * taninmayan kod kuralina duser ve "GBp 3.383,50" yazardi; yanlis olurdu
     * ama en azindan "£3.383,50" gibi yuz kat buyuk bir yalan olmazdi.
     */
    @Test
    fun peniKoduSterlinSimgesiALMAZ() {
        assertEquals("GBp 3.383,50", Money.foreign(3383.5, "GBp"))
    }
}

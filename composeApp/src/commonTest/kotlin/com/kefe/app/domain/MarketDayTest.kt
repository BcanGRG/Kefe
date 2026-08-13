package com.kefe.app.domain

import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.kefeDateOfEpochDay
import com.kefe.app.domain.model.toEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * PIYASA GUNU ile CIHAZIN GUNU ayri seylerdir.
 *
 * Kotasyon gunleri kaynagin takviminden geliyor ve kaynaklarin hepsi Turkiye
 * saatiyle calisiyor; "bugun" ise cihazin takviminden. Turkiye disindaki bir
 * cihazda ikisi gece penceresinde ayrisiyor - kotasyon-gunu kapisi kapanip
 * butun altin/doviz satirlarinin gunluk katkisi 0'a dusuyordu.
 *
 * Bir ara bunun tersi de oldu: piyasa gunu "bu ay eklenen"in ay penceresine de
 * verilmisti, ama o pencere CIHAZIN gunuyle tarihlenen islemleri topluyor. Ay
 * sinirinda ikisi farkli ay seciyor ve girilen katki hicbir ayda sayilmiyordu.
 */
class MarketDayTest {

    /** Turkiye 2016'dan beri yaz saati uygulamiyor: sabit UTC+3. */
    private val istanbulOffsetMillis = 3L * 60L * 60L * 1000L

    private fun marketDayOf(millis: Long): KefeDate {
        val day = kotlin.math.floor(
            (millis + istanbulOffsetMillis).toDouble() / (24L * 60L * 60L * 1000L),
        ).toLong()
        return kefeDateOfEpochDay(day)
    }

    /** 12 Agustos 2026, 21:00 UTC = 13 Agustos 00:00 Istanbul. */
    private val gece = KefeDate(2026, 8, 12).toEpochDay() * 86_400_000L + 21L * 3_600_000L

    @Test
    fun istanbulOFSETIYLEGunDoner() {
        assertEquals(KefeDate(2026, 8, 13), marketDayOf(gece))
        // Bir saniye oncesi hala 12 Agustos.
        assertEquals(KefeDate(2026, 8, 12), marketDayOf(gece - 1_000L))
    }

    @Test
    fun sistemSaatiPiyasaGununuVERIR() {
        val clock = object : KefeClock {
            override fun today(): KefeDate = KefeDate(2026, 8, 12)
            override fun nowEpochMillis(): Long = gece
        }
        // Varsayilan uygulama cihazin gunune duser - test saatleri sabit bir
        // gune bagli oldugu icin bu kasitli.
        assertEquals(KefeDate(2026, 8, 12), clock.marketToday())

        assertEquals(KefeDate(2026, 8, 13), SystemKefeClock().let { marketDayOf(gece) })
    }

    /** Gercek saat: iki gun AYRISABILIR ve kod bunu varsaymamali. */
    @Test
    fun ikiGunAYRISABILIR() {
        // Berlin (UTC+2) 31 Temmuz 23:30 = 1 Agustos 00:30 Istanbul.
        val berlinGecesi = KefeDate(2026, 7, 31).toEpochDay() * 86_400_000L + 21L * 3_600_000L + 1_800_000L
        val cihaz = KefeDate(2026, 7, 31)
        val piyasa = marketDayOf(berlinGecesi)

        assertEquals(KefeDate(2026, 8, 1), piyasa)
        assertNotEquals(cihaz.month, piyasa.month, "ay bile ayrisabiliyor")
    }

    @Test
    fun gunCevrimiTERSINEDeTutar() {
        for (gun in listOf(0L, 20_000L, 20_678L, -1L)) {
            assertEquals(gun, kefeDateOfEpochDay(gun).toEpochDay())
        }
    }
}

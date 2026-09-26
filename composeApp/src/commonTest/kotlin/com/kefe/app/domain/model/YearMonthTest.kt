package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Aylik planin donem birimi: yil sinirlari ve ay sonu. */
class YearMonthTest {

    @Test
    fun yilSinirindanGecer() {
        assertEquals(YearMonth(2027, 1), YearMonth(2026, 12).next())
        assertEquals(YearMonth(2025, 12), YearMonth(2026, 1).previous())
        assertEquals(YearMonth(2027, 3), YearMonth(2026, 10) + 5)
        assertEquals(YearMonth(2025, 11), YearMonth(2026, 10) - 11)
    }

    @Test
    fun sirasiKefeDateSayaciylaAYNI() {
        val date = KefeDate(2026, 10, 17)
        assertEquals(date.monthOrdinal(), YearMonth.of(date).ordinal)
        assertEquals(YearMonth(2026, 10), YearMonth.ofOrdinal(YearMonth(2026, 10).ordinal))
    }

    @Test
    fun aySonuSubattaArtikYilaUyar() {
        assertEquals(KefeDate(2028, 2, 29), YearMonth(2028, 2).lastDay())
        assertEquals(KefeDate(2026, 2, 28), YearMonth(2026, 2).lastDay())
    }

    @Test
    fun tarihAyaAitMi() {
        assertTrue(KefeDate(2026, 10, 31) in YearMonth(2026, 10))
        assertFalse(KefeDate(2026, 11, 1) in YearMonth(2026, 10))
    }

    @Test
    fun siralama() {
        assertTrue(YearMonth(2026, 12) < YearMonth(2027, 1))
        assertEquals(3, YearMonth(2027, 1).monthsSince(YearMonth(2026, 10)))
    }

    @Test
    fun gecersizAyReddedilir() {
        assertFailsWith<IllegalArgumentException> { YearMonth(2026, 13) }
    }

    @Test
    fun etiket() {
        assertEquals("Ekim 2026", YearMonth(2026, 10).label())
    }
}

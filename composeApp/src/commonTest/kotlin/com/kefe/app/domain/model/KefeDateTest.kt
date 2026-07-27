package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class KefeDateTest {

    @Test
    fun epochBaslangici() {
        assertEquals(0L, KefeDate(1970, 1, 1).toEpochDay())
    }

    @Test
    fun bilinenBirTarih() {
        // 1970-01-01 ile 2000-03-01 arasi 11017 gun
        assertEquals(11017L, KefeDate(2000, 3, 1).toEpochDay())
    }

    @Test
    fun artikGun() {
        // 29 Subat 2024 gercek bir gun; 28 Subat'tan 1 gun sonra
        assertEquals(1L, KefeDate(2024, 2, 29).daysSince(KefeDate(2024, 2, 28)))
        assertEquals(1L, KefeDate(2024, 3, 1).daysSince(KefeDate(2024, 2, 29)))
    }

    @Test
    fun artikYil366Gun() {
        // 2024 artik yil - 4'e bolunuyor, 100'e bolunmuyor
        assertEquals(366L, KefeDate(2025, 1, 1).daysSince(KefeDate(2024, 1, 1)))
    }

    @Test
    fun yuzyilKurali365Gun() {
        // 2100 artik yil DEGIL - 100'e bolunuyor, 400'e bolunmuyor
        assertEquals(365L, KefeDate(2100, 1, 1).daysSince(KefeDate(2099, 1, 1)))
        assertEquals(365L, KefeDate(2101, 1, 1).daysSince(KefeDate(2100, 1, 1)))
        // 2000 ise 400'e bolundugu icin artik yil
        assertEquals(366L, KefeDate(2001, 1, 1).daysSince(KefeDate(2000, 1, 1)))
    }

    @Test
    fun daysSinceTersYondeNegatif() {
        assertEquals(-366L, KefeDate(2024, 1, 1).daysSince(KefeDate(2025, 1, 1)))
        assertEquals(0L, KefeDate(2025, 6, 5).daysSince(KefeDate(2025, 6, 5)))
    }

    @Test
    fun plusMonthsYilAtlar() {
        assertEquals(KefeDate(2027, 2, 1), KefeDate(2026, 11, 1).plusMonths(3))
        assertEquals(KefeDate(2026, 12, 1), KefeDate(2026, 11, 1).plusMonths(1))
        assertEquals(KefeDate(2026, 8, 1), KefeDate(2026, 11, 1).plusMonths(-3))
    }
}

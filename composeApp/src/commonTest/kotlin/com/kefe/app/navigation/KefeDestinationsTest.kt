package com.kefe.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Navigasyon listeleri.
 *
 * Alt barda yalniz dort sekmeye yer var ve aylik plan birine ihtiyac duydu:
 * Plan, Ayarlar'in yerini aldi. Ayarlar telefonda/tablette disliyle itilir,
 * masaustu yan menusunde ise son satir olarak kalir.
 */
class KefeDestinationsTest {

    @Test
    fun `alt bar dort sekme - Plan Ayarlarin yerinde`() {
        assertEquals(listOf(SummaryKey, AssetsKey, GoalsKey, PlanKey), topLevelDestinations.map { it.key })
        assertEquals(listOf("Özet", "Varlıklar", "Hedefler", "Plan"), topLevelDestinations.map { it.label })
        assertFalse(topLevelDestinations.any { it.key == SettingsKey })
    }

    @Test
    fun `masaustu yan menu yedi satir - son satir Ayarlar`() {
        assertEquals(
            listOf(SummaryKey, AssetsKey, GoalsKey, PlanKey, MarketKey, ActivityKey, SettingsKey),
            desktopDestinations.map { it.key },
        )
        assertEquals(7, desktopDestinations.size)
        assertEquals(SettingsKey, desktopDestinations.last().key)
        assertEquals("Ayarlar", desktopDestinations.last().label)
    }
}

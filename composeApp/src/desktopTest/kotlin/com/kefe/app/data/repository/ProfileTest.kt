package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.repository.PreferenceKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Iki profil ve YEDEK TUZAGI.
 *
 * En kritik kural ekranda gorunmez: yedek dosyasi tercihler tablosunu oldugu
 * gibi tasidigi icin, cihaza ait olan "bu telefon kimin" bilgisi yedekle
 * gitmemeli. Volkan'in yedegini yukleyen Ayse'nin telefonu kendini Volkan
 * saniyordu - bunu ancak gercek bir veritabaniyla dogrulayabiliriz.
 */
private fun newDatabase(): KefeDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    KefeDatabase.Schema.create(driver)
    val database = createKefeDatabase(driver)
    database.bootstrapIfNeeded()
    return database
}

private fun newRepository(database: KefeDatabase) =
    SqlDelightPortfolioRepository(database, FixedKefeClock(), NoPrices())

class ProfileTest {

    @Test
    fun `bootstrap iki profil kurar`() = runTest {
        val repo = newRepository(newDatabase())
        val members = repo.observeMembers().first()

        assertEquals(2, members.size)
        assertTrue(members.any { it.id == LocalOwnerMemberId })
        assertTrue(members.any { it.id == LocalPartnerMemberId })
    }

    @Test
    fun `renameMember yalniz adi ve bas harfi degistirir`() = runTest {
        val database = newDatabase()
        val repo = newRepository(database)

        repo.renameMember(LocalOwnerMemberId, name = "Volkan", initials = "V")
        repo.renameMember(LocalPartnerMemberId, name = "Ayşe", initials = "A")

        val members = repo.observeMembers().first()
        val owner = members.first { it.id == LocalOwnerMemberId }
        val partner = members.first { it.id == LocalPartnerMemberId }
        assertEquals("Volkan", owner.name)
        assertEquals("V", owner.initials)
        assertEquals("Ayşe", partner.name)
        // sortOrder korundu: es hala ikinci sirada.
        assertEquals(LocalOwnerMemberId, members[0].id)
        assertEquals(LocalPartnerMemberId, members[1].id)
    }

    @Test
    fun `activeMemberId yedege ALINMAZ`() = runTest {
        val database = newDatabase()
        // Volkan'in telefonu: aktif profil sahip.
        database.settingQueries.upsertSetting(
            settingKey = PreferenceKeys.ActiveMemberId,
            settingValue = LocalOwnerMemberId,
        )
        val backup = newRepository(database).exportBackup(takenOn = "2026-07-30")

        // Yedek tercihi TASIR (ayni tercih tablosundan gelir) - bu beklenen.
        // Kritik olan: geri yuklerken ATLANMASI.
        assertTrue(PreferenceKeys.ActiveMemberId in backup.settings)
    }

    @Test
    fun `geri yukleme cihazin profilini DEGISTIRMEZ`() = runTest {
        // Volkan'in yedegi: aktif profil = sahip.
        val volkanDb = newDatabase()
        volkanDb.settingQueries.upsertSetting(
            settingKey = PreferenceKeys.ActiveMemberId,
            settingValue = LocalOwnerMemberId,
        )
        val volkanBackup = newRepository(volkanDb).exportBackup(takenOn = "2026-07-30")

        // Ayse'nin telefonu: aktif profil = es.
        val ayseDb = newDatabase()
        ayseDb.settingQueries.upsertSetting(
            settingKey = PreferenceKeys.ActiveMemberId,
            settingValue = LocalPartnerMemberId,
        )
        // Ayse, Volkan'in yedegini yukluyor.
        newRepository(ayseDb).restoreBackup(volkanBackup)

        // Ayse'nin telefonu HALA Ayse: aktif profil degismedi. Aksi halde
        // Ayse'nin girdigi her islem Volkan'a yazilirdi.
        val active = ayseDb.settingQueries
            .selectSetting(PreferenceKeys.ActiveMemberId)
            .executeAsOneOrNull()
        assertEquals(LocalPartnerMemberId, active)
    }

    @Test
    fun `geri yuklemede aktif profili olmayan cihaz da bozulmaz`() = runTest {
        // Henuz profil secmemis cihaz (activeMemberId yok).
        val freshDb = newDatabase()
        val volkanDb = newDatabase()
        volkanDb.settingQueries.upsertSetting(
            settingKey = PreferenceKeys.ActiveMemberId,
            settingValue = LocalOwnerMemberId,
        )
        val backup = newRepository(volkanDb).exportBackup(takenOn = "2026-07-30")

        newRepository(freshDb).restoreBackup(backup)

        // Yedekteki activeMemberId sizmadi: cihaz hala secimsiz, "bu telefon
        // kimin" adimi gosterilecek.
        assertNull(
            freshDb.settingQueries
                .selectSetting(PreferenceKeys.ActiveMemberId)
                .executeAsOneOrNull()
        )
    }
}

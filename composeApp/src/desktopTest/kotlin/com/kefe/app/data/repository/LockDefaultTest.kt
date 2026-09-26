package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.BootstrapKey
import com.kefe.app.data.db.BootstrapValue
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.db.seedSampleDataIfEmpty
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.backup.BackupFile
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.lockEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Acilis kilidinin VARSAYILANI gercek bir veritabaninda.
 *
 * NEYDI: kilit varsayilan olarak acikti ve anahtar yalniz Ayarlar'daki anahtara
 * dokunulunca yaziliyordu. Yeni kurulum, hic istemedigi bir kilitle aciliyordu.
 * Karar (kullanici onayli): YENI veritabanlari acikca "false" yazar; anahtari
 * olmayan ESKI kurulumlar acik kalir. Bu ayrim yalniz diskteki satirlarla
 * dogrulanabilir - o yuzden bellek-ici JDBC.
 */
class LockDefaultTest {

    private fun emptyDatabase(): KefeDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        return createKefeDatabase(driver)
    }

    private fun KefeDatabase.lockValue(): String? =
        settingQueries.selectSetting(PreferenceKeys.BiometricLock).executeAsOneOrNull()

    private fun KefeDatabase.put(key: String, value: String) =
        settingQueries.upsertSetting(settingKey = key, settingValue = value)

    private suspend fun KefeDatabase.readsLocked(): Boolean =
        SqlDelightPreferencesRepository(this).observeAll().first().lockEnabled()

    private fun repository(database: KefeDatabase) =
        SqlDelightPortfolioRepository(database, FixedKefeClock(millis = 1_000L), NoPrices())

    @Test
    fun `yeni kurulum kilidi KAPALI yazar`() = runTest {
        val db = emptyDatabase()
        db.bootstrapIfNeeded()

        assertEquals("false", db.lockValue())
        assertFalse(db.readsLocked())
    }

    @Test
    fun `eski kurulumun anahtari eksik kalir ve ACIK okunur`() = runTest {
        // Bu degisiklikten once kurulmus telefon: bootstrapVersion var, kilit
        // anahtari hic yazilmamis.
        val db = emptyDatabase()
        db.put(BootstrapKey, BootstrapValue)

        db.bootstrapIfNeeded()

        assertNull(db.lockValue(), "kurulum eski telefona dokunmamali")
        assertTrue(db.readsLocked(), "anahtari olmayan eski kurulum kilitli kalmali")
    }

    @Test
    fun `tum verileri sil kilidi KAPALI ve kurulumu yazili birakir`() = runTest {
        val db = emptyDatabase()
        db.bootstrapIfNeeded()
        db.put(PreferenceKeys.BiometricLock, "true")

        repository(db).deleteAllData()

        // Ayni surecte hemen: eksik anahtar "acik" okunurdu.
        assertEquals("false", db.lockValue())
        assertFalse(db.readsLocked())
        assertEquals(BootstrapValue, db.settingQueries.selectSetting(BootstrapKey).executeAsOneOrNull())

        // Sonraki acilis da ayni kalir.
        db.bootstrapIfNeeded()
        assertEquals("false", db.lockValue())
    }

    @Test
    fun `tum verileri sil kurulumu yeniden calistirir ve eksik es profilini onarir`() = runTest {
        // Es profilini henuz kurmayan eski bir surumden kalan veritabani: tek
        // profil, kurulum bayragi yazili. Silme once bayragi de siliyordu ve
        // sonraki acilistaki kurulum eksik profili geri ekliyordu; bayrak artik
        // silmenin icinde yazildigi icin kurulum da orada calismali.
        val db = emptyDatabase()
        db.bootstrapIfNeeded()
        db.portfolioQueries.deleteMemberById(LocalPartnerMemberId)
        db.portfolioQueries.renameMember(
            name = "Volkan",
            initials = "V",
            updatedAt = 5L,
            id = LocalOwnerMemberId,
        )

        repository(db).deleteAllData()

        val members = db.portfolioQueries.selectMembers().executeAsList()
        assertEquals(listOf(LocalOwnerMemberId, LocalPartnerMemberId), members.map { it.id })
        // Var olan profile dokunulmaz (INSERT OR IGNORE); yalniz eksik olan gelir.
        assertEquals("Volkan", members.first { it.id == LocalOwnerMemberId }.name)
        assertEquals("Eşim", members.first { it.id == LocalPartnerMemberId }.name)
        assertEquals(BootstrapValue, db.settingQueries.selectSetting(BootstrapKey).executeAsOneOrNull())
        assertEquals("false", db.lockValue())
    }

    @Test
    fun `eski kurulumda tum verileri sil de kilidi kapatir`() = runTest {
        val db = emptyDatabase()
        db.put(BootstrapKey, BootstrapValue)

        repository(db).deleteAllData()

        assertEquals("false", db.lockValue())
    }

    @Test
    fun `kullanicinin actigi kilit yeniden kurulumda kapanmaz`() = runTest {
        val db = emptyDatabase()
        db.bootstrapIfNeeded()
        db.put(PreferenceKeys.BiometricLock, "true")

        // Kurulum bayragi yerindeyken kurulum hic calismaz...
        db.bootstrapIfNeeded()
        assertEquals("true", db.lockValue())

        // ...bayrak kaybolup kurulum TEKRAR calissa bile anahtar yalniz
        // eksikse yazilir.
        db.settingQueries.deleteSetting(BootstrapKey)
        db.bootstrapIfNeeded()
        assertEquals("true", db.lockValue())
    }

    @Test
    fun `yedekteki kilit geri yuklenmez`() = runTest {
        // Kilitsiz yeni telefon, kilidi acik bir telefonun ESKI yedegini yukluyor
        // (eski surumler cihaz tercihlerini yedege yaziyordu).
        val db = emptyDatabase()
        db.bootstrapIfNeeded()
        val oldBackup = BackupFile(
            takenOn = "2026-09-01",
            portfolioName = "Birikimlerim",
            settings = mapOf(
                PreferenceKeys.BiometricLock to "true",
                PreferenceKeys.HideBalanceOnStart to "false",
                PreferenceKeys.ShowCents to "true",
            ),
        )

        repository(db).restoreBackup(oldBackup)

        assertEquals("false", db.lockValue())
        assertNull(
            db.settingQueries.selectSetting(PreferenceKeys.HideBalanceOnStart).executeAsOneOrNull(),
            "bakiyeyi gizleme de cihaza ait",
        )
        // Cihaza ait OLMAYAN tercih yine gelir.
        assertEquals("true", db.settingQueries.selectSetting(PreferenceKeys.ShowCents).executeAsOneOrNull())
    }

    @Test
    fun `eski kurulumun kilidi geri yuklemede kaybolmaz`() = runTest {
        // Anahtari olmayan (kilitli) eski telefon, "false" tasiyan bir yedek yukluyor.
        val db = emptyDatabase()
        db.put(BootstrapKey, BootstrapValue)

        repository(db).restoreBackup(
            BackupFile(
                takenOn = "2026-09-01",
                portfolioName = "Birikimlerim",
                settings = mapOf(
                    BootstrapKey to BootstrapValue,
                    PreferenceKeys.BiometricLock to "false",
                ),
            ),
        )

        assertNull(db.lockValue())
        assertTrue(db.readsLocked())
    }

    @Test
    fun `cihaza ait tercihler yedege yazilmaz`() = runTest {
        val db = emptyDatabase()
        db.bootstrapIfNeeded()
        db.put(PreferenceKeys.BiometricLock, "true")
        db.put(PreferenceKeys.HideBalanceOnStart, "false")
        db.put(PreferenceKeys.ActiveMemberId, "member_owner")
        db.put(PreferenceKeys.LastPushedAt, "123")
        db.put(PreferenceKeys.ThemeMode, "Dark")

        val settings = repository(db).exportBackup(takenOn = "2026-09-26").settings

        assertFalse(PreferenceKeys.BiometricLock in settings)
        assertFalse(PreferenceKeys.HideBalanceOnStart in settings)
        assertFalse(PreferenceKeys.ActiveMemberId in settings)
        assertFalse(PreferenceKeys.LastPushedAt in settings)
        assertEquals("Dark", settings[PreferenceKeys.ThemeMode])
    }

    @Test
    fun `ornek veri tohumu da kilidi KAPALI yazar`() = runTest {
        val db = emptyDatabase()
        db.seedSampleDataIfEmpty(KefeDate(2026, 9, 26))

        assertEquals("false", db.lockValue())
        assertFalse(db.readsLocked())
    }
}

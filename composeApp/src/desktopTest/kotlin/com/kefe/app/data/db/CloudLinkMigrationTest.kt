package com.kefe.app.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.sync.CloudMode
import com.kefe.app.data.sync.CloudStatus
import com.kefe.app.data.sync.deriveCloudMode
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Hesap baglantisi gocu: baglanti anahtarindan ONCEKI kurulumlar.
 *
 * NEDEN. Esitleme artik yalniz cihaz bir hesaba BAGLIYKEN calisiyor. Gocsuz,
 * aylardir esitlenen iki telefon guncellemeden sonra "Bağlantı yarım" der ve
 * esitlemeyi birakirdi. Kural yalniz diskteki satirlarla dogrulanabilir -
 * bellek-ici JDBC.
 */
class CloudLinkMigrationTest {

    private fun database(): KefeDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        return createKefeDatabase(driver).also { it.bootstrapIfNeeded() }
    }

    private fun KefeDatabase.session(userId: String = "u1", email: String = "e@k.app") =
        authSessionQueries.upsertSession(
            userId = userId,
            email = email,
            accessToken = "tok",
            refreshToken = "r",
            expiresAtEpochSeconds = 0L,
        )

    private fun KefeDatabase.put(key: String, value: String) =
        settingQueries.upsertSetting(settingKey = key, settingValue = value)

    private fun KefeDatabase.setting(key: String): String? =
        settingQueries.selectSetting(key).executeAsOneOrNull()

    /** Diskteki oturum ve baglantidan, acilistaki mod. */
    private fun KefeDatabase.mode(): CloudMode? {
        val row = authSessionQueries.selectSession().executeAsOneOrNull()
        val auth = if (row == null) {
            AuthState.SignedOut
        } else {
            AuthState.SignedIn(AuthSession(row.userId, row.email, "tok", "r", 0L))
        }
        return deriveCloudMode(
            auth = auth,
            linkUserId = setting(PreferenceKeys.CloudLinkUserId),
            linkEmail = setting(PreferenceKeys.CloudLinkEmail),
            status = CloudStatus.Synced,
        )
    }

    /** Esitlenen eski telefon: guncellemeden sonra hicbir sey gorunur degismez. */
    @Test
    fun `oturum ve watermark varsa baglanti yazilir`() {
        val db = database()
        db.session()
        db.put(PreferenceKeys.LastPushedAt, "123")

        db.migrateCloudLinkIfNeeded()

        assertEquals("u1", db.setting(PreferenceKeys.CloudLinkUserId))
        assertEquals("e@k.app", db.setting(PreferenceKeys.CloudLinkEmail))
        assertEquals("1", db.setting(PreferenceKeys.CloudLinkMigrated))
        assertEquals(CloudMode.Cloud("e@k.app", CloudStatus.Synced), db.mode())
    }

    /**
     * Girisli ama hic push'lamamis (orn. "Bağlanmadan devam et"): hesap hic
     * indirilmemis olabilir, baglanti YAZILMAZ - "Bağlantı yarım".
     */
    @Test
    fun `oturum var watermark yoksa baglanti yarim kalir`() {
        val db = database()
        db.session()

        db.migrateCloudLinkIfNeeded()

        assertNull(db.setting(PreferenceKeys.CloudLinkUserId))
        assertEquals("1", db.setting(PreferenceKeys.CloudLinkMigrated))
        assertEquals(CloudMode.LinkPending("e@k.app"), db.mode())
    }

    @Test
    fun `oturum yoksa bu cihazda`() {
        val db = database()
        db.put(PreferenceKeys.LastPushedAt, "123")

        db.migrateCloudLinkIfNeeded()

        assertNull(db.setting(PreferenceKeys.CloudLinkUserId))
        assertEquals(CloudMode.Local, db.mode())
    }

    /** Goc TEK sefer: isaretten sonra gelen oturum baglanti kazanmaz. */
    @Test
    fun `goc bir kez calisir`() {
        val db = database()
        db.migrateCloudLinkIfNeeded()

        db.session()
        db.put(PreferenceKeys.LastPushedAt, "123")
        db.migrateCloudLinkIfNeeded()

        assertNull(db.setting(PreferenceKeys.CloudLinkUserId))
    }

    /** Var olan baglanti ezilmez - goc yalniz eksigi tamamlar. */
    @Test
    fun `var olan baglantiya dokunulmaz`() {
        val db = database()
        db.session(userId = "u1")
        db.put(PreferenceKeys.LastPushedAt, "123")
        db.put(PreferenceKeys.CloudLinkUserId, "u2")

        db.migrateCloudLinkIfNeeded()

        assertEquals("u2", db.setting(PreferenceKeys.CloudLinkUserId))
    }

    /**
     * "Tüm verileri sil" baglantiyi geri getirmez. Silme aninda yoldaki bir
     * push, silmeden SONRA watermark'i yeniden yazar; oturum da durur. Isaret
     * silinmis kalsaydi bir sonraki acilista goc baglantiyi yeniden yazar ve
     * hesap, "bu telefon kimin" sorulmadan silinen veritabanina inerdi.
     */
    @Test
    fun `tum verileri sil sonrasi goc baglantiyi geri yazmaz`() = runTest {
        val db = database()
        val repo = SqlDelightPortfolioRepository(db, FixedKefeClock(millis = 1_000L), NoPrices())
        db.session()
        db.put(PreferenceKeys.LastPushedAt, "123")
        db.migrateCloudLinkIfNeeded()
        assertEquals("u1", db.setting(PreferenceKeys.CloudLinkUserId))

        repo.deleteAllData()
        // Silmeden once baslamis push simdi biter ve watermark'i yazar.
        db.put(PreferenceKeys.LastPushedAt, "456")
        // Bir sonraki acilis.
        db.migrateCloudLinkIfNeeded()

        assertNull(db.setting(PreferenceKeys.CloudLinkUserId))
        assertEquals(CloudMode.LinkPending("e@k.app"), db.mode())
    }

    @Test
    fun `saf kural`() {
        assertEquals("u1" to "e@k.app", cloudLinkToMigrate("u1", "e@k.app", "5", null))
        assertNull(cloudLinkToMigrate("u1", "e@k.app", null, null))
        assertNull(cloudLinkToMigrate(null, null, "5", null))
        assertNull(cloudLinkToMigrate("", "e@k.app", "5", null))
        assertNull(cloudLinkToMigrate("u1", "e@k.app", "5", "u9"))
    }

    /**
     * Baglanti CIHAZA aittir: yedege yazilmaz, geri yuklemede korunur. Aksi
     * halde hesapli telefonun yedegini yukleyen hesapsiz cihaz kendini o hesaba
     * bagli sanir ve oturumu yokken "Oturum kapandı" derdi.
     */
    @Test
    fun `baglanti anahtarlari yedege girmez ve geri yuklemede korunur`() = runTest {
        val db = database()
        val repo = SqlDelightPortfolioRepository(db, FixedKefeClock(millis = 1_000L), NoPrices())
        db.put(PreferenceKeys.CloudLinkUserId, "u1")
        db.put(PreferenceKeys.CloudLinkEmail, "e@k.app")
        db.put(PreferenceKeys.LastSyncedAt, "900")
        db.put(PreferenceKeys.CloudLinkMigrated, "1")
        db.put(PreferenceKeys.LocalRestoredAt, "800")

        val backup = repo.exportBackup(takenOn = "2026-09-26")
        val deviceKeys = listOf(
            PreferenceKeys.CloudLinkUserId,
            PreferenceKeys.CloudLinkEmail,
            PreferenceKeys.LastSyncedAt,
            PreferenceKeys.CloudLinkMigrated,
            PreferenceKeys.LocalRestoredAt,
        )
        deviceKeys.forEach { key -> assertFalse(key in backup.settings, "$key yedege girmemeli") }

        // Baska bir telefonun (kendi baglantisini tasiyan) yedegi yuklenir.
        val foreign = backup.copy(
            settings = backup.settings + (PreferenceKeys.CloudLinkUserId to "baskasi"),
        )
        repo.restoreBackup(foreign)

        assertEquals("u1", db.setting(PreferenceKeys.CloudLinkUserId))
        assertEquals("e@k.app", db.setting(PreferenceKeys.CloudLinkEmail))
    }
}

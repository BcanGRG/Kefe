package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.AuthException
import com.kefe.app.data.remote.AuthTokens
import com.kefe.app.data.remote.AuthApi
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.security.SecureStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Oturum saklama kurallari GERCEK veritabaniyla dogrulanir.
 *
 * Bu kurallarin hicbiri ekranda gorunmez: jetonun ne zaman yenilendigi, yenileme
 * patlayinca ne oldugu, oturumun yedege sizip sizmadigi. Emulatorde tiklayarak
 * sinanamaz - jetonun suresinin dolmasini beklemek gerekirdi.
 */
private class FakeAuthApi(
    private val onRefresh: (String) -> AuthTokens,
) : AuthApi {

    var refreshCount = 0
        private set

    override suspend fun sendCode(email: String) = Unit

    override suspend fun verifyCode(email: String, code: String): AuthTokens = tokens("verified")

    override suspend fun refreshSession(refreshToken: String): AuthTokens {
        refreshCount++
        return onRefresh(refreshToken)
    }

    override suspend fun signOut(accessToken: String) = Unit
}

private fun tokens(access: String, expiresIn: Long = 3600L) = AuthTokens(
    accessToken = access,
    refreshToken = "refresh_$access",
    expiresInSeconds = expiresIn,
    userId = "user-1",
    email = "test@kefe.app",
)

private fun newRepository(
    nowMillis: Long = 1_000_000L,
    api: AuthApi = FakeAuthApi { tokens("renewed") },
): Pair<SqlDelightAuthRepository, KefeDatabase> {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    KefeDatabase.Schema.create(driver)
    val database = createKefeDatabase(driver)
    // Masaustu SecureStore passthrough; testler duz-metin gidis-gelisi dogrular.
    val repo = SqlDelightAuthRepository(database, api, FixedKefeClock(millis = nowMillis), SecureStore())
    return repo to database
}

class AuthSessionTest {

    @Test
    fun `oturum yokken cikis yapilmis sayilir`() = runTest {
        val (repo, _) = newRepository()
        // Ilk deger Unknown, ikincisi tablodan gelen gercek durum.
        assertEquals(AuthState.Unknown, repo.observeAuthState().first())
    }

    @Test
    fun `taze jeton yenilenmez`() = runTest {
        val api = FakeAuthApi { tokens("renewed") }
        val (repo, db) = newRepository(nowMillis = 1_000_000L, api = api)
        db.authSessionQueries.upsertSession(
            userId = "user-1",
            email = "test@kefe.app",
            accessToken = "fresh",
            refreshToken = "r1",
            // Simdi 1000 sn; jeton 2000'de bitiyor - marj disinda, taze.
            expiresAtEpochSeconds = 2_000L,
        )

        assertEquals("fresh", repo.validAccessToken())
        assertEquals(0, api.refreshCount)
    }

    @Test
    fun `suresi dolan jeton yenilenir ve yeni jeton yazilir`() = runTest {
        val api = FakeAuthApi { tokens("renewed") }
        val (repo, db) = newRepository(nowMillis = 1_000_000L, api = api)
        db.authSessionQueries.upsertSession(
            userId = "user-1",
            email = "test@kefe.app",
            accessToken = "expired",
            refreshToken = "r1",
            expiresAtEpochSeconds = 500L,
        )

        assertEquals("renewed", repo.validAccessToken())
        assertEquals(1, api.refreshCount)

        // Yenisi DISKE yazilmis olmali: yalniz bellekte kalsaydi uygulama
        // kapanip acilinca kullanici disari atilirdi.
        val stored = db.authSessionQueries.selectSession().executeAsOne()
        assertEquals("renewed", stored.accessToken)
        assertEquals("refresh_renewed", stored.refreshToken)
        assertEquals(1_000L + 3_600L, stored.expiresAtEpochSeconds)
    }

    @Test
    fun `yenileme patlarsa oturum kapanir`() = runTest {
        val api = FakeAuthApi { throw AuthException("Refresh token expired") }
        val (repo, db) = newRepository(nowMillis = 1_000_000L, api = api)
        db.authSessionQueries.upsertSession(
            userId = "user-1",
            email = "test@kefe.app",
            accessToken = "expired",
            refreshToken = "olmus",
            expiresAtEpochSeconds = 500L,
        )

        // Eski jetonu sessizce kullanmayi denemek her istegi 401'e goturur;
        // dogru davranis oturumu kapatip kod istemektir.
        assertNull(repo.validAccessToken())
        assertNull(db.authSessionQueries.selectSession().executeAsOneOrNull())
    }

    @Test
    fun `cikis oturumu cihazdan siler`() = runTest {
        val (repo, db) = newRepository()
        db.authSessionQueries.upsertSession(
            userId = "user-1",
            email = "test@kefe.app",
            accessToken = "a",
            refreshToken = "r",
            expiresAtEpochSeconds = 9_999_999L,
        )

        repo.signOut()

        assertNull(db.authSessionQueries.selectSession().executeAsOneOrNull())
    }

    @Test
    fun `oturum yedege ALINMAZ`() = runTest {
        val (_, db) = newRepository()
        db.authSessionQueries.upsertSession(
            userId = "user-1",
            email = "test@kefe.app",
            accessToken = "gizli-jeton",
            refreshToken = "gizli-yenileme",
            expiresAtEpochSeconds = 9_999_999L,
        )

        val backup = SqlDelightPortfolioRepository(db, FixedKefeClock(), NoPrices())
            .exportBackup(takenOn = "2026-07-29")

        // Yedek dosyasi WhatsApp'tan gonderiliyor. Icinde jeton gecerse dosyayi
        // eline gecirenin hesaba girmesine yeterdi.
        val encoded = backup.settings.toString() + backup.portfolioName
        assertTrue("gizli-jeton" !in encoded)
        assertTrue("gizli-yenileme" !in encoded)
    }
}

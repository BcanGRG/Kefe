package com.kefe.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.kefe.app.data.remote.AuthException
import com.kefe.app.data.remote.AuthTokens
import com.kefe.app.data.remote.AuthApi
import com.kefe.app.data.remote.SupabaseConfig
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.security.SecureStore
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Oturumu cihazdaki veritabaninda tutar.
 *
 * NEDEN TERCIHLER TABLOSUNDA DEGIL: yedek dosyasi tercihler tablosunun tamamini
 * icine aliyor. Oturum oraya yazilsaydi yenileme jetonu, kullanicinin Drive'a
 * attigi ya da WhatsApp'tan gonderdigi yedek dosyasinin icinde giderdi. Ayri
 * tablo (bkz. 3.sqm) yedegin disindadir.
 *
 * JETON SERTLESTIRME: erisim ve yenileme jetonu artik [SecureStore] ile
 * sifrelenip yle saklanir - Android'de Keystore, kolonlar sifreli metin tutar.
 * Bu surumden onceki duz-metin oturumlar patlamaz: [SecureStore.reveal] cozemedigi
 * metni oldugu gibi dondurur, ilk yenilemede kendiliginden sifreliye doner.
 */
class SqlDelightAuthRepository(
    private val database: KefeDatabase,
    private val api: AuthApi,
    private val clock: KefeClock,
    private val secureStore: SecureStore,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : AuthRepository {

    private val queries = database.authSessionQueries

    /**
     * Yenileme ayni anda tek. Iki senkron isi ayni anda suresi dolmus bir jetonu
     * gorurse ikisi de yenilemeye kalkar; ikinci istek ILK istegin tukettigi
     * yenileme jetonuyla gider ve reddedilir - kullanici sebepsiz yere disari
     * atilirdi.
     */
    private val refreshMutex = Mutex()

    override val isCloudConfigured: Boolean get() = SupabaseConfig.isConfigured

    override fun observeAuthState(): Flow<AuthState> =
        queries.selectSession().asFlow().mapToOneOrNull(dispatcher)
            .map { row ->
                if (row == null) {
                    AuthState.SignedOut
                } else {
                    AuthState.SignedIn(
                        AuthSession(
                            userId = row.userId,
                            email = row.email,
                            accessToken = secureStore.reveal(row.accessToken),
                            refreshToken = secureStore.reveal(row.refreshToken),
                            expiresAtEpochSeconds = row.expiresAtEpochSeconds,
                        )
                    )
                }
            }
            // Tablo okunana kadar "cikis yapilmis" DEMEYIZ: ilk kare "Giriş yap"
            // ekranini gosterir, hemen ardindan ana ekrana atlardi.
            .onStart { emit(AuthState.Unknown) }

    override suspend fun sendCode(email: String): Result<Unit> = runCatching {
        requireConfigured()
        api.sendCode(email.trim())
    }

    override suspend fun verifyCode(email: String, code: String): Result<Unit> = runCatching {
        requireConfigured()
        val tokens = api.verifyCode(email.trim(), code.trim())
        store(tokens)
    }

    override suspend fun validAccessToken(): String? = refreshMutex.withLock {
        val row = withContext(dispatcher) { queries.selectSession().executeAsOneOrNull() }
            ?: return@withLock null

        val now = clock.nowEpochMillis() / 1000L
        // Tam bitis aninda degil, biraz ONCE yenileriz: istek yolda iken jetonun
        // dolmasi tek basina bir 401 demektir.
        if (now < row.expiresAtEpochSeconds - ExpiryMarginSeconds) {
            return@withLock secureStore.reveal(row.accessToken)
        }

        runCatching { api.refreshSession(secureStore.reveal(row.refreshToken)) }
            .onSuccess { store(it, fallbackEmail = row.email, fallbackUserId = row.userId) }
            .map { it.accessToken }
            // Yenileme jetonu da gecersizse geri donusu yok: oturum kapanir ve
            // kullanici tekrar kod ister. Sessizce eski jetonu kullanmayi denemek
            // her istegi 401'e goturur.
            .getOrElse {
                clearSession()
                null
            }
    }

    override suspend fun signOut() {
        val row = withContext(dispatcher) { queries.selectSession().executeAsOneOrNull() }
        // ONCE cihazdaki kayit silinir. Sunucuya haber vermek iyi olur ama sart
        // degil; ag yokken "cikis yapamadiniz" demek kullaniciyi hesabinda esir
        // birakirdi.
        clearSession()
        row?.let { api.signOut(secureStore.reveal(it.accessToken)) }
    }

    private suspend fun store(
        tokens: AuthTokens,
        fallbackEmail: String = "",
        fallbackUserId: String = "",
    ) {
        withContext(dispatcher) {
            queries.upsertSession(
                userId = tokens.userId.ifBlank { fallbackUserId },
                email = tokens.email.ifBlank { fallbackEmail },
                // Kolonlara SIFRELI metin yazariz; okurken reveal ile cozeriz.
                accessToken = secureStore.protect(tokens.accessToken),
                refreshToken = secureStore.protect(tokens.refreshToken),
                expiresAtEpochSeconds = clock.nowEpochMillis() / 1000L + tokens.expiresInSeconds,
            )
        }
    }

    private suspend fun clearSession() {
        withContext(dispatcher) { queries.deleteSession() }
    }

    private fun requireConfigured() {
        if (!SupabaseConfig.isConfigured) {
            throw AuthException("Bulut hesabı bu sürümde yapılandırılmamış")
        }
    }

    private companion object {
        /** Bitisine bir dakika kala yenile. */
        const val ExpiryMarginSeconds = 60L
    }
}

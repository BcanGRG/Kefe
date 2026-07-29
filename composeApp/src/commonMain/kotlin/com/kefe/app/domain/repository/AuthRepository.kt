package com.kefe.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Cihazda acik oturum.
 *
 * [expiresAtEpochSeconds] erisim jetonunun bittigi an. Yenileme bu damga gecince
 * ISTEK ANINDA yapilir - arka planda saat isletmeyiz. Bir zamanlayici kurup her
 * dakika "doldu mu" diye bakmak, uygulama kapaliyken zaten calismayan ve aciksa
 * da bos yere pil yakan bir cozumdur.
 */
data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
)

/**
 * Kimlik durumu.
 *
 * [Unknown] yalniz ACILIS ANINDA gecerlidir: veritabani okunana kadar kullanici
 * girisli mi degil mi bilinmez. Ayri bir durum olmasaydi acilista bir kare
 * "giris yapin" ekrani parlar, sonra ana ekrana atlardi.
 */
sealed interface AuthState {
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val session: AuthSession) : AuthState
}

interface AuthRepository {

    fun observeAuthState(): Flow<AuthState>

    /** Bulut ozellikleri yapilandirilmis mi (anahtarlar girilmis mi). */
    val isCloudConfigured: Boolean

    /** E-postaya tek kullanimlik giris kodu gonderir. */
    suspend fun sendCode(email: String): Result<Unit>

    /** Kodu dogrular ve oturumu cihaza yazar. */
    suspend fun verifyCode(email: String, code: String): Result<Unit>

    /**
     * Gecerli erisim jetonu. Suresi dolduysa yeniler; yenileme de basarisizsa
     * oturum kapanir ve null doner.
     *
     * Senkron isleri (7.4/7.5) her istekte bunu cagirir; jeton tazeligi tek
     * yerden yonetilsin diye.
     */
    suspend fun validAccessToken(): String?

    suspend fun signOut()
}

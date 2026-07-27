package com.kefe.app.ui.screens.account

/**
 * Giris akisinin ucuncu asamasi. Tasarimda ayri cerceveler olarak duruyor:
 * `login` (e-posta ile giris), `join` (yeni portfoy / davet kodu) ve `lock`
 * (cihaz kilidi). Kilit hesap girisi DEGILDIR: oturum acikken de her acilista
 * gelebilir, bu yuzden ayni ekranin bir asamasi olarak tutulur.
 */
enum class LoginStage { SignIn, Start, Locked }

/** Davet kodu tasarimda alti hane; kutu sayisi buradan turetilir. */
const val InviteCodeLength: Int = 6

data class LoginUiState(
    val stage: LoginStage = LoginStage.SignIn,

    // Giris
    val email: String = "",
    val emailError: String? = null,
    val sendingLink: Boolean = false,
    val linkSent: Boolean = false,

    // Yeni portfoy / katilma
    val inviteCode: String = "",
    val inviteError: String? = null,
    val joining: Boolean = false,

    // Kilit
    val portfolioName: String = "",
    val maskedTotalDigits: Int = 6,
    val unlocking: Boolean = false,
    val unlockError: String? = null,

    // Tek seferlik gezinme isaretleri
    val unlocked: Boolean = false,
    val portfolioCreated: Boolean = false,
) {
    /** Bos ya da bicimsiz e-posta ile baglanti gonderilmez. */
    val canSendLink: Boolean get() = !sendingLink && email.isValidEmail()

    val canJoin: Boolean get() = !joining && inviteCode.length == InviteCodeLength
}

/**
 * Tek "@" ve ondan sonra en az bir nokta. Ortak kodda regex yerine elle
 * bakilir - tarayici dogrulamasiyla ayni sertlikte olmasi gerekmiyor, amac
 * kullaniciyi bariz yanlisla gondermemek.
 */
internal fun String.isValidEmail(): Boolean {
    val at = indexOf('@')
    if (at <= 0 || at != lastIndexOf('@')) return false
    val domain = substring(at + 1)
    val dot = domain.indexOf('.')
    return dot > 0 && dot < domain.length - 1 && none { it.isWhitespace() }
}

sealed interface LoginIntent {
    data class ChangeEmail(val value: String) : LoginIntent
    data object SendMagicLink : LoginIntent
    data object SignInWithPassword : LoginIntent

    data object GoToStart : LoginIntent
    data object GoToSignIn : LoginIntent

    data object CreatePortfolio : LoginIntent
    data class ChangeInviteCode(val value: String) : LoginIntent
    data object Join : LoginIntent

    data object Unlock : LoginIntent
    data object UnlockWithPassword : LoginIntent
}

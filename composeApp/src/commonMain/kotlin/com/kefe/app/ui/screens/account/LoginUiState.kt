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

/** Supabase e-posta kodu da alti hane. */
const val LoginCodeLength: Int = 6

data class LoginUiState(
    val stage: LoginStage = LoginStage.SignIn,

    // Giris
    val email: String = "",
    val emailError: String? = null,
    val sendingCode: Boolean = false,
    /** Kod gonderildi - ekran kod kutusuna gecer. */
    val codeSent: Boolean = false,
    val code: String = "",
    val verifying: Boolean = false,
    /** Dogrulama basarili - cagiran taraf ana ekrana gecirir. */
    val signedIn: Boolean = false,

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
    /** Bos ya da bicimsiz e-posta ile kod gonderilmez. */
    val canSendCode: Boolean get() = !sendingCode && email.isValidEmail()

    val canVerify: Boolean get() = !verifying && code.length == LoginCodeLength

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
    data object SendCode : LoginIntent
    data class ChangeCode(val value: String) : LoginIntent
    data object VerifyCode : LoginIntent

    /** Kod kutusundan e-posta adimina donus - yanlis adres yazilmis olabilir. */
    data object EditEmail : LoginIntent

    data object GoToStart : LoginIntent
    data object GoToSignIn : LoginIntent

    data object CreatePortfolio : LoginIntent
    data class ChangeInviteCode(val value: String) : LoginIntent
    data object Join : LoginIntent

    data object Unlock : LoginIntent
    data object UnlockWithPassword : LoginIntent
}

package com.kefe.app.ui.screens.account

/**
 * Giris ekraninin asamasi: `SignIn` (e-posta ile giris) ve `Locked` (cihaz
 * kilidi). Kilit hesap girisi DEGILDIR: oturum acikken de her acilista gelebilir,
 * bu yuzden ayni ekranin bir asamasi olarak tutulur.
 *
 * Eski `Start` (yeni portfoy / davet kodu) asamasi kaldirildi: davet akisi iki
 * kisilik tek hesap modelinde karsiliksiz, tek secenek "yeni portfoy" oldugu
 * icin ayri bir adim da bos bir duraktı.
 */
enum class LoginStage { SignIn, Locked }

/**
 * Kod kutusundaki hane sayisi - Supabase panelindeki "Email OTP Length" ile ayni.
 *
 * Bu yalniz KUTU SAYISIDIR. Gonderilebilirlik [MinLoginCodeLength] ile olculur;
 * ikisi ayri tutulur cunku sunucu ayari degistiginde kutu sayisi sasabilir ama
 * giris calismaya devam etmelidir. Once tam esitlik araniyordu ve ayar sekize
 * cikinca dugme hic acilmadi - hata da vermeden.
 */
const val LoginCodeLength: Int = 6

/** Supabase'in izin verdigi en kisa kod. */
const val MinLoginCodeLength: Int = 6

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
    /**
     * "Kodu tekrar gonder" icin geri sayim (saniye). 0 iken tekrar gonderilebilir.
     * Sunucunun OTP hiz sinirina takilmadan once kullaniciyi bekletir.
     */
    val resendCooldown: Int = 0,
    /** Dogrulama basarili - cagiran taraf ana ekrana gecirir. */
    val signedIn: Boolean = false,

    // Kilit
    val portfolioName: String = "",
    val maskedTotalDigits: Int = 6,
    val unlocking: Boolean = false,
    val unlockError: String? = null,

    // Tek seferlik gezinme isareti. "Yeni portfoy" icin bir bayrak
    // (portfolioCreated) da vardi ve HIC temizlenmiyordu: tanitimin ilk
    // sayfasindan geri donulunce bayrak hala true oldugu icin tanitim yeniden
    // aciliyor, giris ekranina donmek imkansizlasiyordu. Artik dogrudan
    // cagrilan bir geri donus.
    val unlocked: Boolean = false,
) {
    /** Bos ya da bicimsiz e-posta ile kod gonderilmez. */
    val canSendCode: Boolean get() = !sendingCode && email.isValidEmail()

    /**
     * Kod EN AZ [MinLoginCodeLength] hane olunca gonderilebilir - tam esitlik
     * aranmaz. Sunucudaki uzunluk ayari ile buradaki sabit ayrilinca dugme hic
     * acilmiyor ve hicbir sey de soylenmiyordu: giris sessizce calismaz oluyordu.
     * Uzunluk yanlissa artik sunucu soyler, kullanici da en azindan deneyebilir.
     */
    val canVerify: Boolean get() = !verifying && code.length >= MinLoginCodeLength
}

/**
 * Acilis kilidi bu acilista devrede mi.
 *
 * KILIT YALNIZ KURULUMDAN SONRA. Kilit varsayilan olarak acik ve once kurulum
 * durumuna bakmiyordu: YENI kurulumda bile ilk ekran "Kefe kilitli" oluyordu.
 * Parmak izi (ya da parmak izi yoksa dogrudan) acilinca uygulama giris formunu
 * HIC gostermeden "Profiller" ekranina geciyordu - hesabi olan kullanici
 * e-postasini yazacagi yeri hic gormeden yeni profil olusturmaya itiliyordu ve
 * telefon hesaba baglanmadigi icin kayitlar hic inmiyordu.
 *
 * Kurulmamis bir uygulamada saklanacak bakiye yok; perde ancak kurulumdan
 * sonra anlamli.
 */
internal fun isLaunchLocked(lockEnabled: Boolean, onboarded: Boolean, unlockedThisLaunch: Boolean): Boolean =
    lockEnabled && onboarded && !unlockedThisLaunch

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

    /** Ayni adrese yeni bir kod ister - kod gelmediyse ya da suresi dolduysa. */
    data object ResendCode : LoginIntent

    /**
     * Kabuk girisi karsiladi. VM surec boyunca yasiyor: `signedIn` true kalirsa
     * cikis yapip yeniden "Giriş yap" diyen kullanici e-posta adimini hic
     * gormeden iceri atiliyordu.
     */
    data object SignInHandled : LoginIntent

    /** Kilit asamasina gec - kabuk acilista cagirir. */
    data object Lock : LoginIntent

    data object Unlock : LoginIntent
}

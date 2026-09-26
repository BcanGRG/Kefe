package com.kefe.app.ui.screens.account

import com.kefe.app.data.sync.CloudMode

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
 * Acilis kilidi bu acilista devrede mi. Dort kosulun HEPSI gerekir:
 *
 * - [lockEnabled]: kullanici kilidi acmis (ya da eski kurulum; bkz. lockEnabled()).
 *
 * - [setupDone]: acilis akisi gecilmis VE bu telefonun profili secilmis. NEYDI:
 *   once yalniz "onboarded" bakiliyordu, o da profil seciminden ONCE yaziliyor.
 *   Yeni kurulumda "Atla" deyip profil ekraninda uygulamayi kapatan kullanici
 *   bir sonraki acilista, henuz hicbir kaydi yokken "Kefe kilitli" goruyordu;
 *   daha once de kilit acilinca giris formu hic gorunmeden "Profiller"e
 *   geciliyordu. Kurulmamis bir uygulamada saklanacak bakiye yok.
 *
 * - [gateAvailable]: cihazda kimlik sorulabiliyor. NEYDI: masaustunde ve
 *   parmak izi/ekran kilidi tanimsiz telefonda kilit ekrani bir an cizilip
 *   hemen aciliyordu (kilit orada perdeden bile degil, bos bir parlama).
 *
 * - ![unlockedThisLaunch]: bu acilista bir kez acildiysa tekrar sorulmaz.
 */
internal fun isLaunchLocked(
    lockEnabled: Boolean,
    setupDone: Boolean,
    gateAvailable: Boolean,
    unlockedThisLaunch: Boolean,
): Boolean = lockEnabled && setupDone && gateAvailable && !unlockedThisLaunch

/**
 * Kilit icin kurulum BITTI mi: acilis akisi gecilmis VE bu telefonun profili
 * secilmis. "onboarded" profil seciminden ONCE yazildigi icin tek basina yetmez
 * (bkz. [isLaunchLocked]); `null` = henuz diskten okunmadi, bitmemis sayilir.
 */
internal fun launchSetupDone(onboarded: Boolean?, activeMemberId: String?): Boolean =
    onboarded == true && activeMemberId != null

/**
 * Acilisin BASLANGIC degeri: kilitsiz baslayan acilis "acilmis" sayilir.
 *
 * KILIT YALNIZ ACILISTA. NEYDI: kilitsiz acilan uygulamada Ayarlar'dan kilit
 * acilinca `locked` surecin ortasinda true'ya donuyordu; ardindan "Tüm verileri
 * sil" koku LoginKey yapinca kilit asamasi takiliyor ve kullanici giris formu
 * yerine "Kilitli" goruyordu. Yeni acilan kilit bir sonraki acilista gecerli.
 */
internal fun unlockedAtLaunchStart(lockEnabled: Boolean, setupDone: Boolean, gateAvailable: Boolean): Boolean =
    !isLaunchLocked(lockEnabled, setupDone, gateAvailable, unlockedThisLaunch = false)

/**
 * LoginKey'in ekranda cizecegi durum. Asama YALNIZ kabuktan turetilir; VM'nin
 * kendi `stage`/`unlocked` alanlarina guvenilmez.
 *
 * LoginKey CIFT GOREVLI: acilis KILIDI (yigin koku iken) ve bulut GIRISI
 * (Ayarlar'dan itilince, ya da "Tüm verileri sil" sonrasi kok). AYNI VM surec
 * boyunca yasar ve stage=Locked / unlocked=true hic sifirlanmaz. NEYDI:
 *   - itilmis giriste kilit kalintisi parmak izi istemini acip unlocked
 *     etkisiyle kullaniciyi Ozet'e geri atiyordu;
 *   - "Tüm verileri sil" koku LoginKey yapinca eski unlocked=true enterApp'i
 *     hemen tetikliyor (giris formu hic gorunmuyordu) ya da eski Locked asamasi
 *     "Kilitli" ekranini geri getiriyordu.
 *
 * Bu yuzden: kok VE kilitliyse Locked (ILK KAREDEN - VM SignIn ile dogar,
 * kilit ancak etkiyle gelir; arada giris formu bir kare parliyordu) ve
 * [LoginUiState.unlocked] KORUNUR ki kilit acilinca kabuk iceri alsin. Aksi her
 * durumda temiz SignIn: kilit kalintisi (unlocked, unlockError) yok sayilir.
 */
internal fun loginScreenState(vm: LoginUiState, asRoot: Boolean, locked: Boolean): LoginUiState =
    if (asRoot && locked) {
        vm.copy(stage = LoginStage.Locked)
    } else {
        vm.copy(stage = LoginStage.SignIn, unlocked = false, unlockError = null)
    }

/**
 * Kod dogrulandiktan sonra "bu telefon kimin" adimi (ProfileSetup) sart mi.
 *
 * YALNIZ cihaz bu hesaba ZATEN bagliysa ([CloudMode.Cloud]: ayni hesaba
 * yeniden giris) ve profili seciliyse atlanir. Diger her durumda sorulur:
 *  - baglanti yok ya da baska bir hesaba ait ([CloudMode.LinkPending]): hesap
 *    once indirilmeli, profil HESABIN adlariyla secilmeli, baglanti ancak o
 *    zaman yazilir;
 *  - mod gelmediyse (null, bekleme doldu): guvenli yol.
 *
 * NEYDI: Ayarlar'dan giris yapan kurulu cihaz (profili zaten secili) dogrudan
 * Ozet'e gidiyordu. Hesabin adlari devralininca cihazin eski secimi
 * ("member_owner") kaliyor, telefon sessizce diger kisinin adina kayit
 * yaziyordu.
 */
internal fun profileSetupAfterSignIn(mode: CloudMode?, activeMemberId: String?): Boolean =
    !(mode is CloudMode.Cloud && activeMemberId != null)

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

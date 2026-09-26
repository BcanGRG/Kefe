package com.kefe.app.ui.screens.account

import com.kefe.app.data.sync.CloudMode

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

/**
 * Giris ekrani NEDEN acildi. Baslik ve not buna gore degisir (bkz. [signInCopy]);
 * akisin kendisi ayni: e-postaya kod, kodu yaz.
 */
enum class SignInPurpose {
    /** Hosgeldin'de "Hesapla, iki telefonda". */
    FirstRun,

    /** Hesapsiz kullanilan cihaz hesaba baglaniyor (profil adimi ya da Ayarlar). */
    Link,

    /** Bagli cihazin oturumu dustu; ayni hesaba yeniden giris. */
    Relogin,
}

/**
 * E-posta ile giris ekraninin durumu. Kilit burada DEGIL (bkz. [LockUiState]):
 * ikisi ayni durumda durdugu icin kilit kalintisi girise siziyordu.
 */
data class LoginUiState(
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
    /** Dogrulama basarili - cagiran taraf bir sonraki adima gecirir. */
    val signedIn: Boolean = false,
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

/** E-posta adiminin dugmesi. "Giriş kodu gönder" degil: giris da kayit da ayni kod. */
const val SendCodeLabel: String = "Kod gönder"

/** Kod adiminin dugmesi. "Giriş yap" degil: hesap yoksa bu dugme onu acar. */
const val VerifyCodeLabel: String = "Doğrula"

/**
 * Giris ekraninin amaca gore metinleri.
 *
 * [warning]: iki telefonda AYNI e-posta. Tek akis (kayit == giris) oldugu icin
 * farkli bir e-posta hata vermez, sessizce ayri ve bos bir hesap acar - esin
 * telefonu hicbir kaydi gormez. Bunu yalniz metin onleyebilir.
 *
 * [extra]: yalniz baglamada - bu cihazda zaten kayit var ve nereye gidecekleri
 * soylenmeli.
 */
data class SignInCopy(
    val title: String,
    val note: String,
    val warning: String,
    val extra: String? = null,
)

/** Kayit ile giris ayni akis: kullanici "hesabim yok" diye takilmasin. */
private const val OneFlowNote: String =
    "Şifre yok: e-postanıza tek kullanımlık bir kod gelir. " +
        "Bu e-postayla hesap yoksa açılır, varsa hesabınıza girersiniz."

private const val SameEmailWarning: String =
    "İki telefonda da aynı e-postayı kullanın. Farklı bir e-posta ayrı ve boş bir hesap açar."

/**
 * Amaca gore baslik ve notlar. SAF: her amacin metni testte denenebilsin.
 *
 * Yeniden giriste "hesap yoksa acilir" yazilmaz: cihaz zaten bir hesaba bagli,
 * dogru e-posta belli (alan onunla dolu gelir). Oradaki tek soz, ayni
 * e-postayla girilince esitlemenin kaldigi yerden surmesi.
 */
fun signInCopy(purpose: SignInPurpose): SignInCopy = when (purpose) {
    SignInPurpose.FirstRun -> SignInCopy(
        title = "Hesapla başla",
        note = OneFlowNote,
        warning = SameEmailWarning,
    )
    SignInPurpose.Link -> SignInCopy(
        title = "Hesaba bağla",
        note = OneFlowNote,
        warning = SameEmailWarning,
        extra = "Bu cihazdaki kayıtlar hesaba eklenecek.",
    )
    SignInPurpose.Relogin -> SignInCopy(
        title = "Yeniden giriş yap",
        note = "Şifre yok: e-postanıza tek kullanımlık bir kod gelir. " +
            "Bu cihazın bağlı olduğu e-postayla girin; eşitleme kaldığı yerden sürer.",
        warning = SameEmailWarning,
    )
}

/** Giris ekraninda geri (sistem tusu ya da ust cubuktaki ok) ne yapar. */
enum class SignInBack {
    /** Dogrulama suruyor: hicbir sey. */
    Ignore,

    /** Kod kutusundan e-posta adimina. */
    EditEmail,

    /** Ekrandan cik - geldigi yere (hosgeldin, profil adimi, Ayarlar). */
    Leave,
}

/**
 * Geri ne yapar. SAF.
 *
 * DOGRULAMA SURERKEN GERI YOK. NEYDI: "Kontrol ediliyor…" iken iki kez geri
 * basilinca giris ekrani kapaniyor, dogrulama ise arkada bitiyordu - oturum
 * yaziliyor ama onu karsilayacak ekran yoktu; `signedIn` bayragi da takili
 * kaliyor, sonraki "Giriş yap" e-postayi sormadan geciyordu.
 *
 * Kod dogrulandiktan sonra da geri yok: kabuk bir sonraki adima geciriyor
 * (bkz. afterSignIn); arada e-posta adimina donmek bos bir kare gosterirdi.
 *
 * Kod kutusundayken geri once e-posta adimina doner: iki adim ayri gezinme
 * girdisi degil, tek ekranin durumu.
 */
internal fun signInBack(state: LoginUiState): SignInBack = when {
    state.verifying || state.signedIn -> SignInBack.Ignore
    state.codeSent -> SignInBack.EditEmail
    else -> SignInBack.Leave
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
 * sil" koku giris ekrani yapinca kilit asamasi takiliyor ve kullanici giris
 * formu yerine "Kilitli" goruyordu. Yeni acilan kilit bir sonraki acilista
 * gecerli.
 *
 * [unlockedBefore]: kilit bu Activity'de zaten acildi (LockViewModel'in
 * `unlocked`i). Activity yeniden yaratilinca (katlama, bolunmus ekran, dil)
 * Compose durumu sifirlanir ama VM kalir; bu bir ACILIS degil. NEYDI: kok
 * yeniden kilit oluyor, VM'de kalan `unlocked` kullaniciyi hemen iceri aliyor,
 * ayni karede kilit ekrani sistem istemini Ozet'in (bakiyelerin) ustunde
 * aciyordu.
 */
internal fun unlockedAtLaunchStart(
    lockEnabled: Boolean,
    setupDone: Boolean,
    gateAvailable: Boolean,
    unlockedBefore: Boolean = false,
): Boolean = unlockedBefore || !isLaunchLocked(lockEnabled, setupDone, gateAvailable, unlockedThisLaunch = false)

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

/** Kod dogrulandiktan sonra kabugun yapacagi gezinme. */
enum class AfterSignIn {
    /** Yigin sifirlanir, kok "bu telefon kimin" (hesap indirilir, secim sorulur). */
    ProfileSetup,

    /** Giris ekrani kapanir, cagiran ekrana (Ozet, Ayarlar) donulur. */
    ReturnToCaller,

    /** Yigin sifirlanir, kok Ozet. */
    EnterApp,
}

/**
 * Giristen sonra nereye. SAF.
 *
 * Profil adimi gerekmiyorsa (ayni hesaba yeniden giris, bkz.
 * [profileSetupAfterSignIn]) giris ekrani yalniz KAPANIR: kullanici "Yeniden
 * giriş yap"a Ozet'ten ya da Ayarlar'dan gelmisti, oraya doner. NEYDI: her
 * giris yigini sifirlayip Ozet'e atiyordu; Ayarlar'dan gelen Ayarlar'i kaybediyordu.
 *
 * [callerInShell]: giris ekraninin altinda uygulamanin bir sekmesi var mi
 * (hosgeldin ya da profil adimi degil).
 */
internal fun afterSignIn(mode: CloudMode?, activeMemberId: String?, callerInShell: Boolean): AfterSignIn =
    when {
        profileSetupAfterSignIn(mode, activeMemberId) -> AfterSignIn.ProfileSetup
        callerInShell -> AfterSignIn.ReturnToCaller
        else -> AfterSignIn.EnterApp
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
    /**
     * Giris ekrani ACILIYOR - kabuk itmeden hemen once gonderir. Onceki
     * denemenin her izi silinir ve yoldaki istekler iptal edilir; [email]
     * (yeniden giriste bagli hesabin adresi) alana yazilir.
     *
     * NEYDI: VM surec boyunca yasiyor ve giris ekrani hic sifirlanmiyordu.
     * Yarida birakilan bir girisin `signedIn`/`codeSent` bayragi kaliyor,
     * sonraki "Giriş yap" e-postayi sormadan geciyor ya da eski kod kutusunu
     * aciyordu.
     */
    data class Begin(val email: String? = null) : LoginIntent

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
}

// --- Acilis kilidi ---------------------------------------------------------

/**
 * Acilis kilidi ekraninin durumu. Giristen AYRI: kilit hesap girisi degildir,
 * oturum acikken de her acilista gelebilir. NEYDI: ikisi ayni ViewModel'in iki
 * asamasiydi; kilit kalintisi (stage=Locked, unlocked=true) hic sifirlanmiyor,
 * Ayarlar'dan acilan giriste parmak izi istemini aciyor ya da "Tüm verileri
 * sil" sonrasi giris formunu atliyordu.
 */
data class LockUiState(
    val portfolioName: String = "",
    val maskedTotalDigits: Int = 6,
    val unlocking: Boolean = false,
    val unlockError: String? = null,
    /**
     * Kilit bu Activity'de acildi; bir daha sorulmaz. KALICI (tek seferlik
     * degil): VM Activity yeniden yaratilinca da yasar ve kabuk bununla kilidin
     * zaten acildigini bilir (bkz. unlockedAtLaunchStart).
     */
    val unlocked: Boolean = false,
)

/**
 * Kimlik istemi acilabilir mi. SAF.
 *
 * Acilmis kilit tekrar sormaz. NEYDI: yalniz "istem acik mi" bakiliyordu;
 * yeniden yaratilan Activity'de kilit ekrani bir kare bestelenince acilmis
 * kilide ikinci bir sistem istemi aciliyor, istem iceri alinmis kullanicinin
 * ekraninda (bakiyelerin ustunde) kaliyordu.
 */
internal fun LockUiState.canStartUnlock(): Boolean = !unlocking && !unlocked

sealed interface LockIntent {
    data object Unlock : LockIntent
}

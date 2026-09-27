package com.kefe.app.ui.screens.account

import com.kefe.app.data.sync.CloudMode
import com.kefe.app.data.sync.CloudStatus
import com.kefe.app.ui.components.longLabel
import com.kefe.app.ui.format.trGenitive

/** Tema secimi. "Sistem" cihazin koyu/acik tercihini izler. */
enum class ThemeMode {
    Dark,
    Light,
    System;

    fun label(): String = when (this) {
        Dark -> "Koyu"
        Light -> "Açık"
        System -> "Sistem"
    }
}

data class SettingsMember(
    val name: String,
    val initials: String,
    val index: Int,
)

data class SettingsUiState(
    val portfolioName: String = "",
    val members: List<SettingsMember> = emptyList(),

    // Gorunum
    /**
     * Varsayilan SISTEM. Once Acik idi ve acilis penceresi koyu sabitti; cihazi
     * koyu modda olan biri her acilista once koyu bir kare, sonra acik bir ekran
     * goruyordu. Sistemi izlemek ikisini kendiliginden hizalar.
     */
    val themeMode: ThemeMode = ThemeMode.System,
    val showCents: Boolean = false,

    // Gizlilik
    val hideBalanceOnStart: Boolean = true,

    /**
     * Acilis kilidi GERCEKTEN devrede mi. Varsayilan KAPALI: yeni kurulum
     * kilitsiz baslar, kilit Ayarlar'dan acilir. Diskteki deger `lockEnabled()`
     * ile okunur (anahtari olmayan eski kurulum acik sayilir), ama bu cihazda
     * kimlik sorulamiyorsa kapali gorunur (bkz. lockSwitchOn) - anahtar ile
     * acilis kapisi ayni seyi soylesin.
     */
    val biometricLock: Boolean = false,

    /**
     * "Açılış kilidi" satiri cizilsin mi. Donanimi olmayan telefonda ve
     * masaustunde kilidin karsiligi yok; acilamayacak bir anahtar gostermek
     * "bozuk" gorunuyordu. Kimligi tanimsiz (NotEnrolled) cihazda satir durur,
     * acilmak istenince sebebini soyler.
     */
    val lockAvailable: Boolean = false,

    /**
     * Tercihler DISKTEN OKUNDU mu.
     *
     * Kilit varsayilani artik kapali, ama eski kurulumlarda kilit diskte ACIK
     * okunur (bkz. lockEnabled). Bayrak olmasaydi uygulama diske bakmadan
     * "kilitsiz" varsayar ve kilitli bir telefonda ilk karede bakiyeyi
     * gosterirdi. Kilit karari ancak bu true olunca verilir.
     */
    val prefsLoaded: Boolean = false,

    /** Bu cihazin profili. null ise "bu telefon kimin" adimi henuz gecilmedi. */
    val activeMemberId: String? = null,

    // Fiyatlar - salt okunur bilgi satirlari. Fiyatlar acilista ve elle
    // yenilendiginde cekilir; ayarlanabilir bir aralik ya da secilebilir kaynak
    // yok, o yuzden bu satirlar dokunulamaz. Gercegi yazarlar.
    val priceRefreshLabel: String = "Açılışta ve elle yenilendiğinde",
    val priceSourceLabel: String = "Serbest piyasa · TCMB · TEFAS",

    // Veri ve hesap
    /** Son yedek tarihi ("29 Temmuz 2026"); bos ise henuz yedek alinmadi. */
    val lastBackupLabel: String = "",
    /**
     * HESAP modu (bkz. CloudMode) - "Hesap ve eşitleme" bolumu buna gore
     * cizilir. null = oturum henuz okunmadi; bolum o ana kadar cizilmez.
     *
     * NEYDI: bolum yalniz "girisli mi" bayragina bakiyordu. Girisli ama hesaba
     * baglanmamis cihaz (esitleme yok) "Çıkış yap"li bir hesap gibi, oturumu
     * dusmus bagli cihaz ise hic hesabi olmayan biri gibi gorunuyordu.
     */
    val cloudMode: CloudMode? = null,
    /** Bulut anahtarlari bu surumde var mi; yoksa hesap bolumu cizilmez. */
    val cloudConfigured: Boolean = false,
    /**
     * "Son eşitleme" satiri: son BASARILI esitleme ("az önce", "5 dk önce") ya
     * da "Henüz yok". Push watermark'ini degil LastSyncedAt'i okur - yalniz
     * karsi telefondan alan bir cihaz da esitleniyor demektir.
     */
    val lastSyncedLabel: String = "Henüz yok",
    val appVersion: String = "",

    /**
     * Esin (bu cihazin profili OLMAYAN) adlandirilmis adi; adsizsa null.
     * Onay metinleri "Merve'nin telefonu" der, ad yoksa "eşinizin telefonu".
     */
    val partnerName: String? = null,

    /** "Tüm verileri sil" / "Bu cihazı sıfırla" onay penceresi acik mi. */
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
    /** "Hesaptan çık" onay penceresi acik mi. */
    val confirmSignOut: Boolean = false,
    /**
     * Hesaba henuz gitmemis yerel degisiklik var (onay acilirken bakilir):
     * cikis esitlemeyi durdurur, bunlar yalniz bu cihazda kalir.
     */
    val unsentChanges: Boolean = false,
    /** Geri yukleme onayi bekleniyor - mevcut veri silinecek. */
    val confirmRestore: Boolean = false,
    /** Yedekleme ya da geri yukleme suruyor. */
    val working: Boolean = false,
) {
    /** Profiller kartinin alt satiri: iki profilin adi ("Burak Can, Merve"). */
    val shareSummary: String
        get() = members.joinToString(", ") { it.name }
}

sealed interface SettingsIntent {
    data class SelectTheme(val mode: ThemeMode) : SettingsIntent
    data class SetShowCents(val value: Boolean) : SettingsIntent

    data class SetHideBalanceOnStart(val value: Boolean) : SettingsIntent

    /** Acmak once kimlik dogrulamasi ister (bkz. lockEnableStep); kapatmak istemez. */
    data class SetBiometricLock(val value: Boolean) : SettingsIntent

    data object Backup : SettingsIntent
    data object Restore : SettingsIntent
    data object ConfirmRestore : SettingsIntent
    data object DismissRestoreConfirm : SettingsIntent
    data object ExportCsv : SettingsIntent

    /** Onay penceresini acar - silmez. */
    data object DeleteAllData : SettingsIntent
    data object DismissDeleteConfirm : SettingsIntent

    /** Asil silme. Yalniz onay penceresinden gonderilir. */
    data object ConfirmDeleteAllData : SettingsIntent

    /**
     * "Hesaptan çık" satiri: ONAY penceresini acar (cikmaz). Pencere kayitlarin
     * akibetini ve gitmemis degisiklikleri soyler.
     */
    data object SignOut : SettingsIntent

    /** Onaydan: baglanti ve oturum birakilir, kayitlar cihazda kalir. */
    data object ConfirmSignOut : SettingsIntent
    data object DismissSignOutConfirm : SettingsIntent

    /** "Şimdi eşitle": yalniz hesaba ulasilamiyorken gorunur. */
    data object SyncNow : SettingsIntent

    /** Yarim baglantida "Vazgeç", dusen oturumda "Hesapsız devam et". */
    data object DropLink : SettingsIntent
}

sealed interface SettingsEffect {
    data object AllDataDeleted : SettingsEffect
    data class DeleteFailed(val message: String) : SettingsEffect

    /**
     * Karsiligi henuz yazilmamis bir satira dokunuldu.
     *
     * Sessiz kalmak hata gibi gorunuyordu: kullanici "Yedekle"ye basip hicbir sey
     * olmayinca uygulamanin takildigini dusunuyor.
     */
    data object NotReady : SettingsEffect

    /**
     * Hesap baglantisi birakildi (acik cikis ya da "Vazgeç" / "Hesapsız devam
     * et"). Kabuk kullaniciyi Ayarlar'da tutar, kayitlarin akibetini soyler.
     */
    data object SignedOut : SettingsEffect

    /** Dosya kullanicinin sectigi yere gonderildi. */
    data object BackupReady : SettingsEffect

    /** Yedek geri yuklendi - ekranlar kendiliginden tazelenir. */
    data object Restored : SettingsEffect

    data class BackupFailed(val message: String) : SettingsEffect

    /**
     * Kullaniciya tek satirlik bilgi (orn. "Açılış kilidi açık..."). Kabuk
     * seritte gosterir; anahtarin neden acilmadigini sessiz birakmak "anahtar
     * bozuk" gibi gorunuyordu.
     */
    data class Notice(val message: String) : SettingsEffect
}

// --- Veriye ne olacak: cikis, sifirlama, geri yukleme ------------------------

/** Onay penceresinin metinleri. */
data class DialogCopy(val title: String, val message: String, val confirmLabel: String)

/** "Merve'nin telefonu" ya da (ad yoksa) "eşinizin telefonu". SAF. */
internal fun partnerPhone(partnerName: String?): String =
    partnerName?.takeIf { it.isNotBlank() }?.let { "${it.trGenitive()} telefonu" } ?: "eşinizin telefonu"

/**
 * "Hesaptan çık" onayi. SAF.
 *
 * NEYDI: satir dogrudan cikiyordu ve kullanici kayitlarina ne olacagini
 * bilmiyordu - "cikarsam her sey silinir mi, esimin telefonu da mi cikar?".
 * Metin ucunu de soyler: kayitlar bu cihazda kalir, esin telefonu etkilenmez,
 * henuz gitmemis bir degisiklik varsa yalniz burada kalir.
 */
fun signOutDialog(partnerName: String?, unsentChanges: Boolean): DialogCopy {
    val base = "Eşitleme bu cihazda durur. Kayıtlarınız bu cihazda kalır, hesapsız kullanmaya " +
        "devam edersiniz; ${partnerPhone(partnerName)} hesapla eşitlenmeye devam eder."
    val unsent = " Hesaba henüz gönderilmemiş değişiklikler var; çıkarsanız yalnız bu cihazda kalır."
    return DialogCopy(
        title = "Hesaptan çıkılsın mı?",
        message = if (unsentChanges) base + unsent else base,
        confirmLabel = "Hesaptan çık",
    )
}

/**
 * Silme bir HESABI da ilgilendiriyor mu: cihaz bagli, baglantisi yarim ya da
 * oturumu dusmus. O zaman satir "Bu cihazı sıfırla" olur ve silmeden ONCE
 * hesaptan cikilir (bkz. SettingsViewModel.deleteAll).
 */
fun resetsAccount(mode: CloudMode?): Boolean =
    mode is CloudMode.Cloud || mode is CloudMode.LinkPending || mode is CloudMode.SessionLost

/** Veri bolumundeki yikici satirin adi. SAF. */
fun deleteRowLabel(mode: CloudMode?): String =
    if (resetsAccount(mode)) "Bu cihazı sıfırla" else "Tüm verileri sil"

/**
 * Silme / sifirlama onayi. SAF.
 *
 * Hesapsiz cihazda kayitlarin TEK kopyasi burada: metin yedegi hatirlatir.
 * Hesapli cihazda ise hesaptakiler ve esin telefonu etkilenmez; ayni e-postayla
 * girilince kayitlar geri gelir. NEYDI: iki durumda da ayni "Tüm verileri sil"
 * vardi ve hesapli cihazda silinenler bir sonraki pull'la ~1,5 sn icinde geri
 * iniyordu - kullanici silmenin calismadigini saniyordu.
 *
 * "Geri gelir" plan, gelir, gider ve butce icin de dogru: onlar da hesapla
 * esitleniyor. Esitlenmedikleri gunlerde bu cumle onlar icin yanlisti (bkz.
 * ILERLEME §42, Bilinen bedeller).
 */
fun deleteDialog(mode: CloudMode?, partnerName: String?): DialogCopy =
    if (resetsAccount(mode)) {
        DialogCopy(
            title = "Bu cihaz sıfırlansın mı?",
            message = "Hesaptan çıkılır ve bu cihazdaki kayıtlar ile tercihler silinir. " +
                "Hesabınızdaki kayıtlar ve ${partnerPhone(partnerName)} etkilenmez; " +
                "aynı e-postayla girdiğinizde geri gelir.",
            confirmLabel = "Sıfırla",
        )
    } else {
        DialogCopy(
            title = "Tüm verileri sil",
            message = "Bu cihazdaki varlıklar, işlemler, hedefler, planlar ve tercihler silinir. " +
                "Hesap kullanmadığınız için başka bir kopyası yok — önce yedek almak isteyebilirsiniz. " +
                "Bu işlem geri alınamaz.",
            confirmLabel = "Sil",
        )
    }

/**
 * Geri yukleme KAPALI mi: cihaz bir hesaba bagli (oturumu dusmus olsa da).
 *
 * NEDEN. Geri yukleme her satiri "simdi" damgalar; bagli cihazda yedegin eski
 * hali LWW ile hesabin yeni halinin USTUNE yazilir, iki telefona birden gider.
 * Oturumu dusmus cihaz da ayni hesaba donunce sorusuz esitlenir. Hesapsiz ya
 * da baglantisi yarim cihazda acik: orada sonraki baglanti soru sorar (bkz.
 * LocalRestoredAt, classifyLink).
 */
fun restoreLocked(mode: CloudMode?): Boolean = mode is CloudMode.Cloud || mode is CloudMode.SessionLost

/** Kapali geri yukleme satirinin degeri ve dokununca soyledigi. SAF. */
const val RestoreLockedValue: String = "Hesaba bağlıyken kapalı"

fun restoreLockedMessage(mode: CloudMode?): String {
    val exit = if (mode is CloudMode.SessionLost) "Hesapsız devam et" else "Hesaptan çık"
    return "Hesaba bağlıyken yüklenen yedek hesaptaki kayıtların yerine geçemez, onlarla karışır. " +
        "Yedeği yüklemek için önce $exit."
}

/**
 * Veri bolumunun alt notu: kayitlarin kac kopyasi var. SAF. Bulut yoksa ya da
 * mod henuz okunmadiysa not yok.
 */
fun dataFootnote(mode: CloudMode?, cloudConfigured: Boolean): String? = when {
    !cloudConfigured || mode == null -> null
    mode is CloudMode.Cloud -> "Kayıtlarınız hesabınızda da saklanıyor."
    mode == CloudMode.Local -> "Hesap kullanmadığınız için kayıtların tek kopyası bu cihazda. Ara sıra yedek alın."
    else -> null
}

// --- Hesap ve esitleme bolumu ---------------------------------------------

/** Hesap bolumundeki eylemler; ekran her birini bir niyete ya da gezinmeye baglar. */
enum class AccountAction {
    /** Hesapsiz cihaz: giris ekranina. */
    Link,

    /** Yarim baglanti: hesabi indirip "bu telefon kimin"i soran adima. */
    CompleteLink,

    /** Yarim baglantida "Vazgeç", dusen oturumda "Hesapsız devam et". */
    DropLink,

    /** Dusen oturum: ayni hesaba yeniden giris. */
    Relogin,

    /** Hesaba ulasilamiyor: bir tur simdi denensin. */
    SyncNow,

    /** Bagli cihaz: hesaptan cik. */
    SignOut,
}

data class AccountActionRow(
    val title: String,
    val subtitle: String?,
    val action: AccountAction,
)

/**
 * "Hesap ve eşitleme" bolumunun icerigi: bir durum satiri, bagliyken "Son
 * eşitleme" ve moda gore eylemler.
 */
data class AccountSection(
    val statusTitle: String,
    val statusSubtitle: String?,
    /** "Son eşitleme" satirinin degeri; yalniz bagli cihazda (Cloud) dolu. */
    val lastSynced: String?,
    val actions: List<AccountActionRow>,
)

/**
 * Hesap bolumunu moddan kurar. SAF: her modun satirlari testte denenebilsin
 * (bkz. CloudModeTest). Bulut yapilandirilmamissa ya da oturum henuz
 * okunmadiysa null - bolum hic cizilmez.
 *
 * Durum basligi CloudMode'un uzun bicimidir (Banners.kt'deki tek kaynak);
 * "Eşitlendi"ye zaman eklenmez, onu ayri "Son eşitleme" satiri soyler.
 */
fun accountSection(
    mode: CloudMode?,
    cloudConfigured: Boolean,
    lastSyncedLabel: String,
): AccountSection? {
    if (!cloudConfigured || mode == null) return null
    return when (mode) {
        CloudMode.Local -> AccountSection(
            statusTitle = mode.longLabel(),
            statusSubtitle = "Hesap kullanmıyorsunuz. Kayıtlar başka bir cihazla eşitlenmez.",
            lastSynced = null,
            actions = listOf(
                AccountActionRow(
                    title = "Hesaba bağla",
                    subtitle = "Eşinizle iki telefonda aynı birikim için",
                    action = AccountAction.Link,
                ),
            ),
        )
        is CloudMode.Cloud -> AccountSection(
            statusTitle = mode.longLabel(),
            statusSubtitle = mode.email.ifBlank { null },
            lastSynced = lastSyncedLabel,
            actions = buildList {
                if (mode.status == CloudStatus.Unreachable) {
                    add(AccountActionRow("Şimdi eşitle", null, AccountAction.SyncNow))
                }
                add(AccountActionRow("Hesaptan çık", null, AccountAction.SignOut))
            },
        )
        is CloudMode.LinkPending -> AccountSection(
            statusTitle = mode.longLabel(),
            statusSubtitle = listOfNotNull(
                mode.email.ifBlank { null },
                "Kayıtlar henüz hesaba gönderilmiyor.",
            ).joinToString(" · "),
            lastSynced = null,
            actions = listOf(
                AccountActionRow(
                    title = "Tamamla",
                    subtitle = "Hesaptaki profiller indirilir, bu telefonun kim olduğu sorulur",
                    action = AccountAction.CompleteLink,
                ),
                AccountActionRow(
                    title = "Vazgeç",
                    subtitle = "Hesaptan çıkılır; kayıtlar bu cihazda kalır",
                    action = AccountAction.DropLink,
                ),
            ),
        )
        is CloudMode.SessionLost -> AccountSection(
            statusTitle = "Oturum kapandı",
            statusSubtitle = "Eşitleme durdu. Kayıtlar bu cihazda duruyor.",
            lastSynced = null,
            actions = listOf(
                AccountActionRow(
                    title = "Yeniden giriş yap",
                    subtitle = mode.email.ifBlank { null },
                    action = AccountAction.Relogin,
                ),
                AccountActionRow(
                    title = "Hesapsız devam et",
                    subtitle = "Hesap bağlantısı kaldırılır; kayıtlar bu cihazda kalır",
                    action = AccountAction.DropLink,
                ),
            ),
        )
    }
}

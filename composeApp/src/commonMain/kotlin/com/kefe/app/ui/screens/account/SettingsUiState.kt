package com.kefe.app.ui.screens.account

import com.kefe.app.data.sync.CloudMode
import com.kefe.app.data.sync.CloudStatus
import com.kefe.app.ui.components.longLabel

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

    /** "Tüm verileri sil" onay penceresi acik mi. */
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
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

    /** "Hesaptan çık": baglanti ve oturum birakilir, kayitlar cihazda kalir. */
    data object SignOut : SettingsIntent

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

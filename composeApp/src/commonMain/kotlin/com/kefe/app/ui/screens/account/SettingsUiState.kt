package com.kefe.app.ui.screens.account

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
    val biometricLock: Boolean = true,

    /**
     * Tercihler DISKTEN OKUNDU mu.
     *
     * [biometricLock] varsayilani acik; bayrak olmasaydi uygulama diske hic
     * bakmadan "kilitli" varsayar ve kilidi hic istememis bir kullaniciya bile
     * ilk karede parmak izi sorardi. Kilit ancak bu true olunca uygulanir.
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
    val email: String = "",
    /** Bulut hesabina girilmis mi - "Çıkış yap" satiri buna gore gorunur. */
    val signedIn: Boolean = false,
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

    data object SignOut : SettingsIntent
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

    /** Oturum kapandi - kabuk giris ekranina doner. */
    data object SignedOut : SettingsEffect

    /** Dosya kullanicinin sectigi yere gonderildi. */
    data object BackupReady : SettingsEffect

    /** Yedek geri yuklendi - ekranlar kendiliginden tazelenir. */
    data object Restored : SettingsEffect

    data class BackupFailed(val message: String) : SettingsEffect
}

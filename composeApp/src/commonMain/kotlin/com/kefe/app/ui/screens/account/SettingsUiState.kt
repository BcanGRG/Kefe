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
    val currencyLabel: String = "₺ Türk lirası",
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

    // Fiyatlar
    val priceRefreshLabel: String = "15 dakikada bir",
    val priceSourceLabel: String = "Serbest piyasa",

    // Bildirimler
    val notifyPartnerEntry: Boolean = true,
    val notifyMonthlyReminder: Boolean = true,
    val notifyMilestone: Boolean = false,

    // Veri ve hesap
    val lastBackupLabel: String = "",
    val email: String = "",
    /** Bulut hesabina girilmis mi - "Çıkış yap" satiri buna gore gorunur. */
    val signedIn: Boolean = false,
    val appVersion: String = "1.0.4",

    /** "Tüm verileri sil" onay penceresi acik mi. */
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
    /** Geri yukleme onayi bekleniyor - mevcut veri silinecek. */
    val confirmRestore: Boolean = false,
    /** Yedekleme ya da geri yukleme suruyor. */
    val working: Boolean = false,
) {
    /** Paylasim kartinin alt satiri: "Ortak Birikim · Volkan, Ayşe". */
    val shareSummary: String
        get() = listOf(portfolioName, members.joinToString(", ") { it.name })
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}

sealed interface SettingsIntent {
    data class SelectTheme(val mode: ThemeMode) : SettingsIntent
    data object OpenCurrency : SettingsIntent
    data class SetShowCents(val value: Boolean) : SettingsIntent

    data class SetHideBalanceOnStart(val value: Boolean) : SettingsIntent
    data class SetBiometricLock(val value: Boolean) : SettingsIntent

    data object OpenPriceRefresh : SettingsIntent
    data object OpenPriceSource : SettingsIntent

    data class SetNotifyPartnerEntry(val value: Boolean) : SettingsIntent
    data class SetNotifyMonthlyReminder(val value: Boolean) : SettingsIntent
    data class SetNotifyMilestone(val value: Boolean) : SettingsIntent

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
    data object OpenPrivacy : SettingsIntent
    data object OpenTerms : SettingsIntent
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

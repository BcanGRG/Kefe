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
    val themeMode: ThemeMode = ThemeMode.Dark,
    val currencyLabel: String = "₺ Türk lirası",
    val showCents: Boolean = false,

    // Gizlilik
    val hideBalanceOnStart: Boolean = true,
    val biometricLock: Boolean = true,

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
    val appVersion: String = "1.0.4",
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
    data object ExportCsv : SettingsIntent
    data object DeleteAllData : SettingsIntent

    data object SignOut : SettingsIntent
    data object OpenPrivacy : SettingsIntent
    data object OpenTerms : SettingsIntent
}

package com.kefe.app.ui.screens.account

import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.ui.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Ayarlar.
 *
 * Tercihler DISKE yazilir: once yalniz bellekteydi ve kullanici temayi Acik yapip
 * uygulamayi kapatinca secim kayboluyordu. Yazma ile okuma ayni akista bulusur -
 * ekran her zaman diskteki degeri gosterir, iyimser bir kopyayi degil.
 */
class SettingsViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val preferences: PreferencesRepository,
) : MviViewModel<SettingsUiState, SettingsIntent, SettingsEffect>(
    SettingsUiState(email = "volkan@ornek.com"),
) {

    init {
        observe()
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTheme -> put(PreferenceKeys.ThemeMode, intent.mode.name)
            is SettingsIntent.SetShowCents -> put(PreferenceKeys.ShowCents, intent.value)
            is SettingsIntent.SetHideBalanceOnStart ->
                put(PreferenceKeys.HideBalanceOnStart, intent.value)
            is SettingsIntent.SetBiometricLock -> put(PreferenceKeys.BiometricLock, intent.value)
            is SettingsIntent.SetNotifyPartnerEntry ->
                put(PreferenceKeys.NotifyPartnerEntry, intent.value)
            is SettingsIntent.SetNotifyMonthlyReminder ->
                put(PreferenceKeys.NotifyMonthlyReminder, intent.value)
            is SettingsIntent.SetNotifyMilestone -> put(PreferenceKeys.NotifyMilestone, intent.value)

            // Silme ONAY ISTER. Dogrudan silen bir satir, yanlislikla dokunulunca
            // geri donusu olmayan bir kayip demekti.
            SettingsIntent.DeleteAllData -> setState { copy(confirmDelete = true) }
            SettingsIntent.DismissDeleteConfirm -> setState { copy(confirmDelete = false) }
            SettingsIntent.ConfirmDeleteAllData -> deleteAll()

            // Henuz karsiligi olmayanlar. Sessizce yutmak yerine kullaniciya
            // soylenir - dokununca hicbir sey olmamasi hata gibi gorunuyordu.
            SettingsIntent.OpenCurrency,
            SettingsIntent.OpenPriceRefresh,
            SettingsIntent.OpenPriceSource,
            SettingsIntent.Backup,
            SettingsIntent.Restore,
            SettingsIntent.ExportCsv,
            SettingsIntent.SignOut,
            SettingsIntent.OpenPrivacy,
            SettingsIntent.OpenTerms,
            -> emitEffect(SettingsEffect.NotReady)
        }
    }

    private fun put(key: String, value: Boolean) = put(key, value.toString())

    private fun put(key: String, value: String) {
        viewModelScope.launch { preferences.put(key, value) }
    }

    private fun deleteAll() {
        setState { copy(confirmDelete = false, deleting = true) }
        viewModelScope.launch {
            runCatching { portfolioRepository.deleteAllData() }
                .onSuccess { emitEffect(SettingsEffect.AllDataDeleted) }
                .onFailure { emitEffect(SettingsEffect.DeleteFailed(it.message ?: "Silinemedi.")) }
            setState { copy(deleting = false) }
        }
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePortfolio(),
                portfolioRepository.observeMembers(),
                preferences.observeAll(),
            ) { portfolio, members, prefs ->
                current.copy(
                    portfolioName = portfolio.name,
                    members = members.mapIndexed { index, member ->
                        SettingsMember(member.name, member.initials, index)
                    },
                    themeMode = prefs.themeMode(),
                    showCents = prefs.flag(PreferenceKeys.ShowCents, default = false),
                    hideBalanceOnStart = prefs.flag(PreferenceKeys.HideBalanceOnStart, true),
                    biometricLock = prefs.flag(PreferenceKeys.BiometricLock, true),
                    notifyPartnerEntry = prefs.flag(PreferenceKeys.NotifyPartnerEntry, true),
                    notifyMonthlyReminder = prefs.flag(PreferenceKeys.NotifyMonthlyReminder, true),
                    notifyMilestone = prefs.flag(PreferenceKeys.NotifyMilestone, false),
                )
            }.collect { next -> setState { next } }
        }
    }
}

/** Taninmayan deger varsayilana duser - eski bir kayit uygulamayi dusurmemeli. */
private fun Map<String, String>.themeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == this[PreferenceKeys.ThemeMode] } ?: ThemeMode.Dark

private fun Map<String, String>.flag(key: String, default: Boolean): Boolean =
    this[key]?.toBooleanStrictOrNull() ?: default

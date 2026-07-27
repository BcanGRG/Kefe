package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Ayarlar. Tercihler kalici depoya yazilana kadar bellekte tutulur; ekran
 * sozlesmesi degismeyecegi icin depo geldiginde yalniz bu sinif degisir.
 */
class SettingsViewModel(
    private val portfolioRepository: PortfolioRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            lastBackupLabel = "Son: 12 Tem 03:00",
            email = "volkan@ornek.com",
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        observePortfolio()
    }

    fun onIntent(intent: SettingsIntent) {
        val current = _state.value
        _state.value = when (intent) {
            is SettingsIntent.SelectTheme -> current.copy(themeMode = intent.mode)
            is SettingsIntent.SetShowCents -> current.copy(showCents = intent.value)
            is SettingsIntent.SetHideBalanceOnStart ->
                current.copy(hideBalanceOnStart = intent.value)
            is SettingsIntent.SetBiometricLock -> current.copy(biometricLock = intent.value)
            is SettingsIntent.SetNotifyPartnerEntry ->
                current.copy(notifyPartnerEntry = intent.value)
            is SettingsIntent.SetNotifyMonthlyReminder ->
                current.copy(notifyMonthlyReminder = intent.value)
            is SettingsIntent.SetNotifyMilestone -> current.copy(notifyMilestone = intent.value)

            // Alt sayfa acan ve yikici eylemler bir ust katmanda karsilanir;
            // burada durum degismez.
            SettingsIntent.OpenCurrency,
            SettingsIntent.OpenPriceRefresh,
            SettingsIntent.OpenPriceSource,
            SettingsIntent.Backup,
            SettingsIntent.Restore,
            SettingsIntent.ExportCsv,
            SettingsIntent.DeleteAllData,
            SettingsIntent.SignOut,
            SettingsIntent.OpenPrivacy,
            SettingsIntent.OpenTerms,
            -> current
        }
    }

    private fun observePortfolio() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePortfolio(),
                portfolioRepository.observeMembers(),
            ) { portfolio, members ->
                _state.value.copy(
                    portfolioName = portfolio.name,
                    members = members.mapIndexed { index, member ->
                        SettingsMember(member.name, member.initials, index)
                    },
                )
            }.collect { _state.value = it }
        }
    }
}

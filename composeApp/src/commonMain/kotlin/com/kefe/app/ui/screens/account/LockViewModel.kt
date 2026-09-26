package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.security.BiometricAvailability
import com.kefe.app.security.BiometricGate
import com.kefe.app.security.BiometricResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Acilis kilidi. Giristen AYRI bir ViewModel: kilit yalniz acilista, yigin
 * KOKUNDE (LockKey) gorunur; giris ise her zaman itilir. NEYDI: ikisi ayni
 * VM'deydi ve kilit kalintisi (stage=Locked, unlocked=true) hic sifirlanmadigi
 * icin kabuk girisi "asRoot"/vmState.copy yamalariyla kilitten ayirmak
 * zorundaydi.
 */
class LockViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val biometric: BiometricGate,
) : ViewModel() {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        observePortfolio()
    }

    fun onIntent(intent: LockIntent) {
        when (intent) {
            LockIntent.Unlock -> unlock()
        }
    }

    /** Kilit ekranindaki portfoy adi depodan gelir - ekranda sabit yazilmaz. */
    private fun observePortfolio() {
        viewModelScope.launch {
            portfolioRepository.observePortfolio().collect { portfolio ->
                _state.value = _state.value.copy(portfolioName = portfolio.name)
            }
        }
    }

    /**
     * Cihaz kilidini acar.
     *
     * KILIT KAPI DEGIL, PERDEDIR. Cihazda parmak izi tanimli degilse ya da
     * donanim yoksa kullanici ICERI ALINIR - bakiyeyi baskasindan saklamak
     * icin konan bir ozellik, kullaniciyi kendi verisinden etmemelidir.
     *
     * Yanlis parmak denemesi buraya hic gelmez: sistem istemi acik kalir ve
     * kullanici tekrar dener. Buraya yalniz sonuc doner.
     */
    private fun unlock() {
        if (!_state.value.canStartUnlock()) return
        _state.value = _state.value.copy(unlocking = true, unlockError = null)

        viewModelScope.launch {
            if (biometric.availability() != BiometricAvailability.Available) {
                _state.value = _state.value.copy(unlocking = false, unlocked = true)
                return@launch
            }

            val result = biometric.authenticate(
                title = "Kefe kilitli",
                subtitle = "Bakiyeleri görmek için kimliğinizi doğrulayın",
            )
            _state.value = when (result) {
                BiometricResult.Success ->
                    _state.value.copy(unlocking = false, unlocked = true, unlockError = null)

                // Vazgecmek hata degil: ekran kilitli kalir, kirmizi yazi cikmaz.
                BiometricResult.Cancelled ->
                    _state.value.copy(unlocking = false, unlockError = null)

                is BiometricResult.Failed ->
                    _state.value.copy(unlocking = false, unlockError = result.message)
            }
        }
    }
}

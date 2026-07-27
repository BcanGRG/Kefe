package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Giris / baslangic / kilit asamalari. MVI-lite: tek [LoginUiState] akisi.
 *
 * Kimlik dogrulama katmani henuz yok; burada yalniz ekranin durum makinesi
 * yasar. Ag cagrisi geldiginde [sendMagicLink] ve [unlock] govdeleri degisir,
 * ekran degismez.
 */
class LoginViewModel(
    private val portfolioRepository: PortfolioRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        observePortfolio()
    }

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.ChangeEmail -> _state.value = _state.value.copy(
                // Kullanici yazmaya baslayinca hata ve "gonderildi" bilgisi duser.
                email = intent.value.trim(),
                emailError = null,
                linkSent = false,
            )

            LoginIntent.SendMagicLink -> sendMagicLink()

            LoginIntent.SignInWithPassword -> Unit

            LoginIntent.GoToStart -> _state.value =
                _state.value.copy(stage = LoginStage.Start, portfolioCreated = false)

            LoginIntent.GoToSignIn -> _state.value = _state.value.copy(stage = LoginStage.SignIn)

            LoginIntent.CreatePortfolio -> _state.value = _state.value.copy(portfolioCreated = true)

            is LoginIntent.ChangeInviteCode -> _state.value = _state.value.copy(
                inviteCode = intent.value.filter { it.isDigit() }.take(InviteCodeLength),
                inviteError = null,
            )

            LoginIntent.Join -> join()

            LoginIntent.Unlock -> unlock()

            LoginIntent.UnlockWithPassword -> unlock()
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

    private fun sendMagicLink() {
        val current = _state.value
        if (!current.email.isValidEmail()) {
            _state.value = current.copy(emailError = "Geçerli bir e-posta yazın")
            return
        }
        _state.value = current.copy(sendingLink = false, linkSent = true, emailError = null)
    }

    private fun join() {
        val current = _state.value
        if (current.inviteCode.length != InviteCodeLength) {
            _state.value = current.copy(inviteError = "Kod altı haneli olmalı")
            return
        }
        _state.value = current.copy(joining = false, inviteError = null)
    }

    private fun unlock() {
        _state.value = _state.value.copy(unlocking = false, unlocked = true, unlockError = null)
    }
}

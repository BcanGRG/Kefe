package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.remote.AuthException
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Giris / baslangic / kilit asamalari. MVI-lite: tek [LoginUiState] akisi.
 *
 * Giris PAROLASIZDIR: e-postaya alti haneli tek kullanimlik kod gider, kullanici
 * onu yazar. Kod yerine tiklanabilir baglanti kullanmak her platformda ayri is
 * demekti - Android'de intent filter, iOS'ta universal link, masaustunde dogru
 * duzgun bir karsiligi yok. Kod uc platformda ayni sekilde calisir.
 */
class LoginViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val authRepository: AuthRepository,
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
                codeSent = false,
            )

            LoginIntent.SendCode -> sendCode()

            is LoginIntent.ChangeCode -> _state.value = _state.value.copy(
                code = intent.value.filter { it.isDigit() }.take(LoginCodeLength),
                emailError = null,
            )

            LoginIntent.VerifyCode -> verifyCode()

            LoginIntent.EditEmail -> _state.value = _state.value.copy(
                codeSent = false,
                code = "",
                emailError = null,
            )

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

    private fun sendCode() {
        val current = _state.value
        if (!current.email.isValidEmail()) {
            _state.value = current.copy(emailError = "Geçerli bir e-posta yazın")
            return
        }
        _state.value = current.copy(sendingCode = true, emailError = null)
        viewModelScope.launch {
            val error = authRepository.sendCode(current.email).exceptionOrNull()
            _state.value = _state.value.copy(
                sendingCode = false,
                // Kod kutusu ancak gonderim BASARILIYSA acilir; yoksa kullanici
                // hic gelmeyecek bir kodu bekler.
                codeSent = error == null,
                emailError = error?.userMessage(),
            )
        }
    }

    private fun verifyCode() {
        val current = _state.value
        if (current.code.length != LoginCodeLength) {
            _state.value = current.copy(emailError = "Kod altı haneli olmalı")
            return
        }
        _state.value = current.copy(verifying = true, emailError = null)
        viewModelScope.launch {
            val error = authRepository.verifyCode(current.email, current.code).exceptionOrNull()
            _state.value = _state.value.copy(
                verifying = false,
                signedIn = error == null,
                // Yanlis kod en sik hata; sebebi sunucudan gelen metinle yazariz
                // ama bos gelirse kullaniciya ise yarar bir sey soyleriz.
                emailError = error?.let { it.userMessage() ?: "Kod doğrulanamadı" },
                code = if (error == null) "" else current.code,
            )
        }
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

/**
 * Hatanin kullaniciya gosterilebilir yuzu.
 *
 * Ag hatalarinin mesaji ("Failed to connect to /10.0.2.2:443") kullaniciya
 * hicbir sey anlatmaz; kimlik hatalarininki ("Token has expired or is invalid")
 * ise dogrudan ise yarar. Ayrimi tur uzerinden yapariz.
 */
private fun Throwable.userMessage(): String? = when (this) {
    is AuthException -> message
    else -> "Bağlanılamadı — internet bağlantınızı kontrol edin"
}

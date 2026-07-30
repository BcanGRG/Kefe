package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.remote.AuthException
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.security.BiometricAvailability
import com.kefe.app.security.BiometricGate
import com.kefe.app.security.BiometricResult
import com.kefe.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val biometric: BiometricGate,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // "Kodu tekrar gonder" geri sayimi; e-posta duzeltilince ya da yeni gonderimde
    // iptal edilir.
    private var cooldownJob: Job? = null

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

            LoginIntent.EditEmail -> {
                cooldownJob?.cancel()
                _state.value = _state.value.copy(
                    codeSent = false,
                    code = "",
                    emailError = null,
                    resendCooldown = 0,
                )
            }

            LoginIntent.ResendCode -> resendCode()

            LoginIntent.CreatePortfolio -> _state.value = _state.value.copy(portfolioCreated = true)

            LoginIntent.Lock -> _state.value =
                _state.value.copy(stage = LoginStage.Locked, unlocked = false)

            LoginIntent.Unlock -> unlock()
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
            // Basariyla gonderildiyse "tekrar gonder" geri sayimi baslar.
            if (error == null) startResendCooldown()
        }
    }

    /**
     * Ayni adrese yeni kod. sendCode'dan farki: BASARISIZ olsa da kod kutusundan
     * ATMAZ - kullanici zaten kod bekliyor, yalniz hatayi gorur ve tekrar dener.
     */
    private fun resendCode() {
        val current = _state.value
        if (current.resendCooldown > 0 || current.sendingCode) return
        _state.value = current.copy(sendingCode = true, emailError = null)
        viewModelScope.launch {
            val error = authRepository.sendCode(current.email).exceptionOrNull()
            _state.value = _state.value.copy(
                sendingCode = false,
                emailError = error?.userMessage(),
            )
            if (error == null) startResendCooldown()
        }
    }

    /** Foreground, tek seferlik geri sayim - biter, arka planda donen bir sey yok. */
    private fun startResendCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in ResendCooldownSeconds downTo 1) {
                _state.value = _state.value.copy(resendCooldown = remaining)
                delay(1_000)
            }
            _state.value = _state.value.copy(resendCooldown = 0)
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
        if (_state.value.unlocking) return
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

/**
 * "Kodu tekrar gonder" arasindaki bekleme. Supabase OTP icin varsayilan yeniden
 * gonderim araligiyla (60 sn) ayni; kullaniciyi sunucu reddetmeden once bekletir.
 */
private const val ResendCooldownSeconds = 60

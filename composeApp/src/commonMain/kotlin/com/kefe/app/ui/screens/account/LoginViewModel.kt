package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.remote.AuthException
import com.kefe.app.domain.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * E-posta koduyla giris. MVI-lite: tek [LoginUiState] akisi.
 *
 * Giris PAROLASIZDIR: e-postaya alti haneli tek kullanimlik kod gider, kullanici
 * onu yazar. Kod yerine tiklanabilir baglanti kullanmak her platformda ayri is
 * demekti - Android'de intent filter, iOS'ta universal link, masaustunde dogru
 * duzgun bir karsiligi yok. Kod uc platformda ayni sekilde calisir.
 *
 * Kayit ile giris AYNI akistir: bu e-postayla hesap yoksa dogrulama onu acar.
 *
 * Acilis kilidi burada DEGIL (bkz. [LockViewModel]). VM surec boyunca yasar;
 * her acilista [LoginIntent.Begin] ile sifirlanir.
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // "Kodu tekrar gonder" geri sayimi; e-posta duzeltilince ya da yeni gonderimde
    // iptal edilir.
    private var cooldownJob: Job? = null

    // Yoldaki gonderim ve dogrulama. Begin ikisini de iptal eder: yarida
    // birakilmis bir denemenin sonucu yeni acilan ekranin durumuna yazilmasin.
    private var sendJob: Job? = null
    private var verifyJob: Job? = null

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.Begin -> begin(intent.email)

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
                // Dogrulama suruyorken e-postaya donulmez (bkz. signInBack):
                // sonuc yine gelir ve oturum yazilir; ekran onu karsilayacak
                // durumda kalmali, bos bir e-posta formunda degil.
                if (_state.value.verifying) return
                cooldownJob?.cancel()
                _state.value = _state.value.copy(
                    codeSent = false,
                    code = "",
                    emailError = null,
                    resendCooldown = 0,
                )
            }

            LoginIntent.ResendCode -> resendCode()

            LoginIntent.SignInHandled -> _state.value =
                _state.value.copy(signedIn = false, codeSent = false, code = "", emailError = null)
        }
    }

    private fun begin(email: String?) {
        sendJob?.cancel()
        verifyJob?.cancel()
        cooldownJob?.cancel()
        _state.value = LoginUiState(email = email?.trim().orEmpty())
    }

    private fun sendCode() {
        val current = _state.value
        if (!current.email.isValidEmail()) {
            _state.value = current.copy(emailError = "Geçerli bir e-posta yazın")
            return
        }
        _state.value = current.copy(sendingCode = true, emailError = null)
        sendJob = viewModelScope.launch {
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
        sendJob = viewModelScope.launch {
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
        if (current.verifying) return
        if (current.code.length != LoginCodeLength) {
            _state.value = current.copy(emailError = "Kod altı haneli olmalı")
            return
        }
        _state.value = current.copy(verifying = true, emailError = null)
        verifyJob = viewModelScope.launch {
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

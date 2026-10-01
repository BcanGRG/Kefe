package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.remote.AuthException
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
 * Cihaz bir hesaba bagliyken baska bir hesapla girilemez (bkz.
 * accountSwitchBlocked): kod dogru olsa da oturum bu cihazda kapatilir.
 *
 * Acilis kilidi burada DEGIL (bkz. [LockViewModel]). VM surec boyunca yasar;
 * her acilista [LoginIntent.Begin] ile sifirlanir.
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    // Hesap degistirme engeli bu cihazin baglantisini okur (bkz. verifyCode).
    private val preferences: PreferencesRepository,
) : ViewModel() {

    val state: StateFlow<LoginUiState>
        field = MutableStateFlow(LoginUiState())

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

            is LoginIntent.ChangeEmail -> state.value = state.value.copy(
                // Kullanici yazmaya baslayinca hata, engel ve "gonderildi"
                // bilgisi duser.
                email = intent.value.trim(),
                emailError = null,
                codeSent = false,
                guard = null,
            )

            LoginIntent.SendCode -> sendCode()

            is LoginIntent.ChangeCode -> state.value = state.value.copy(
                code = intent.value.filter { it.isDigit() }.take(LoginCodeLength),
                emailError = null,
            )

            LoginIntent.VerifyCode -> verifyCode()

            LoginIntent.EditEmail -> {
                // Dogrulama suruyorken e-postaya donulmez (bkz. signInBack):
                // sonuc yine gelir ve oturum yazilir; ekran onu karsilayacak
                // durumda kalmali, bos bir e-posta formunda degil.
                if (state.value.verifying) return
                cooldownJob?.cancel()
                state.value = state.value.copy(
                    codeSent = false,
                    code = "",
                    emailError = null,
                    resendCooldown = 0,
                )
            }

            LoginIntent.ResendCode -> resendCode()

            LoginIntent.SignInHandled -> state.value =
                state.value.copy(signedIn = false, codeSent = false, code = "", emailError = null)
        }
    }

    private fun begin(email: String?) {
        sendJob?.cancel()
        verifyJob?.cancel()
        cooldownJob?.cancel()
        state.value = LoginUiState(email = email?.trim().orEmpty())
    }

    private fun sendCode() {
        val current = state.value
        if (!current.email.isValidEmail()) {
            state.value = current.copy(emailError = "Geçerli bir e-posta yazın")
            return
        }
        state.value = current.copy(sendingCode = true, emailError = null)
        sendJob = viewModelScope.launch {
            val error = authRepository.sendCode(current.email).exceptionOrNull()
            state.value = state.value.copy(
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
        val current = state.value
        if (current.resendCooldown > 0 || current.sendingCode) return
        state.value = current.copy(sendingCode = true, emailError = null)
        sendJob = viewModelScope.launch {
            val error = authRepository.sendCode(current.email).exceptionOrNull()
            state.value = state.value.copy(
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
                state.value = state.value.copy(resendCooldown = remaining)
                delay(1_000)
            }
            state.value = state.value.copy(resendCooldown = 0)
        }
    }

    private fun verifyCode() {
        val current = state.value
        if (current.verifying) return
        if (current.code.length != LoginCodeLength) {
            state.value = current.copy(emailError = "Kod altı haneli olmalı")
            return
        }
        state.value = current.copy(verifying = true, emailError = null, guard = null)
        verifyJob = viewModelScope.launch {
            // Baglanti DOGRULAMADAN ONCE okunur: dogrulama bitince esitleme
            // bir sonraki karede baslayabilir; o anki okuma yarisa girerdi.
            val linkUserId = preferences.get(PreferenceKeys.CloudLinkUserId)
            val linkEmail = preferences.get(PreferenceKeys.CloudLinkEmail)
            val error = authRepository.verifyCode(current.email, current.code).exceptionOrNull()
            if (error == null) {
                val session = (
                    authRepository.observeAuthState().first { it !is AuthState.Unknown } as? AuthState.SignedIn
                    )?.session
                if (accountSwitchBlocked(linkUserId, session?.userId)) {
                    // HESAP DEGISTIRME ENGELI. Bu cihazin kayitlari baska bir
                    // hesaba ait: yeni hesaba baglanmak iki portfoyu karistirir.
                    // Yeni oturum YALNIZ bu cihazda kapanir; baglanti ve kayitlar
                    // yerinde kalir (mod "Oturum kapandı"ya doner, dogru hesapla
                    // yeniden girilebilir). NEYDI: engel yoktu; yanlis e-postayla
                    // giren cihaz profil adimina gidiyor, oradan iki hesabin
                    // kayitlari birbirine akabiliyordu.
                    authRepository.signOut()
                    state.value = state.value.copy(
                        verifying = false,
                        signedIn = false,
                        codeSent = false,
                        code = "",
                        resendCooldown = 0,
                        guard = accountSwitchCopy(linkEmail),
                    )
                    cooldownJob?.cancel()
                    return@launch
                }
            }
            state.value = state.value.copy(
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

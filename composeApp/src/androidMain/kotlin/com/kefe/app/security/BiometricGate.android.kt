package com.kefe.app.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.kefe.app.data.backup.AndroidFileBridge
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Android biyometrik kilidi.
 *
 * BIOMETRIC_WEAK **veya** DEVICE_CREDENTIAL isteriz. Yalniz gucluyu (STRONG)
 * sormak, yuz tanimayla acilan bircok telefonda kilidi kullanilamaz kilardi;
 * cihaz kimligini de kabul etmek ise kullaniciya PIN/desen cikis yolu birakir.
 * Burada korunan sey banka hesabi degil, omuz ustunden bakilan bir bakiye.
 *
 * Activity referansi [AndroidFileBridge]'den alinir. Ayri bir kopru daha kurmak,
 * ekran her dondugunde sizdirilacak ikinci bir Activity demekti.
 */
actual class BiometricGate actual constructor() {

    actual fun availability(): BiometricAvailability {
        val activity = AndroidFileBridge.activity ?: return BiometricAvailability.Unsupported
        return when (BiometricManager.from(activity).canAuthenticate(Authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> BiometricAvailability.NoHardware
            else -> BiometricAvailability.Unsupported
        }
    }

    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult {
        val activity = AndroidFileBridge.activity as? FragmentActivity
            ?: return BiometricResult.Failed("Ekran hazır değil")

        // BiometricPrompt ANA IS PARCACIGINDA kurulmali; baska bir yerden
        // cagrildiginda sessizce hicbir sey gostermiyor.
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val prompt = BiometricPrompt(
                    activity,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult,
                        ) {
                            if (cont.isActive) cont.resume(BiometricResult.Success)
                        }

                        override fun onAuthenticationError(code: Int, message: CharSequence) {
                            if (!cont.isActive) return
                            // Vazgecmek hata degildir: kullanici geri tusuna
                            // bastiysa ekranda kirmizi bir uyari gormemeli.
                            val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                                code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                code == BiometricPrompt.ERROR_CANCELED
                            cont.resume(
                                if (cancelled) {
                                    BiometricResult.Cancelled
                                } else {
                                    BiometricResult.Failed(message.toString())
                                }
                            )
                        }

                        // onAuthenticationFailed BILEREK BOS: yanlis parmak
                        // denemesi akisi bitirmez, sistem istemi acik kalir ve
                        // kullanici tekrar dener.
                    },
                )

                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(Authenticators)
                    .build()

                prompt.authenticate(info)
                cont.invokeOnCancellation { prompt.cancelAuthentication() }
            }
        }
    }

    private companion object {
        const val Authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

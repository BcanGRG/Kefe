package com.kefe.app.security

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication

/**
 * iOS cihaz kilidi (Face ID / Touch ID).
 *
 * DeviceOwnerAuthentication secilir, Biometrics degil: birincisi biyometri
 * basarisiz olursa parolaya duser. Yalniz biyometri sorulsaydi, yuzunu
 * tanitamayan kullanicinin baska yolu kalmazdi.
 *
 * Info.plist'e NSFaceIDUsageDescription gerekir; olmadan Face ID istemi
 * gosterilmeden uygulama sonlanir.
 *
 * Bu dosya Windows'ta DERLENMEZ - Apple arac zinciri yalniz macOS'ta calisiyor
 * (bkz. build.gradle.kts). Yerinde durur, bir Mac'te derlenir.
 */
actual class BiometricGate actual constructor() {

    actual fun availability(): BiometricAvailability {
        val context = LAContext()
        val canEvaluate = context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)
        return if (canEvaluate) BiometricAvailability.Available else BiometricAvailability.NotEnrolled
    }

    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult =
        suspendCancellableCoroutine { cont ->
            LAContext().evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthentication,
                localizedReason = subtitle,
            ) { success, error ->
                if (!cont.isActive) return@evaluatePolicy
                cont.resume(
                    when {
                        success -> BiometricResult.Success
                        error?.code == LAErrorUserCancel -> BiometricResult.Cancelled
                        else -> BiometricResult.Failed(
                            error?.localizedDescription ?: "Kilit açılamadı"
                        )
                    }
                )
            }
        }
}

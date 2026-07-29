package com.kefe.app.security

/**
 * Masaustunde cihaz kilidi YOKTUR.
 *
 * Windows Hello / Touch ID'ye JVM'den guvenilir bir kopru yok; olsa bile
 * masaustu surumu evdeki ortak bilgisayarda calisiyor ve orada omuz ustunden
 * bakis riski telefondakinden farkli. [BiometricAvailability.Unsupported]
 * donmek yeterli - Ayarlar satiri bu platformda hic cizilmez.
 */
actual class BiometricGate actual constructor() {

    actual fun availability(): BiometricAvailability = BiometricAvailability.Unsupported

    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult =
        BiometricResult.Failed("Bu platformda cihaz kilidi yok")
}

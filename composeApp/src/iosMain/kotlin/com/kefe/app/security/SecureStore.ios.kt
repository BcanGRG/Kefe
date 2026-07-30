package com.kefe.app.security

/**
 * iOS sertlestirmesi HENUZ passthrough.
 *
 * Dogru cozum Keychain'dir (kSecAttrAccessibleWhenUnlockedThisDeviceOnly);
 * Kotlin/Native Security cercevesi koprusu ayri bir istir ve bu makinede
 * derlenemedigi icin (bkz. build.gradle.kts) simdilik metin oldugu gibi durur.
 * Android tarafi gercek Keystore ile korunuyor; iOS'a Keychain, iOS uygulamasi
 * kurulurken eklenecek.
 */
actual class SecureStore actual constructor() {
    actual fun protect(plain: String): String = plain
    actual fun reveal(stored: String): String = stored
}

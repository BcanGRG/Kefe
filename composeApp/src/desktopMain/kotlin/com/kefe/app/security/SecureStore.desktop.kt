package com.kefe.app.security

/**
 * Masaustunde donanim destekli guvenli depo yok.
 *
 * JVM'den Windows DPAPI / macOS Keychain'e guvenilir, bagimliliksiz bir kopru
 * yok; sahte bir sifreleme (sabit anahtar) hic sifrelemekten daha kotu olurdu -
 * guvenlik yanilsamasi verir. Metin oldugu gibi saklanir. Asil hedef cihaz
 * Android; masaustu ikincil ve ortak bir bilgisayarda calisiyor.
 */
actual class SecureStore actual constructor() {
    actual fun protect(plain: String): String = plain
    actual fun reveal(stored: String): String = stored
}

package com.kefe.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore ile AES/GCM.
 *
 * Anahtar KEYSTORE'DA dogar ve ORADAN CIKMAZ - uygulama yalnizca sifreleme
 * istegi gonderir, anahtarin kendisini hicbir zaman gormez. Bu yuzden jeton
 * cihazdan kopyalansa bile (yedek, adb) baska bir cihazda cozulemez.
 *
 * GCM her sifrelemede yeni bir IV uretir; IV gizli degildir, sifreli metnin
 * basina yazilir. Onek ([PREFIX]) hangi metnin sifreli oldugunu soyler: oneksiz
 * gelen (bu surumden onceki duz-metin oturum) oldugu gibi dondurulur.
 */
actual class SecureStore actual constructor() {

    actual fun protect(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + iv.base64() + SEP + cipherText.base64()
    }

    actual fun reveal(stored: String): String {
        // Oneksizse bu surumden once yazilmis duz metindir - oldugu gibi ver.
        if (!stored.startsWith(PREFIX)) return stored
        val parts = stored.removePrefix(PREFIX).split(SEP, limit = 2)
        if (parts.size != 2) return stored
        val iv = parts[0].fromBase64()
        val cipherText = parts[1].fromBase64()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Biyometrik kilit ayri bir ozellik; anahtarin kendisini kimlik
                // dogrulamaya baglamiyoruz. Yoksa arka plandaki jeton yenileme de
                // parmak izi isterdi.
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kefe_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PREFIX = "enc1:"
        const val SEP = ":"
    }
}

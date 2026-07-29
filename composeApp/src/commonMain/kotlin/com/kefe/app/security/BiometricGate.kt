package com.kefe.app.security

/**
 * Cihazin biyometrik kilidi kullanilabilir mi.
 *
 * Bu ayrim SUS DEGIL: kilit acikken parmak izi tanimli olmayan bir cihazda
 * kullanici kendi verisinin disinda kalir. Ayarlar satiri buna gore ya kapali
 * durur ya da sebebini yazar.
 */
enum class BiometricAvailability {
    /** Donanim var ve en az bir parmak izi/yuz kayitli. */
    Available,

    /** Donanim var ama hicbir sey kayitli degil - kullanici cihaz ayarlarindan ekler. */
    NotEnrolled,

    /** Bu cihazda biyometrik donanim yok. */
    NoHardware,

    /** Bu platformda karsiligi yok (masaustu). */
    Unsupported,
}

sealed interface BiometricResult {
    data object Success : BiometricResult

    /** Kullanici vazgecti. Hata DEGILDIR - ekranda kirmizi bir sey yazilmaz. */
    data object Cancelled : BiometricResult

    data class Failed(val message: String) : BiometricResult
}

/**
 * Cihaz kilidi.
 *
 * [FileTransfer][com.kefe.app.data.backup.FileTransfer] ile ayni desen: ortak
 * kod sozlesmeyi tanir, her platform kendi karsiligini verir. Android tarafi
 * Activity ister ve onu ayni kopruden alir - ikinci bir Activity referansi
 * tutmak, ekran her dondugunde bir oncekini sizdirmak demekti.
 */
expect class BiometricGate() {

    fun availability(): BiometricAvailability

    /**
     * Kullaniciyi dogrular. Cagiran taraf beklerken ekranda kilit durur.
     *
     * Atmaz: her sonuc [BiometricResult] ile doner, cunku "iptal" ile "cihaz
     * desteklemiyor" farkli seyler ve ikisi de istisna degil.
     */
    suspend fun authenticate(title: String, subtitle: String): BiometricResult
}

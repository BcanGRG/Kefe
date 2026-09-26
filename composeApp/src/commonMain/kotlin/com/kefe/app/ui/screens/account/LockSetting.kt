package com.kefe.app.ui.screens.account

import com.kefe.app.security.BiometricAvailability
import com.kefe.app.security.BiometricResult

/**
 * "Açılış kilidi" anahtari ACILMAK istendiginde ne yapilacagi.
 *
 * NEYDI: anahtar dokunulur dokunulmaz "true" yaziyordu. Parmak izi ya da ekran
 * kilidi tanimli olmayan telefonda kilit acik gorunuyor ama hicbir sey
 * sormuyordu; tanimli olanda da kullanici kilidin gercekten calistigini ancak
 * bir sonraki acilista, belki kendini disarida birakarak ogreniyordu. Artik kilit
 * ancak bir kez basariyla dogrulandiktan sonra yazilir.
 *
 * BiometricGate bir `expect class` - testte taklit edilemez. Karar bu yuzden saf
 * fonksiyonlarda; ViewModel yalniz uygular.
 */
internal sealed interface LockEnableStep {

    /** Kimlik sorulur; kilit yalniz [BiometricResult.Success] ile yazilir. */
    data object Authenticate : LockEnableStep

    /** Kilit acilmaz, kullaniciya sebebi soylenir. */
    data class Refuse(val message: String) : LockEnableStep

    /**
     * Bu cihazda kilidin karsiligi yok. Satir zaten cizilmez (bkz. [lockRowVisible]);
     * yine de bir yoldan gelirse sessizce hicbir sey yapilmaz.
     */
    data object Unavailable : LockEnableStep
}

internal fun lockEnableStep(availability: BiometricAvailability): LockEnableStep =
    when (availability) {
        BiometricAvailability.Available -> LockEnableStep.Authenticate
        BiometricAvailability.NotEnrolled -> LockEnableStep.Refuse(LockNotEnrolledMessage)
        BiometricAvailability.NoHardware,
        BiometricAvailability.Unsupported,
        -> LockEnableStep.Unavailable
    }

/**
 * Ayarlar'da "Açılış kilidi" satiri gorunsun mu.
 *
 * Kimligi tanimsiz (NotEnrolled) cihazda satir DURUR: kullanici cihaz
 * ayarlarindan parmak izi ekleyip geri donebilir, satir ona neyin eksik
 * oldugunu soyler. Donanim yoksa ya da platformda karsiligi yoksa (masaustu)
 * satir cizilmez - acilamayacak bir anahtar yalniz kafa karistirir.
 */
internal fun lockRowVisible(availability: BiometricAvailability): Boolean =
    availability == BiometricAvailability.Available ||
        availability == BiometricAvailability.NotEnrolled

/**
 * Kilit bu cihazda UYGULANABILIR mi: yalniz kimlik gercekten sorulabiliyorsa.
 *
 * Acilis kapisi (isLaunchLocked'in gateAvailable'i) ile Ayarlar'daki anahtar
 * AYNI kurali okur. NEYDI: masaustunde ve kimligi tanimsiz telefonda kilit
 * ekrani bir an cizilip hemen aciliyordu - soracak bir sey yoktu.
 */
internal fun lockCanApply(availability: BiometricAvailability): Boolean =
    availability == BiometricAvailability.Available

/**
 * "Açılış kilidi" anahtari ACIK mi gorunsun.
 *
 * Anahtar diskteki degeri degil, kilidin GERCEKTEN yapacagini gosterir: kayitli
 * deger acik VE bu cihazda uygulanabiliyorsa acik. NEYDI: anahtari olmayan eski
 * kurulum "acik" okunur (bkz. lockEnabled); o telefonda parmak izi/ekran kilidi
 * tanimli degilse acilis kapisi hicbir sey sormuyor, ama anahtar "acik" ve alt
 * satiri "sorulur" diyordu. Artik orada anahtar kapali gorunur; acmak isteyene
 * yeni kurulumdaki gibi "tanımlı değil" soylenir.
 *
 * Kayitli deger BILEREK degistirilmez: kullanici sonradan parmak izi eklerse
 * eski kurulumun kilidi geri gelir - o telefonun sahibi kilidi zaten acik
 * birakmisti.
 */
internal fun lockSwitchOn(lockEnabled: Boolean, availability: BiometricAvailability): Boolean =
    lockEnabled && lockCanApply(availability)

/** Dogrulama sonucunun karsiligi: kilit yazilsin mi, kullaniciya ne soylensin. */
internal data class LockEnableOutcome(val enable: Boolean, val notice: String?)

internal fun lockEnableOutcome(result: BiometricResult): LockEnableOutcome =
    when (result) {
        BiometricResult.Success -> LockEnableOutcome(enable = true, notice = LockEnabledMessage)
        // Vazgecmek hata degil: anahtar kapali kalir, kirmizi bir sey yazilmaz.
        BiometricResult.Cancelled -> LockEnableOutcome(enable = false, notice = null)
        is BiometricResult.Failed -> LockEnableOutcome(enable = false, notice = LockFailedMessage)
    }

/** Acma isteminin basligi ve alt satiri - sistem penceresinde gorunur. */
internal const val LockEnablePromptTitle = "Açılış kilidini etkinleştir"
internal const val LockEnablePromptSubtitle = "Onaylamak için kimliğinizi doğrulayın"

internal const val LockEnabledMessage = "Açılış kilidi açık. Kefe her açılışta kimliğinizi soracak."
internal const val LockFailedMessage = "Doğrulanamadı; kilit açılmadı."
internal const val LockNotEnrolledMessage =
    "Bu cihazda parmak izi, yüz ya da ekran kilidi tanımlı değil. Önce cihaz ayarlarından ekleyin."

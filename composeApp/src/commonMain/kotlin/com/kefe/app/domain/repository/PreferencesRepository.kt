package com.kefe.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Kullanici tercihleri.
 *
 * Anahtar-deger, tipli bir model DEGIL: bu tercihlerin domain karsiligi yok,
 * hepsi ekran durumunda yasiyor ve listesi urun kararlariyla degisiyor. Her
 * tercih icin alan acmak, her yeni anahtarda hem domain hem sema degistirmek
 * demekti. Yorumlamak - "true", "Dark", "15" - cagiran tarafin isi.
 *
 * Onceden tercihler yalniz bellekteydi: kullanici temayi Acik yapip uygulamayi
 * kapatinca secim kayboluyordu.
 */
interface PreferencesRepository {

    fun observeAll(): Flow<Map<String, String>>

    suspend fun put(key: String, value: String)

    /** Tek seferlik okuma. Senkron watermark'i gibi akis istemeyen yerler icin. */
    suspend fun get(key: String): String?
}

/**
 * Tercih anahtarlari tek yerde.
 *
 * Diske yazilan metinler: degistirilirse eski kayitlar okunamaz hale gelir.
 */
object PreferenceKeys {
    const val ThemeMode = "themeMode"
    const val ShowCents = "showCents"
    const val HideBalanceOnStart = "hideBalanceOnStart"
    const val BiometricLock = "biometricLock"
    const val NotifyPartnerEntry = "notifyPartnerEntry"
    const val NotifyMonthlyReminder = "notifyMonthlyReminder"
    const val NotifyMilestone = "notifyMilestone"

    /**
     * Bu cihazin hangi profil oldugu. "Bu telefon Volkan'in" - eklenen her islem
     * bu profile yazilir.
     *
     * CIHAZA AITTIR, senkronlanmaz ve yedege GIRMEZ: Volkan'in yedegi Ayse'nin
     * telefonuna yuklenince o telefon Volkan olmamali (bkz. restoreBackup).
     */
    const val ActiveMemberId = "activeMemberId"

    /** Son yedegin alindigi tarih ("2026-07-28"). Ayarlar satirinin sagi. */
    const val LastBackupAt = "lastBackupAt"

    /**
     * Push watermark'i: bu cihazin sunucuya en son ittigi ana kadarki epoch ms.
     * Bir sonraki push yalniz updatedAt >= bu deger olan satirlari gonderir.
     *
     * CIHAZA AITTIR: her cihazin kendi ilerlemesi. Senkronlanmaz, yedege girmez,
     * geri yuklemede korunur (bkz. DeviceOnlySettings).
     */
    const val LastPushedAt = "lastPushedAt"
}

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
}

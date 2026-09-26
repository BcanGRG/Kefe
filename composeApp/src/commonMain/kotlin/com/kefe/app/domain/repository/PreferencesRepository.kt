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
    /**
     * Acilista bakiyeyi gizle. CIHAZA AITTIR: omuz ustunden bakis riski her
     * telefonda ayri; Volkan'in yedegi Ayse'nin telefonundaki secimi degistirmemeli.
     */
    const val HideBalanceOnStart = "hideBalanceOnStart"

    /**
     * Acilis kilidi. CIHAZA AITTIR (yedege girmez, geri yuklemede korunur):
     * kilit bu telefonun parmak izine/ekran kilidine baglidir, baska bir
     * cihazin yedegiyle acilip kapanmamali. Okuma yalniz [lockEnabled] ile.
     */
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

/**
 * Acilis kilidi acik mi. Kilidi okuyan TEK yer.
 *
 * ANAHTAR YOKSA ACIK (eski kurulum). NEYDI: kilit varsayilan olarak acikti ve
 * anahtar yalniz Ayarlar'daki anahtara dokunulunca yaziliyordu; o surumden gelen
 * telefonlarda anahtar hic yok ve sahipleri kilitli acilisa alisik. Onlarinki
 * sessizce kapanmasin diye eksik anahtar "acik" okunur.
 *
 * YENI veritabanlari kurulumda acikca "false" yazar (bkz. bootstrapIfNeeded,
 * deleteAllData): yeni kurulum kilitsiz baslar, isteyen Ayarlar'dan acar.
 *
 * NEDEN TEK OKUYUCU: varsayilan once ekran durumunda ve ViewModel'de ayri ayri
 * yaziliydi; biri degisip digeri kalinca ayni anahtar iki yerde iki anlam
 * tasirdi.
 */
fun Map<String, String>.lockEnabled(): Boolean =
    this[PreferenceKeys.BiometricLock]?.toBooleanStrictOrNull() ?: true

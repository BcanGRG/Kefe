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

    /**
     * Birden cok anahtari TEK ISLEMDE yazar; degeri null olan anahtar SILINIR.
     *
     * NEDEN TEK ISLEM: hesap baglantisi (CloudLinkUserId + CloudLinkEmail +
     * ActiveMemberId) yarim yazilirsa mod ikisinin arasinda kalir - baglanti
     * var ama profil yok ya da tersi. Ayri ayri put'larda iki yazma arasinda
     * akis bir kez ara durumu yayar ve senkron o anlik durumla baslayabilirdi.
     */
    suspend fun putAll(changes: Map<String, String?>)

    /** Anahtari siler. "Yok" ile "bos metin" ayri seyler: bos yazmak yerine sil. */
    suspend fun remove(key: String) = putAll(mapOf(key to null))
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

    /**
     * Bu cihazin verisi HANGI HESABA bagli (Supabase kullanici kimligi).
     *
     * NEDEN OTURUMDAN AYRI: oturum "kim giris yapmis", baglanti "bu cihazin
     * kayitlari hangi hesapla esitleniyor" sorusunun cevabi. Ikisi ayni degilse
     * (baglanti hic kurulmadi, baska bir hesaba girildi, oturum dustu) senkron
     * CALISMAZ - bkz. deriveCloudMode. Once "hic push'lamadi mi" (LastPushedAt ==
     * null) ilk baglanti sayiliyordu: ilk pull patlayip push gecince isaret
     * kalici kayboluyor ve yerelde yazilmis adlar hesabin ustune itiliyordu.
     *
     * YALNIZ baglanti tamamlaninca yazilir (hesap indirildikten ve "bu telefon
     * kimin" secildikten sonra). Acik cikis ve "Hesapsız devam et" siler.
     * CIHAZA AITTIR: yedege girmez, geri yuklemede korunur.
     */
    const val CloudLinkUserId = "cloudLinkUserId"

    /**
     * Baglantinin e-postasi. Oturum dustugunde (SessionLost) "hangi hesaba
     * yeniden gireceksiniz" sorusunun cevabi yalniz burada kalir. CIHAZA AITTIR.
     */
    const val CloudLinkEmail = "cloudLinkEmail"

    /**
     * Son BASARILI esitlemenin ani (epoch ms). "Son eşitleme" satiri bunu okur.
     *
     * NEYDI: satir push watermark'ini (LastPushedAt) okuyordu; o yalniz yereldeki
     * bir degisiklik gidince ilerliyor. Karsi telefondan gelen her seyi alan ama
     * kendisi bir sey yazmayan cihaz "3 gün önce" diyordu - pull her dakika
     * calisirken. CIHAZA AITTIR.
     */
    const val LastSyncedAt = "lastSyncedAt"

    /**
     * Hesapsizken yedek geri yuklendiyse ani (epoch ms). Geri yukleme satirlari
     * "simdi" damgalar; bu cihaz sonra bir hesaba baglanirsa hangi kaydin nereden
     * geldigi ayirt edilemez - baglanti adimi bunu soru sormak icin okur.
     * Baglanti tamamlaninca silinir. CIHAZA AITTIR.
     */
    const val LocalRestoredAt = "localRestoredAt"

    /**
     * Tek seferlik baglanti gocunun isareti (bkz. migrateCloudLinkIfNeeded).
     * Varsa goc bir daha calismaz. CIHAZA AITTIR.
     */
    const val CloudLinkMigrated = "cloudLinkMigrated"
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

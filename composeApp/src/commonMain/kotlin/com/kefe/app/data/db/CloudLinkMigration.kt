package com.kefe.app.data.db

import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.repository.PreferenceKeys

/**
 * Hesap baglantisi gocu: baglanti anahtarlarindan ONCEKI kurulumlar icin, tek
 * sefer, veritabani acilirken.
 *
 * NEDEN. Esitleme artik yalniz cihaz bir hesaba BAGLIYSA calisiyor
 * ([PreferenceKeys.CloudLinkUserId]). Eski surumde bu anahtar yoktu; gocsuz,
 * aylardir esitlenen iki telefon guncellemeden sonra "Bağlantı yarım" der ve
 * esitlemeyi birakirdi.
 *
 * KURAL (bkz. [cloudLinkToMigrate]):
 *  - oturum var VE cihaz en az bir kez push'lamis: baglanti oturumdan yazilir,
 *    hicbir sey gorunur degismez;
 *  - oturum var ama hic push yok (orn. "Bağlanmadan devam et"): baglanti
 *    YAZILMAZ, mod "Bağlantı yarım" olur - hesap hic indirilmemis olabilir;
 *  - oturum yok: yazilacak bir sey yok, mod "Bu cihazda".
 *
 * Oturum tablosu duz metin userId/email tasir (jetonlar sifreli ama onlara
 * gerek yok), yani goc surucu acilir acilmaz senkron calisabilir.
 *
 * Isaret ([PreferenceKeys.CloudLinkMigrated]) cihaza aittir: yedege girmez,
 * geri yuklemede korunur. "Tüm verileri sil" ayarlari silip isareti AYNI islemde
 * yeniden yazar (bkz. deleteAllData). NEDEN: silme aninda yoldaki bir push
 * watermark'i yeniden yazabiliyor; isaretsiz kalsa bir sonraki acilista goc
 * "oturum + watermark" gorup baglantiyi geri yazar ve hesap, "bu telefon kimin"
 * sorulmadan silinen veritabanina inerdi.
 */
fun KefeDatabase.migrateCloudLinkIfNeeded() {
    transaction {
        if (settingQueries.selectSetting(PreferenceKeys.CloudLinkMigrated).executeAsOneOrNull() != null) {
            return@transaction
        }
        val session = authSessionQueries.selectSession().executeAsOneOrNull()
        val link = cloudLinkToMigrate(
            sessionUserId = session?.userId,
            sessionEmail = session?.email,
            lastPushedAt = settingQueries.selectSetting(PreferenceKeys.LastPushedAt).executeAsOneOrNull(),
            existingLink = settingQueries.selectSetting(PreferenceKeys.CloudLinkUserId).executeAsOneOrNull(),
        )
        if (link != null) {
            settingQueries.upsertSetting(settingKey = PreferenceKeys.CloudLinkUserId, settingValue = link.first)
            settingQueries.upsertSetting(settingKey = PreferenceKeys.CloudLinkEmail, settingValue = link.second)
        }
        settingQueries.upsertSetting(settingKey = PreferenceKeys.CloudLinkMigrated, settingValue = "1")
    }
}

/**
 * Gocun yazacagi baglanti (userId, email) ya da null. SAF: tablo satirlari
 * olmadan denenebilsin.
 *
 * Zaten bir baglanti varsa DOKUNULMAZ - goc yalniz eksigi tamamlar.
 */
internal fun cloudLinkToMigrate(
    sessionUserId: String?,
    sessionEmail: String?,
    lastPushedAt: String?,
    existingLink: String?,
): Pair<String, String>? {
    if (!existingLink.isNullOrBlank()) return null
    val userId = sessionUserId?.takeIf { it.isNotBlank() } ?: return null
    if (lastPushedAt == null) return null
    return userId to sessionEmail.orEmpty()
}

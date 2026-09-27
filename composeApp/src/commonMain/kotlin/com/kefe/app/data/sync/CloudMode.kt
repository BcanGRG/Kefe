package com.kefe.app.data.sync

import com.kefe.app.domain.repository.AuthState

/**
 * Hesapla esitleme GORUNUR durumu: bu cihazin kayitlari nerede yasiyor.
 *
 * NEYDI. Ekranlar uc ayri sinyalden besleniyordu ve birbirini tutmuyordu:
 * ozet cipi bulut durumunu (girisli mi), ray ve yan navigasyon FIYAT tazeligini
 * gosteriyordu; ayni an rayda "Bekliyor", cipte "Eşitleniyor", seritte
 * "Çevrimdışı" yaziyordu. Ustelik "girisli" ile "bu cihaz hesaba bagli" ayni
 * sey sayiliyordu: hesaba giren cihaz, hesabin kayitlari inmeden ve "bu telefon
 * kimin" sorulmadan push'a basliyor, yerelde yazilmis adlar hesabin ustune
 * gidiyordu.
 *
 * Mod DISKE YAZILMAZ; her an oturumdan ve baglanti anahtarlarindan TURETILIR
 * (bkz. [deriveCloudMode]). Ayri bir "mod" kaydi olsaydi oturumla ayrisabilirdi.
 */
sealed interface CloudMode {

    /** Oturum yok, baglanti yok: kayitlar yalniz bu cihazda. */
    data object Local : CloudMode

    /**
     * Oturum var ama bu cihaz o hesaba BAGLANMADI (baglanti yok ya da baska bir
     * hesaba ait). Hicbir sey gonderilmez ve cekilmez - baglanti adimi
     * tamamlanana kadar.
     */
    data class LinkPending(val email: String) : CloudMode

    /** Oturum baglantiyla AYNI hesapta: esitleme acik. */
    data class Cloud(val email: String, val status: CloudStatus) : CloudMode

    /**
     * Baglanti duruyor ama oturum yok: sunucu oturumu reddetti ya da cihazin
     * anahtari kayboldu. Esitleme durdu, kayitlar bu cihazda; ayni hesapla
     * yeniden girilince kaldigi yerden devam eder.
     */
    data class SessionLost(val email: String) : CloudMode
}

/**
 * Bagli cihazin esitleme durumu.
 *
 * [Syncing] ilk turun sonucu gelene kadar: once giris ANINDA "Eşit" yaziliyordu,
 * henuz tek istek gitmeden. Ayri bir "reddedildi" durumu yok; RLS reddi de
 * [Unreachable] gorunur, sebebi logda kalir.
 */
enum class CloudStatus { Syncing, Synced, Unreachable }

/**
 * Modu oturumdan ve baglanti anahtarlarindan turetir. SAF: testte her satiri
 * denenebilsin diye (bkz. CloudModeTest).
 *
 * `null` = oturum henuz diskten okunmadi ([AuthState.Unknown]). "Bu cihazda"
 * diye baslayip bir kare sonra "Eşitlendi"ye atlamamak icin cip o ana kadar
 * cizilmez.
 */
fun deriveCloudMode(
    auth: AuthState,
    linkUserId: String?,
    linkEmail: String?,
    status: CloudStatus,
): CloudMode? {
    val link = linkUserId?.takeIf { it.isNotBlank() }
    return when (auth) {
        AuthState.Unknown -> null
        is AuthState.SignedIn -> {
            val session = auth.session
            if (link != null && session.userId == link) {
                CloudMode.Cloud(email = session.email, status = status)
            } else {
                CloudMode.LinkPending(email = session.email)
            }
        }
        AuthState.SignedOut ->
            if (link != null) CloudMode.SessionLost(email = linkEmail.orEmpty()) else CloudMode.Local
    }
}

/**
 * Esitlemenin calisacagi hesap: oturum baglantiyla ayni hesaptaysa onun
 * kimligi, aksi halde null. Push, pull ve soket YALNIZ bu null degilken calisir.
 */
fun linkedUserId(auth: AuthState, linkUserId: String?): String? {
    val session = (auth as? AuthState.SignedIn)?.session ?: return null
    val link = linkUserId?.takeIf { it.isNotBlank() } ?: return null
    return session.userId.takeIf { it == link }
}

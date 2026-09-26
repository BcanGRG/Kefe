package com.kefe.app.ui.format

/**
 * Iki epoch ms arasindaki farki kaba bir Turkce etikete cevirir ("az önce",
 * "5 dk önce", "2 sa önce", "3 gün önce"). Gun-alti duraklar yeterli: kullanici
 * "ne kadar taze" sorusuna bakar, saniye hassasiyeti istemez.
 *
 * Ayarlar'daki "Son eşitleme" ve yan navigasyondaki "Hesapla eşitlendi · …"
 * AYNI yerden okur; once yalniz Ayarlar'da, ozel bir fonksiyondu.
 */
fun relativeSince(then: Long, now: Long): String {
    val seconds = ((now - then) / 1000L).coerceAtLeast(0)
    return when {
        seconds < 60 -> "az önce"
        seconds < 3600 -> "${seconds / 60} dk önce"
        seconds < 86_400 -> "${seconds / 3600} sa önce"
        else -> "${seconds / 86_400} gün önce"
    }
}

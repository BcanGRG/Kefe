package com.kefe.app.data.remote

import kotlinx.coroutines.delay

/**
 * Gecici ag hatalarinda yeniden dener.
 *
 * Ucretsiz fiyat kaynaklari bagenti kapatmayi seviyor: ayni istek arka arkaya
 * bir basarili bir "server prematurely closed the connection" donebiliyor.
 * Tek denemede pes etmek fiyat tablosunu sebepsiz eskitiyordu.
 *
 * Yalniz GECICI hatalar tekrarlanir. 4xx gibi kalici yanitlarda beklemek
 * kullaniciyi bosuna oyalar - onlar oldugu gibi yukari cikar.
 */
internal suspend fun <T> retryOnTransient(
    attempts: Int = 3,
    initialDelayMillis: Long = 300,
    block: suspend () -> T,
): T {
    var delayMillis = initialDelayMillis
    var last: Exception? = null

    repeat(attempts) { index ->
        try {
            return block()
        } catch (error: Exception) {
            last = error
            if (index == attempts - 1) throw error
            delay(delayMillis)
            delayMillis *= 2
        }
    }
    throw last ?: IllegalStateException("retryOnTransient: deneme yapilmadi")
}

package com.kefe.app.data.remote

import io.ktor.client.plugins.ClientRequestException
import kotlin.coroutines.cancellation.CancellationException
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
 *
 * ADINA VE BU BELGEYE UYMUYORDU: `catch (Exception)` her seyi yakaliyor,
 * bulunamayan bir fon kodu (404) uc kez deneniyor ve kullanici bosuna
 * bekletiliyordu. Daha kotusu [CancellationException] de yutuluyordu - ekran
 * kapandiginda iptal edilen istek iptal olmayi reddedip iki kez daha aga
 * cikiyordu.
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
        } catch (cancelled: CancellationException) {
            // Iptal bir hata degil, bir karar. Tekrarlamak coroutine iptalini
            // bozar ve istek olmesi gerekirken yasamaya devam eder.
            throw cancelled
        } catch (permanent: ClientRequestException) {
            // 4xx: istek yanlis, tekrar etmek ayni cevabi getirir.
            throw permanent
        } catch (error: Exception) {
            last = error
            if (index == attempts - 1) throw error
            delay(delayMillis)
            delayMillis *= 2
        }
    }
    throw last ?: IllegalStateException("retryOnTransient: deneme yapilmadi")
}

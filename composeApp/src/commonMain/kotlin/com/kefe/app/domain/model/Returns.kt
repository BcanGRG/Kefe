package com.kefe.app.domain.model

import kotlin.math.abs
import kotlin.math.pow

/**
 * Bir nakit hareketi. Negatif = cepten cikan (alim), pozitif = cebe giren
 * (satis ya da bugunku deger).
 */
data class CashFlow(val date: KefeDate, val amount: Double)

/**
 * Yillik para-agirlikli getiri (XIRR).
 *
 * NEDEN GEREKLI: varliklar farkli tarihlerde alindiginda basit yuzde yaniltir.
 * 3 ayda %20 ile 3 yilda %20 ayni sayiyi verir ama bambaska performanstir.
 * XIRR her liranin ne kadar sure calistigini hesaba katar ve tek bir
 * "yillik %" uretir - duzensiz araliklarla yapilan alimlar icin dogru olcu budur.
 *
 * Cozulen denklem:  toplam( cf_i / (1+r)^(gun_i/365) ) = 0
 *
 * Once Newton-Raphson denenir (hizli); yakinsamazsa isaret degistiren aralikta
 * ikiye bolme kullanilir (yavas ama her zaman bulur). Ikisi de basarisizsa null.
 */
fun xirr(flows: List<CashFlow>, guess: Double = 0.1): Double? {
    if (flows.size < 2) return null

    val base = flows.minOf { it.date.toEpochDay() }
    val years = flows.map { (it.date.toEpochDay() - base).toDouble() / 365.0 }
    val amounts = flows.map { it.amount }

    // Isaret degisimi yoksa kok yoktur (hep para koydun ya da hep aldin).
    if (amounts.none { it > 0 } || amounts.none { it < 0 }) return null
    // Tum hareketler ayni gunse zaman boyutu yok.
    if (years.all { it == years.first() }) return null

    fun npv(rate: Double): Double {
        val f = 1.0 + rate
        if (f <= 0.0) return Double.NaN
        var sum = 0.0
        for (i in amounts.indices) sum += amounts[i] / f.pow(years[i])
        return sum
    }

    fun dNpv(rate: Double): Double {
        val f = 1.0 + rate
        if (f <= 0.0) return Double.NaN
        var sum = 0.0
        for (i in amounts.indices) sum += -years[i] * amounts[i] / f.pow(years[i] + 1.0)
        return sum
    }

    // 1) Newton-Raphson
    var rate = guess
    repeat(60) {
        val value = npv(rate)
        if (value.isNaN()) return@repeat
        if (abs(value) < 1e-7) return rate
        val slope = dNpv(rate)
        if (slope.isNaN() || abs(slope) < 1e-12) return@repeat
        val next = rate - value / slope
        if (next <= -0.9999) {
            rate = -0.99
            return@repeat
        }
        if (abs(next - rate) < 1e-9) return next
        rate = next
    }

    // 2) Ikiye bolme - once isaret degistiren araligi tara
    var low = -0.9999
    var high = 10.0
    var fLow = npv(low)
    val fHigh = npv(high)
    if (fLow.isNaN() || fHigh.isNaN() || fLow * fHigh > 0) return null

    repeat(200) {
        val mid = (low + high) / 2.0
        val fMid = npv(mid)
        if (fMid.isNaN()) return null
        if (abs(fMid) < 1e-9 || (high - low) < 1e-10) return mid
        if (fLow * fMid < 0) {
            high = mid
        } else {
            low = mid
            fLow = fMid
        }
    }
    return (low + high) / 2.0
}

/**
 * Bir pozisyonun yillik getirisi (%).
 *
 * Alimlar negatif, satislar pozitif hareket olarak girer; en sona BUGUNKU
 * PIYASA DEGERI pozitif hareket olarak eklenir - yani "bugun satsaydim" varsayimi.
 * Sonuc null ise (tek islem, ayni gun, kok bulunamadi) arayuz yillik getiri
 * satirini GOSTERMEZ; uydurma sayi uretmez.
 */
fun annualizedReturnPercent(
    transactions: List<Transaction>,
    currentValue: Double,
    today: KefeDate,
): Double? {
    if (transactions.isEmpty()) return null

    val flows = buildList {
        for (tx in transactions) {
            val amount = when (tx.side) {
                TradeSide.Buy -> -(tx.quantity * tx.unitPrice + tx.fee)
                TradeSide.Sell -> tx.quantity * tx.unitPrice - tx.fee
            }
            add(CashFlow(tx.date, amount))
        }
        if (currentValue > 0.0) add(CashFlow(today, currentValue))
    }

    return xirr(flows)?.let { it * 100.0 }
}

/**
 * Elde tutma suresi (gun). Basit yuzdenin yaninda gosterilir ki kullanici
 * "%26,6" rakaminin hangi surede olustugunu gorebilsin.
 */
fun holdingDays(firstBuy: KefeDate?, today: KefeDate): Long? =
    firstBuy?.let { today.daysSince(it).coerceAtLeast(0) }

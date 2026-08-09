package com.kefe.app.ui.format

import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.todayChangePercent

/**
 * Degisim penceresi. Piyasa tablosu ve Varliklar listesi AYNI seciciyi
 * kullanir - iki ekranda iki ayri "donem" kavrami olsaydi kullanici birinde
 * haftaya, digerinde gune bakip rakamlari kiyaslardi.
 *
 * Uc secenekle sinirli: kaynaklar bundan otesini vermiyor ve cihazdaki gecmis
 * de bir aydan uzun bir pencereyi guvenilir doldurmuyor.
 */
enum class ChangePeriod(val label: String) {
    Day("Gün"),
    Week("Hafta"),
    Month("Ay"),
}

/** Bilinmeyen degisim. Sifir DEGIL: "degismedi" ile "bilmiyoruz" ayri seylerdir. */
const val UnknownChangeText: String = "—"

/**
 * Fiyatin bu donemdeki yuzde degisimi; null ise veri yok.
 *
 * GUN penceresi KOTASYON GUNUNE baglidir - Varliklar ve Ozet ile ayni kapi
 * (bkz. [Price.todayChangePercent]). Once burada ham [Price.changePercent]
 * okunuyordu ve ayni "Gün" cipi iki ekranda iki farkli sayi veriyordu: pazar
 * gunu Piyasa cumanin hareketini yazarken Varliklar ve Ozet dogru sekilde 0
 * gosteriyordu. Ozet ekraninda celiski ayni ekranda gorunuyordu - piyasa karti
 * dolu, hemen ustundeki "bugün" satiri bostu.
 *
 * Borsa kapaliyken dogru cevap sifirdir: kullanicinin serveti o gun
 * degismemistir.
 */
fun Price.changeIn(period: ChangePeriod, today: KefeDate): Double? = when (period) {
    ChangePeriod.Day -> todayChangePercent(today)
    ChangePeriod.Week -> weekChangePercent
    ChangePeriod.Month -> monthChangePercent
}

/** Pozisyonun bu donemdeki yuzde degisimi; null ise veri yok. */
fun Position.changeIn(period: ChangePeriod): Double? = when (period) {
    ChangePeriod.Day -> dailyChangePercent
    ChangePeriod.Week -> weekChangePercent
    ChangePeriod.Month -> monthChangePercent
}

/** `+2,41%` / `—`. Isaret her zaman yazilir: yon bilgisi renge birakilmaz. */
fun changeText(percent: Double?): String =
    if (percent == null) UnknownChangeText else Money.delta(percent)

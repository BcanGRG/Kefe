package com.kefe.app.domain

import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.kefeDateOfEpochDay

/**
 * Uygulamanin "bugun"u.
 *
 * Getiri hesabi (yillik getiri, elde tutma suresi) ve hedef projeksiyonu bir
 * referans gune ihtiyac duyar. Bu gun ekranlarin icine gomulurse her ekran kendi
 * tarihini uydurur - nitekim once oyle oldu: bir ekranda sabit tarih, digerinde
 * son anlik goruntunun tarihi kullaniliyordu. Tek kaynak burasi.
 */
interface KefeClock {
    fun today(): KefeDate

    /**
     * PIYASANIN bugunu - kotasyon gunleriyle kiyaslanacak tarih.
     *
     * [today] CIHAZIN takvimidir. Kotasyon gunu ise kaynagin takviminden gelir
     * ve kaynaklarin hepsi Turkiye saatiyle calisir (serbest piyasa, TCMB,
     * TEFAS, BIST). Ikisi ayni sanilıyordu: Turkiye disindaki bir cihazda aksam
     * saatlerinde cihaz yarina gecince "kotasyon bugune ait mi" kapisi
     * kapaniyor ve butun altin/doviz satirlarinin gunluk katkisi 0'a dusuyordu.
     *
     * VARSAYILAN [today]'dir: yalniz gercek saati okuyan saat piyasa gununu
     * bilebilir, test saatleri sabit bir gune baglidir.
     */
    fun marketToday(): KefeDate = today()

    /**
     * Duvar saati - 1970'ten beri gecen milisaniye.
     *
     * Gun yetmiyor. Iki cihaz ayni kaydi degistirdiginde hangisinin daha yeni
     * oldugunu soyleyecek tek sey bu; senkron katmanindaki `updatedAt` buradan
     * gelir. Ayrica fiyat tazeligindeki "2 saatten eski" kurali da bugune kadar
     * isletilemiyordu, cunku olcecek bir saat yoktu.
     *
     * CIHAZIN saati, guvenilir bir zaman kaynagi DEGIL: kullanici geri alabilir.
     * Cakisma cozumu icin yeterli (aralarindaki fark saniyeler), guvenlik icin
     * degil - sunucu tarafinda zaman damgasi ayrica tutulacak.
     */
    fun nowEpochMillis(): Long
}

/**
 * Cihazin takvimi.
 *
 * Kayitlar artik DISKE yaziliyor: yanlis tarihli bir islem uygulamayi kapatinca
 * silinmiyor, sonsuza kadar kaliyor. Elle duzeltme ekrani da yok. Bu yuzden
 * gercek gun sart.
 */
class SystemKefeClock : KefeClock {
    override fun today(): KefeDate = currentDate()
    override fun nowEpochMillis(): Long = currentEpochMillis()

    /**
     * Turkiye saatiyle bugun - duvar saatinden turetilir, platform takvim
     * API'siyle degil.
     *
     * Turkiye 2016'dan beri yaz saati uygulamiyor: sabit UTC+3. Sabit ofset,
     * her platformda ayri bir saat dilimi cagrisi yazmaktan hem daha az kod hem
     * de test edilebilir - cevrimin kendisi ortak kodda ve saf.
     */
    override fun marketToday(): KefeDate =
        kefeDateOfEpochDay(floorDiv(currentEpochMillis() + IstanbulOffsetMillis, DayMillis))
}

private const val IstanbulOffsetMillis = 3L * 60L * 60L * 1000L
private const val DayMillis = 24L * 60L * 60L * 1000L

/** Negatif damgalarda da dogru: -1 ms 1969'un son gunudur, 1970'in ilki degil. */
private fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if (a % b != 0L && (a xor b) < 0L) q - 1L else q
}

/**
 * Sabit tarih - yalniz TESTLER icin.
 *
 * Uretimde kullanilmamali: ornek verinin kurgulandigi gune sabitler ve
 * kullanicinin bugun girdigi islem 12 Temmuz 2026 tarihiyle diske yazilir.
 */
class FixedKefeClock(
    private val date: KefeDate = SampleToday,
    private val millis: Long = 0L,
) : KefeClock {
    override fun today(): KefeDate = date
    override fun nowEpochMillis(): Long = millis
}

/** Ornek verinin kurgulandigi gun. */
val SampleToday: KefeDate = KefeDate(2026, 7, 12)

/**
 * Cihazin yerel takvim gunu.
 *
 * kotlinx-datetime eklemek yerine platform API'si kullanildi: tek ihtiyac yerel
 * gun, bagimlilik getirmeye degmez.
 */
expect fun currentDate(): KefeDate

/**
 * Duvar saati, milisaniye.
 *
 * [currentDate] gibi platform API'siyle: tek ihtiyac bir sayi, bagimlilik
 * getirmeye degmez.
 */
expect fun currentEpochMillis(): Long

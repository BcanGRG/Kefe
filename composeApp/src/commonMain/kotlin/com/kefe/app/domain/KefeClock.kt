package com.kefe.app.domain

import com.kefe.app.domain.model.KefeDate

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
}

/**
 * Sabit tarih - yalniz TESTLER icin.
 *
 * Uretimde kullanilmamali: ornek verinin kurgulandigi gune sabitler ve
 * kullanicinin bugun girdigi islem 12 Temmuz 2026 tarihiyle diske yazilir.
 */
class FixedKefeClock(private val date: KefeDate = SampleToday) : KefeClock {
    override fun today(): KefeDate = date
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

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
 * Sabit tarih. Ornek veri belirli bir gune gore kurgulandigi icin (en yeni islem
 * 12 Temmuz 2026, fiyatlar 14:32, net deger serisi Tem 2026'da bitiyor) uygulama
 * simdilik o gunu "bugun" kabul eder.
 *
 * Gercek saat platform API'si gerektirir; kotlinx-datetime eklendiginde yalniz
 * bu sinifin yerine gecen bir uygulama yazilir, cagiran hicbir yer degismez.
 */
class FixedKefeClock(private val date: KefeDate = SampleToday) : KefeClock {
    override fun today(): KefeDate = date
}

/** Ornek verinin kurgulandigi gun. */
val SampleToday: KefeDate = KefeDate(2026, 7, 12)

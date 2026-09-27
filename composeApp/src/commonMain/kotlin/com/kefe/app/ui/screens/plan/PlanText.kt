package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.daysInMonth
import com.kefe.app.domain.model.monthName

// Plan sekmesinin metinleri. SAF ve Compose'suz: her cumle commonTest'te sinanir
// (bkz. PlanTextTest).

/**
 * Ay basliginin alt satiri.
 *
 * Bu ay kalan gunu soyler ("Ekim · 9 gün kaldı"; son gun "Ekim · son gün" -
 * "0 gün kaldı" bitmis gibi okunur). Gecmis ayda olcunun ne oldugunu soyler:
 * alimlar islem TARIHINE gore sayilir, sonradan girilen gecmis tarihli bir alim
 * o aya yazilir. Gelecek ayda henuz sayilacak bir sey yoktur.
 */
internal fun headerSubtitle(month: YearMonth, today: KefeDate): String =
    when (monthRelation(month, today)) {
        MonthRelation.Current -> {
            val left = daysInMonth(month.year, month.month) - today.day
            if (left <= 0) "${month.monthName()} · son gün" else "${month.monthName()} · $left gün kaldı"
        }
        MonthRelation.Past -> "Geçmiş ay · alımlar işlem tarihine göre sayılır."
        MonthRelation.Future -> "Gelecek ay · alımlar ay başlayınca sayılır."
    }

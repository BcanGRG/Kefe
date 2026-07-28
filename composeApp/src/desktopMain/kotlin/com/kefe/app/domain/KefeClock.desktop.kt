package com.kefe.app.domain

import com.kefe.app.domain.model.KefeDate
import java.time.LocalDate

actual fun currentDate(): KefeDate {
    val now = LocalDate.now()
    return KefeDate(year = now.year, month = now.monthValue, day = now.dayOfMonth)
}

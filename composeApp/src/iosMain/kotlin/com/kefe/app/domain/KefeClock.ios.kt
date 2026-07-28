package com.kefe.app.domain

import com.kefe.app.domain.model.KefeDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

actual fun currentDate(): KefeDate {
    val calendar = NSCalendar.currentCalendar
    val parts = calendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        fromDate = NSDate(),
    )
    return KefeDate(
        year = parts.year.toInt(),
        month = parts.month.toInt(),
        day = parts.day.toInt(),
    )
}

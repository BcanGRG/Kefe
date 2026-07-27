package com.kefe.app.domain.model

/**
 * Uygulamanin tarih tipi. Platform tarih API'lerine bagimli olmamak icin
 * yalin yil/ay/gun tutar; bicimleme de Turkce ay adlariyla burada yapilir.
 */
data class KefeDate(
    val year: Int,
    val month: Int,
    val day: Int = 1,
)

private val MonthNamesFull = listOf(
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
)

private val MonthNamesShort = listOf(
    "Oca", "Şub", "Mar", "Nis", "May", "Haz",
    "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
)

private fun monthIndexOf(month: Int): Int = (month - 1).coerceIn(0, 11)

/** "Şubat" */
fun KefeDate.monthName(): String = MonthNamesFull[monthIndexOf(month)]

/** "Şub" */
fun KefeDate.monthLabel(): String = MonthNamesShort[monthIndexOf(month)]

/** "22 Şubat 2024" */
fun KefeDate.formatLong(): String = "$day ${monthName()} $year"

/** "Aralık 2028" */
fun KefeDate.formatMonthYear(): String = "${monthName()} $year"

/** "12 Tem 2026" */
fun KefeDate.formatShort(): String = "$day ${monthLabel()} $year"

/**
 * Gun sayisina cevirir (1970-01-01 = 0).
 *
 * Getiri hesabi farkli tarihlerdeki alimlari kiyaslamak zorunda oldugu icin
 * gercek gun farki gerekir; "ay x 30" yaklasimi yillik getiriyi kaydirir.
 * Algoritma Howard Hinnant'in days_from_civil'i - artik yillari ve 100/400
 * kurallarini dogru sayar.
 */
fun KefeDate.toEpochDay(): Long {
    val m = month.coerceIn(1, 12)
    val d = day.coerceIn(1, 31)
    val y = year - if (m <= 2) 1 else 0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400                                   // [0, 399]
    val mp = (m + if (m > 2) -3 else 9)                       // Mart = 0
    val doy = (153 * mp + 2) / 5 + d - 1                      // [0, 365]
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy           // [0, 146096]
    return era.toLong() * 146097L + doe.toLong() - 719468L
}

/** Iki tarih arasindaki gun farki (bu - other). */
fun KefeDate.daysSince(other: KefeDate): Long = toEpochDay() - other.toEpochDay()

/** Ay ekleyip normalize eder - projeksiyon eksenlerini uretmek icin. */
fun KefeDate.plusMonths(count: Int): KefeDate {
    val zeroBased = (year * 12) + (month - 1) + count
    return KefeDate(
        year = zeroBased / 12,
        month = (zeroBased % 12) + 1,
        day = day,
    )
}

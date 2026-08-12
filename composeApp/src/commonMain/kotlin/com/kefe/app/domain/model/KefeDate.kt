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

/**
 * Ay ekleyip normalize eder - projeksiyon eksenlerini uretmek icin.
 *
 * GUN AY SONUNA KISILIR. Kisilmiyordu: `31 Ocak + 1 ay` 31 SUBAT uretiyordu.
 * [toEpochDay] gunu 1..31 arasina cekip kabul ettigi icin hata patlamiyor,
 * sessizce BASKA BIR GUNE tasiyor; [dateKeyOf] ise var olmayan bir anahtar
 * yaziyordu. Ustelik gecersiz tarih `goals` tablosuna kalici yazilabiliyordu
 * (bkz. goalProjection -> hedef tarihi).
 */
fun KefeDate.plusMonths(count: Int): KefeDate {
    val zeroBased = (year * 12) + (month - 1) + count
    val newYear = zeroBased / 12
    val newMonth = (zeroBased % 12) + 1
    return KefeDate(
        year = newYear,
        month = newMonth,
        day = day.coerceAtMost(daysInMonth(newYear, newMonth)),
    )
}

/** O ayin gun sayisi - artik yil dahil. */
fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 31
}

fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

/**
 * [KefeDate.toEpochDay]'in TERSI.
 *
 * Uzun sure yoktu ve yoklugu semaya kadar yansimisti: tarihler tek epochDay
 * kolonu yerine yil/ay/gun ucluleri halinde saklaniyor. Piyasa gununu duvar
 * saatinden turetmek icin gerekti (bkz. KefeClock.marketToday).
 *
 * Algoritma Howard Hinnant'in civil_from_days'i - [KefeDate.toEpochDay]'de
 * kullanilan days_from_civil'in tam tersi.
 */
fun kefeDateOfEpochDay(days: Long): KefeDate {
    val z = days + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = z - era * 146097L                                  // [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)             // [0, 365]
    val mp = (5 * doy + 2) / 153                                  // Mart = 0
    val d = doy - (153 * mp + 2) / 5 + 1                          // [1, 31]
    val m = if (mp < 10) mp + 3 else mp - 9                       // [1, 12]
    return KefeDate(
        year = (if (m <= 2) y + 1 else y).toInt(),
        month = m.toInt(),
        day = d.toInt(),
    )
}

package com.kefe.app.domain.model

/**
 * Takvim ayi - aylik plan, gelir ve giderin donem birimi.
 *
 * [KefeDate] gunu de tasir ve "Ekim planı" diyen bir satirda gun hicbir sey
 * ifade etmez; gunu tasiyan bir tip kullanmak her kiyaslamada gunu yok saymayi
 * hatirlamak demekti. Hedef tarihinde bu unutuldu ve icinde bulunulan aya
 * kurulan hedef dogar dogmaz "gecikmis" sayildi (bkz. [isOverdue]).
 *
 * Sira ve fark [monthOrdinal] ile hesaplanir - ay sayaci projede tek yerde
 * yasar, ucuncu bir kopyasi acilmaz.
 */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {

    init {
        require(month in 1..12) { "gecersiz ay: $month" }
    }

    /** Yil*12 + (ay-1): ardisik aylar ardisik sayilardir. */
    val ordinal: Int get() = firstDay().monthOrdinal()

    operator fun plus(months: Int): YearMonth = ofOrdinal(ordinal + months)

    operator fun minus(months: Int): YearMonth = ofOrdinal(ordinal - months)

    fun previous(): YearMonth = this - 1

    fun next(): YearMonth = this + 1

    fun firstDay(): KefeDate = KefeDate(year, month, 1)

    fun lastDay(): KefeDate = KefeDate(year, month, daysInMonth(year, month))

    /** Tarih bu aya mi dusuyor. */
    operator fun contains(date: KefeDate): Boolean = date.year == year && date.month == month

    override fun compareTo(other: YearMonth): Int = ordinal.compareTo(other.ordinal)

    companion object {
        fun of(date: KefeDate): YearMonth = YearMonth(date.year, date.month)

        fun ofOrdinal(ordinal: Int): YearMonth {
            val date = monthDateOf(ordinal)
            return YearMonth(date.year, date.month)
        }
    }
}

/** "Ekim 2026". */
fun YearMonth.label(): String = firstDay().formatMonthYear()

/** "Ekim". */
fun YearMonth.monthName(): String = firstDay().monthName()

/** Iki ay arasindaki fark: `this - other` ay. */
fun YearMonth.monthsSince(other: YearMonth): Int = ordinal - other.ordinal

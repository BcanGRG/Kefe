package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.ExpenseBudget
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.toEpochDay

/**
 * Kullanicinin GERCEK Ekim 2026 defteri (telefondan, 7 Ekim): 30 harcama, 10
 * aylik gider. Harcamalar sayfasi ve ana ekran widget'i testleri ayni veriye bakar.
 */
internal object RealOctober {

    val oct = YearMonth(2026, 10)
    val today = KefeDate(2026, 10, 7)

    val kk = ExpenseCategory.custom("Kredi Kartı Limit")!!
    val pilates = ExpenseCategory.custom("Merve Pilates")!!
    val yemek = ExpenseCategory.custom("Dışarıda yemek")!!
    val halisaha = ExpenseCategory.custom("Halisaha")!!
    val market = ExpenseCategory.Groceries
    val fatura = ExpenseCategory.Bills
    val diger = ExpenseCategory.Other

    val book = MonthBook(
        month = oct,
        budgets = listOf(
            budget(ExpenseCategory.Housing, 25_000.0),
            budget(kk, 15_000.0),
            budget(ExpenseCategory.custom("Merve Pastacılık")!!, 12_000.0),
            budget(ExpenseCategory.custom("Bolu Gezisi")!!, 10_000.0),
            budget(market, 7_000.0),
            budget(yemek, 5_000.0),
            budget(ExpenseCategory.custom("Bağış")!!, 5_000.0),
            budget(ExpenseCategory.Transport, 3_000.0),
            budget(pilates, 3_000.0),
            budget(fatura, 2_000.0),
        ),
        expenses = listOf(
            e(1, market, 66.38, "Macrocenter muz", at(1, 15, 27)),
            e(1, kk, 190.0, "Damacana su", at(1, 15, 28)),
            e(1, kk, 1_090.0, "Merve kiyafet", at(1, 18, 27)),
            e(1, kk, 140.0, "Dis fircasi basligi", at(1, 18, 28)),
            e(1, kk, 810.97, "Merve sampuan", at(1, 18, 29)),
            e(1, market, 205.0, "Pazar alisverisi", at(1, 18, 29, 30)),
            e(1, pilates, 3_000.0, null, at(1, 18, 30)),
            e(1, kk, 260.0, "Dondurma", at(1, 21, 55)),
            e(2, diger, 500.0, "Ecem düğün", at(2, 9, 34)),
            e(2, market, 207.0, null, at(2, 9, 59)),
            e(2, kk, 130.0, "Merve ayakkabı bağcık", at(2, 18, 36)),
            e(2, kk, 1_800.0, "Merve mont", at(2, 18, 58)),
            e(2, market, 2_024.0, "Gimat", at(2, 19, 58)),
            e(2, market, 100.0, "Bim", at(2, 20, 15)),
            e(3, fatura, 800.0, "İnternet", at(3, 12, 28)),
            e(3, market, 101.5, null, at(3, 13, 9)),
            e(4, kk, 200.0, null, at(4, 10, 27)),
            e(5, kk, 200.0, "Su", at(5, 9, 8)),
            e(5, kk, 623.57, "Merve yüz krem", at(5, 17, 4)),
            e(5, kk, 130.0, "Dondurma", at(5, 17, 32)),
            e(6, kk, 680.0, "Merve çiçek", at(6, 8, 15)),
            e(6, fatura, 693.32, "Su", at(6, 10, 35)),
            e(6, fatura, 565.0, "Elektrik", at(6, 14, 26)),
            e(7, diger, 150.0, "Oğuzhan çiçek", at(7, 8, 43)),
            e(7, kk, 120.0, "Su", at(7, 11, 49)),
            // Eylul sonunda Ekim'e girildi - eski kural ayin son gununu yaziyordu.
            e(31, market, 277.5, null, sept(27, 19, 36)),
            e(31, market, 95.0, null, sept(28, 20, 10)),
            e(31, kk, 1_050.0, "Avokado", sept(29, 21, 10)),
            e(31, yemek, 1_175.0, "Masabasi burak", sept(30, 21, 49)),
            e(31, halisaha, 370.0, null, sept(30, 21, 49, 30)),
        ),
    )

    private var seq = 0

    private fun e(day: Int, category: ExpenseCategory, amount: Double, note: String?, createdAt: Long) = ExpenseEntry(
        id = "x${seq++}",
        date = KefeDate(2026, 10, day),
        category = category,
        amount = amount,
        note = note,
        createdAt = createdAt,
    )

    private fun budget(category: ExpenseCategory, amount: Double) =
        ExpenseBudget("b_${category.name}", oct, category, amount)

    /** Ekim'in [day]'i, Turkiye saatiyle hh:mm(:ss). */
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) = istanbul(KefeDate(2026, 10, day), hour, minute, second)

    private fun sept(day: Int, hour: Int, minute: Int, second: Int = 0) = istanbul(KefeDate(2026, 9, day), hour, minute, second)

    private fun istanbul(date: KefeDate, hour: Int, minute: Int, second: Int): Long =
        date.toEpochDay() * 86_400_000L + ((hour * 60L + minute) * 60L + second) * 1_000L - 3L * 3_600_000L

    /** Ekim'in [day]'i, Turkiye saatiyle. */
    fun octAt(day: Int, hour: Int, minute: Int): Long = at(day, hour, minute)
}

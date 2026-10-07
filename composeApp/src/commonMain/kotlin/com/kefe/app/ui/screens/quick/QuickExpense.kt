package com.kefe.app.ui.screens.quick

import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.daysInMonth
import com.kefe.app.domain.model.kefeDateOfEpochDay
import com.kefe.app.domain.model.monthFlow
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.model.monthName
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.screens.plan.customCategoriesOf
import com.kefe.app.ui.screens.plan.entryClock
import com.kefe.app.ui.screens.plan.percent
import com.kefe.app.ui.screens.plan.plannedCategoriesOf
import kotlin.math.floor

/**
 * Ana ekrandan harcama: widget ve hizli giris penceresinin ortak turetimi.
 *
 * NEYDI: kullanici harcamasini her gun aninda giriyor; uygulamayi acip Plan'a
 * gidip formu acmak yavasti. Tasarim onayli (Ekim 2026): widget bugunu, ayin
 * gidisini ve en cok girilen dort kalemi gosterir; kaleme ya da + dugmesine
 * dokununca uygulama acilmadan kucuk bir giris penceresi gelir.
 */

/** Widget'ta ve hizli giriste bir kalem. */
data class QuickCategoryUi(
    val category: ExpenseCategory,
    val label: String,
    val initial: String,
    /** Harcamalar sayfasiyla ayni renk sirasi (ayin harcamasina gore); -1 = plan disi, soluk. */
    val colorIndex: Int,
)

/** Buyuk widget'in "Son girilenler" satiri. */
data class QuickRecentUi(
    val title: String,
    /** "11:49" (bugun), "dün", "5 Eki". */
    val whenText: String,
    val amount: String,
    val initial: String,
    val colorIndex: Int,
)

/** Ayin aylik giderlere gore gidisi; aylik gider yoksa widget bu satiri cizmez. */
data class WidgetMonthUi(
    /** "Ekim · ₺16.734 / ₺87.000" */
    val text: String,
    /** "%19 · ayın %23'ü" */
    val ratioText: String,
    val ratio: Float,
    val todayRatio: Float,
    val over: Boolean,
)

data class ExpenseWidgetUi(
    /** "BUGÜN · 7 EKİM" */
    val todayLabel: String,
    val todayTotal: String,
    /** "2 harcama · günlük pay ≈ ₺2.927" */
    val todayLine: String,
    /** Kucuk boy: yalniz "2 harcama". */
    val countText: String,
    val month: WidgetMonthUi?,
    val tiles: List<QuickCategoryUi>,
    val recent: List<QuickRecentUi>,
    /** "Harcamalar · 30 harcama" */
    val pageLink: String,
)

/** Not cipi: "Su ₺120" - dokununca not ve tutar dolar. */
data class NoteSuggestionUi(val note: String, val amount: Double, val amountText: String)

enum class QuickTone { Muted, Warning, Negative }

/** Kalem cipinin altindaki satir: "Kalan ₺7.575,46 → ₺7.455,46". */
data class QuickLineUi(val text: String, val tone: QuickTone)

/**
 * Widget'in butun icerigi. [books] butun aylar; bugun ve ay [today]'in ayindan,
 * kalemler son [RecentDays] gunun girislerinden.
 */
fun expenseWidget(books: List<MonthBook>, today: KefeDate): ExpenseWidgetUi {
    val month = YearMonth.of(today)
    val book = books.firstOrNull { it.month == month } ?: MonthBook(month)
    val flow = monthFlow(book, emptyList(), plannedInvest = null)
    val colors = categoryColors(book)

    val todays = book.expenses.filter { it.date == today }
    val countText = if (todays.isEmpty()) "Harcama yok" else "${todays.size} harcama"
    val limit = flow.budgetTotal?.takeIf { it > 0.0 }
    val allowance = limit?.let { dailyAllowance(it - flow.spentInPlan, today) }

    val monthUi = limit?.let {
        val ratio = flow.spentInPlan / it
        val todayRatio = today.day.toDouble() / daysInMonth(today.year, today.month)
        val dayText = percent(todayRatio)
        WidgetMonthUi(
            text = "${today.monthName()} · ${Money.tl(flow.spentInPlan)} / ${Money.tl(it)}",
            ratioText = "${percent(ratio)} · ayın $dayText${possessive(dayText)}",
            ratio = ratio.coerceIn(0.0, 1.0).toFloat(),
            todayRatio = todayRatio.toFloat(),
            over = flow.spentInPlan > it + Tolerance,
        )
    }

    val recent = books.asSequence().flatMap { it.expenses }
        .sortedWith(compareByDescending<ExpenseEntry> { it.createdAt }.thenByDescending { it.date.toEpochDay() })
        .take(RecentCount)
        .map { e ->
            QuickRecentUi(
                title = e.noteOrNull() ?: e.category.label(),
                whenText = whenText(e, today),
                amount = Money.tlExact(e.amount),
                initial = initialOf(e.category),
                colorIndex = colors[e.category] ?: -1,
            )
        }
        .toList()

    return ExpenseWidgetUi(
        todayLabel = "Bugün · ${today.day} ${today.monthName()}".trUpper(),
        todayTotal = Money.tlExact(todays.sumOf { it.amount }),
        todayLine = listOfNotNull(countText, allowance).joinToString(" · "),
        countText = countText,
        month = monthUi,
        tiles = quickCategories(books, today).take(TileCount).map { quickCategoryUi(it, colors) },
        recent = recent,
        pageLink = "Harcamalar · ${book.expenses.size} harcama",
    )
}

/**
 * Kalemler, en cok girilen once: son [RecentDays] gunde girisi olanlar sayiya
 * (esitlikte en son girilen) gore; ardindan ayin aylik giderleri, hazir dokuz
 * ve daha once kullanilan ozel kalemler. Widget ilk dordunu, giris penceresi
 * hepsini gosterir.
 */
fun quickCategories(books: List<MonthBook>, today: KefeDate): List<ExpenseCategory> {
    val last = today.toEpochDay()
    val first = last - (RecentDays - 1)
    val byUse = books.asSequence().flatMap { it.expenses }
        .filter { it.date.toEpochDay() in first..last }
        .groupBy { it.category }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<ExpenseCategory, List<ExpenseEntry>>> { it.value.size }
                .thenByDescending { entry -> entry.value.maxOf { it.createdAt } },
        )
        .map { it.key }
    val month = YearMonth.of(today)
    val planned = plannedCategoriesOf(books.firstOrNull { it.month == month } ?: MonthBook(month))
    return (byUse + planned + ExpenseCategory.entries + customCategoriesOf(books)).distinct()
}

/** Kalem cipleri: renk ayin harcama sirasindan (Harcamalar sayfasiyla ayni). */
fun quickCategoryUis(books: List<MonthBook>, today: KefeDate, categories: List<ExpenseCategory>): List<QuickCategoryUi> {
    val month = YearMonth.of(today)
    val colors = categoryColors(books.firstOrNull { it.month == month } ?: MonthBook(month))
    return categories.map { quickCategoryUi(it, colors) }
}

/**
 * Kalemin sik girilen notlari, son [SuggestionDays] gunden: en cok tekrar eden
 * once (esitlikte en son girilen). Tutar o notun SON girisinden - "Su" bir gun
 * ₺120, bir gun ₺200 olsa da en yakini onerilir.
 */
fun noteSuggestions(books: List<MonthBook>, category: ExpenseCategory, today: KefeDate): List<NoteSuggestionUi> {
    val first = today.toEpochDay() - (SuggestionDays - 1)
    return books.asSequence().flatMap { it.expenses }
        .filter { it.category == category && it.date.toEpochDay() >= first }
        .mapNotNull { e -> e.noteOrNull()?.let { it to e } }
        .groupBy { it.first.lowercase() }
        .values
        .map { group -> group.size to group.map { it.second }.maxWith(compareBy<ExpenseEntry> { it.createdAt }.thenBy { it.date.toEpochDay() }) }
        .sortedWith(compareByDescending<Pair<Int, ExpenseEntry>> { it.first }.thenByDescending { it.second.createdAt })
        .take(SuggestionCount)
        .map { (_, latest) -> NoteSuggestionUi(latest.noteOrNull().orEmpty(), latest.amount, Money.tlExact(latest.amount)) }
}

/**
 * Secilen kalemin aylik giderine gore satir; ayda hic aylik gider yoksa null.
 * [amount] yazilan tutar (0 = henuz yok).
 */
fun quickLine(book: MonthBook, category: ExpenseCategory, amount: Double): QuickLineUi? {
    val flow = monthFlow(book, emptyList(), plannedInvest = null)
    if (flow.budgetByCategory.isEmpty()) return null
    val limit = flow.budgetByCategory[category]
        ?: return QuickLineUi("Bu ayın aylık giderlerinde yok · plan dışı sayılır", QuickTone.Muted)
    val left = limit - (flow.expensesByCategory[category] ?: 0.0)
    val after = left - amount
    return when {
        left < -Tolerance -> QuickLineUi(
            "${Money.tlExact(-left)} aşıldı" + if (amount > 0.0) " · bununla ${Money.tlExact(-after)}" else "",
            QuickTone.Negative,
        )
        after < -Tolerance -> QuickLineUi("Kalan ${Money.tlExact(left)} · bununla ${Money.tlExact(-after)} aşılır", QuickTone.Warning)
        amount > 0.0 -> QuickLineUi("Kalan ${Money.tlExact(left)} → ${Money.tlExact(after)}", QuickTone.Muted)
        else -> QuickLineUi("${category.label()} · kalan ${Money.tlExact(left)}", QuickTone.Muted)
    }
}

/** Kaydedince: "Kalan ₺7.455,46" / "₺58,32 aşıldı" / "Plan dışı harcama"; aylik gider yoksa null. */
fun quickSavedLine(book: MonthBook, category: ExpenseCategory, amount: Double): QuickLineUi? {
    val flow = monthFlow(book, emptyList(), plannedInvest = null)
    if (flow.budgetByCategory.isEmpty()) return null
    val limit = flow.budgetByCategory[category] ?: return QuickLineUi("Plan dışı harcama", QuickTone.Muted)
    val after = limit - (flow.expensesByCategory[category] ?: 0.0) - amount
    return if (after >= -Tolerance) {
        QuickLineUi("Kalan ${Money.tlExact(maxOf(after, 0.0))}", QuickTone.Muted)
    } else {
        QuickLineUi("${Money.tlExact(-after)} aşıldı", QuickTone.Negative)
    }
}

// --- Tus takimi -------------------------------------------------------------

sealed interface QuickKey {
    data class Digit(val digit: Char) : QuickKey
    data object Comma : QuickKey
    data object Back : QuickKey

    /** Geri silmeye basili tutunca: tutar bosalir. */
    data object Clear : QuickKey
}

/**
 * Tus takiminin yazdigi HAM metin ("1050", "12,5"): virgul bir kez, en cok iki
 * kurus hanesi, en cok dokuz lira hanesi; bastaki tek sifirin yerine rakam gecer.
 */
fun typeKey(text: String, key: QuickKey): String = when (key) {
    QuickKey.Back -> text.dropLast(1)
    QuickKey.Clear -> ""
    QuickKey.Comma -> if (',' in text) text else text.ifEmpty { "0" } + ","
    is QuickKey.Digit -> {
        val comma = text.indexOf(',')
        when {
            comma >= 0 && text.length - comma - 1 >= MaxCentDigits -> text
            comma < 0 && text.length >= MaxLiraDigits -> text
            text == "0" -> key.digit.toString()
            else -> text + key.digit
        }
    }
}

/** Buyuk tutar: "₺1.050", "₺12,5", bossa "₺0". */
fun shownAmount(text: String): String {
    if (text.isEmpty()) return "${Money.LIRA}0"
    val comma = text.indexOf(',')
    val lira = if (comma >= 0) text.substring(0, comma) else text
    val cents = if (comma >= 0) "," + text.substring(comma + 1) else ""
    return Money.LIRA + groupDigits(lira.ifEmpty { "0" }) + cents
}

// --- Yardimci ---------------------------------------------------------------

private fun quickCategoryUi(category: ExpenseCategory, colors: Map<ExpenseCategory, Int>) = QuickCategoryUi(
    category = category,
    label = category.label(),
    initial = initialOf(category),
    colorIndex = colors[category] ?: -1,
)

/** Harcamalar sayfasiyla ayni: aylik gideri olan kalemler ayin harcamasina gore. */
private fun categoryColors(book: MonthBook): Map<ExpenseCategory, Int> {
    val flow = monthFlow(book, emptyList(), plannedInvest = null)
    return flow.expensesByCategory.entries
        .filter { it.key in flow.budgetByCategory }
        .sortedByDescending { it.value }
        .mapIndexed { index, entry -> entry.key to index }
        .toMap()
}

/** Kalan aylik giderin gune dusen kismi - Harcamalar sayfasindaki "GÜNDE" ile ayni. */
private fun dailyAllowance(left: Double, today: KefeDate): String {
    val daysLeft = daysInMonth(today.year, today.month) - today.day
    return when {
        left < -Tolerance -> "aylık giderler ${Money.tlExact(-left)} aşıldı"
        left <= Tolerance -> "aylık giderler doldu"
        daysLeft <= 0 -> "ay sonuna ${Money.tlExact(left)}"
        else -> "günlük pay ≈ ${Money.tlExact(floor(left / daysLeft))}"
    }
}

private fun whenText(e: ExpenseEntry, today: KefeDate): String = when (e.date.toEpochDay() - today.toEpochDay()) {
    0L -> entryClock(e) ?: "bugün"
    -1L -> "dün"
    else -> "${e.date.day} ${e.date.monthLabel()}"
}

private fun initialOf(category: ExpenseCategory): String =
    category.label().take(1).trUpper()

private fun ExpenseEntry.noteOrNull(): String? = note?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Yuzdeden sonraki iyelik eki: "%23'ü", "%19'u", "%50'si", "%6'sı". Sayinin
 * okunusunun son hecesine gore (yirmi uc -> "ü").
 */
internal fun possessive(percentText: String): String {
    val n = percentText.filter { it.isDigit() }.toIntOrNull() ?: return ""
    val last = when {
        n == 0 -> "sıfır"
        n % 10 != 0 -> Ones[n % 10]
        n % 100 != 0 -> Tens[(n / 10) % 10]
        else -> "yüz"
    }
    val vowel = last.last { it in "aeıioöuü" }
    val harmony = when (vowel) {
        'a', 'ı' -> 'ı'
        'e', 'i' -> 'i'
        'o', 'u' -> 'u'
        else -> 'ü'
    }
    return "'" + (if (last.last() in "aeıioöuü") "s" else "") + harmony
}

private val Ones = listOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
private val Tens = listOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")

private fun groupDigits(digits: String): String =
    digits.reversed().chunked(3).joinToString(".").reversed()

/** Bugunun dunu - "Dün" secimi; ay basinda onceki aya duser. */
fun KefeDate.yesterday(): KefeDate = kefeDateOfEpochDay(toEpochDay() - 1)

/** Kalem siralamasinin baktigi pencere. */
private const val RecentDays = 30L

/** Not onerilerinin baktigi pencere. */
private const val SuggestionDays = 60L

private const val TileCount = 4
private const val RecentCount = 3
private const val SuggestionCount = 4
private const val MaxCentDigits = 2
private const val MaxLiraDigits = 9

/** Kurus alti fark asim sayilmaz. */
private const val Tolerance = 0.005

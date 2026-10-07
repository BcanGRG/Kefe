package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.daysInMonth
import com.kefe.app.domain.model.kefeDateOfEpochDay
import com.kefe.app.domain.model.label
import com.kefe.app.domain.model.monthFlow
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.model.monthName
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.screens.goals.trMonthLocative
import kotlin.math.floor
import kotlin.math.round

/**
 * "Harcamalar" sayfasi: bir ayin harcamalari, tumu ya da tek kalem.
 *
 * NEYDI: Plan sekmesindeki kart en yeni 10 girisi karisik gosteriyordu ve
 * "Kredi Kartı Limit"e dokununca hicbir sey olmuyordu. Kullanici bir kalemin
 * harcamalarini SIRASIYLA ve NOTLARIYLA gormek istedi. Tasarim onayli (Ekim 2026):
 * ayri sayfa, ustte kalem cipleri, ozet, kalemde gunluk grafik, gun gun liste.
 */
sealed interface ExpenseFilter {
    data object All : ExpenseFilter

    /** Aylik gideri olmayan butun kalemler. */
    data object Unplanned : ExpenseFilter

    data class Category(val category: ExpenseCategory) : ExpenseFilter

    /** Gezinme anahtarinda tasinan bicim; null = tumu. */
    fun key(): String? = when (this) {
        All -> null
        Unplanned -> UnplannedKey
        is Category -> category.name
    }

    companion object {
        private const val UnplannedKey = "__plan_disi"

        fun of(key: String?): ExpenseFilter = when (key) {
            null -> All
            UnplannedKey -> Unplanned
            else -> Category(ExpenseCategory.fromName(key))
        }
    }
}

enum class ExpenseSort { Date, Amount }

data class ExpensesPageUi(
    val title: String,
    /** "Ekim 2026 · 24 gün kaldı" | "Ekim 2026 · aylık gider" | "Ekim 2026 · plan dışı". */
    val subtitle: String,
    val chips: List<ExpenseChipUi>,
    val summary: ExpensesSummaryUi,
    /** Yalniz tek kalemde; o kalemde hic harcama yoksa null. */
    val daily: DailySpendUi?,
    /** "14 harcama"; suzgecte harcama yoksa null. */
    val countLabel: String?,
    /** Tarihe gore: gun gun, en yeni gun ustte. */
    val groups: List<ExpenseDayGroupUi>,
    /** Tutara gore: buyukten kucuge, gunsuz. */
    val ranked: List<ExpenseLineUi>,
    val emptyText: String?,
    val addLabel: String,
    /** "Bu kaleme harcama ekle" formu bu kalemle acar. */
    val addCategory: ExpenseCategory?,
)

data class ExpenseChipUi(
    val label: String,
    val count: Int,
    val filter: ExpenseFilter,
    val selected: Boolean = false,
)

sealed interface ExpensesSummaryUi {
    /** Tumu: ay toplami ve kalemlere gore dagilim. */
    data class Overview(val total: String, val line: String, val split: List<SplitRowUi>) : ExpensesSummaryUi

    /** Aylik gideri olan kalem: sinira karsi. */
    data class Budgeted(
        val spent: String,
        val limit: String,
        /** Harcanan / sinir, 1'de kirpilir. */
        val ratio: Float,
        val over: Boolean,
        /** "%49 harcandı" | "%103 harcandı". */
        val spentText: String,
        /** Ayin gecen kismi - yalniz bu ayda ("bugün · %23"). */
        val todayRatio: Float?,
        val todayText: String?,
        val pace: PaceUi?,
        val stats: List<StatUi>,
    ) : ExpensesSummaryUi

    /** Aylik gideri olmayan kalem ya da "Plan dışı" suzgeci. */
    data class Unbudgeted(
        val total: String,
        val line: String,
        val stats: List<StatUi>,
        val split: List<SplitRowUi>,
    ) : ExpensesSummaryUi
}

data class SplitRowUi(
    val label: String,
    val amount: String,
    /** "%42" */
    val share: String,
    val weight: Float,
    /** Kalemin rengi; -1 = plan disi (sonuk). */
    val colorIndex: Int,
    val filter: ExpenseFilter,
)

/** "Hızlı gidiyor ..." uyari, ya da asim. */
data class PaceUi(val text: String, val over: Boolean)

data class StatUi(val label: String, val value: String, val note: String)

data class DailySpendUi(val bars: List<DayBarUi>, val peak: String, val axis: List<String>)

data class DayBarUi(val day: Int, val ratio: Float, val kind: DayBarKind)

enum class DayBarKind { Empty, Spent, Today, Ahead }

data class ExpenseDayGroupUi(
    /** "7 Ekim Çarşamba" */
    val title: String,
    /** "bugün" | "ileri tarihli" | null */
    val tag: String?,
    val total: String,
    /** "27–30 Eylül'de girildi" - gunden ONCE girilen harcamalar icin; yoksa null. */
    val hint: String?,
    val items: List<ExpenseLineUi>,
)

data class ExpenseLineUi(
    val id: String,
    /** Not; not yoksa kalemin adi (tumu) ya da "Not girilmedi" (tek kalem). */
    val title: String,
    val titleMuted: Boolean,
    /** Tumu: kalem; tek kalem: giris saati ("11:49") ya da "29 Eylül'de girildi". */
    val sub: String,
    val amount: String,
    val unplanned: Boolean,
    /** Sol kutudaki harf; tek kalemde null (kutu cizilmez). */
    val initial: String?,
    val colorIndex: Int,
)

/**
 * Sayfanin butun icerigi - saf, test edilebilir.
 *
 * [today] gercek gun: "bugün", "ileri tarihli" ve ayin gecen kismi ondan.
 */
internal fun expensesPage(
    book: MonthBook,
    today: KefeDate,
    filter: ExpenseFilter,
    sort: ExpenseSort,
): ExpensesPageUi {
    val month = book.month
    val flow = monthFlow(book, emptyList(), plannedInvest = null)
    val budgets = flow.budgetByCategory
    val spentBy = flow.expensesByCategory
    val all = book.expenses
    val relation = when {
        month == YearMonth.of(today) -> Relation.Current
        month < YearMonth.of(today) -> Relation.Past
        else -> Relation.Future
    }

    // Renk ayin harcama sirasindan: sayfa suzulse de bir kalemin rengi degismez.
    val plannedBySpend = spentBy.entries.filter { it.key in budgets }.sortedByDescending { it.value }
    val colorOf = plannedBySpend.mapIndexed { index, entry -> entry.key to index }.toMap()

    val entries = when (filter) {
        ExpenseFilter.All -> all
        ExpenseFilter.Unplanned -> all.filter { it.category !in budgets }
        is ExpenseFilter.Category -> all.filter { it.category == filter.category }
    }
    val single = filter is ExpenseFilter.Category

    val chips = buildList {
        add(ExpenseChipUi("Tümü", all.size, ExpenseFilter.All))
        plannedBySpend.forEach { (category, _) ->
            add(ExpenseChipUi(category.label(), all.count { it.category == category }, ExpenseFilter.Category(category)))
        }
        val unplannedCount = all.count { it.category !in budgets }
        if (unplannedCount > 0) add(ExpenseChipUi("Plan dışı", unplannedCount, ExpenseFilter.Unplanned))
        // Harcamasi olmayan kalemden (ya da plan disi tek kalemden) acildiysa cipi de olsun.
        if (filter is ExpenseFilter.Category && none { it.filter == filter }) {
            add(ExpenseChipUi(filter.category.label(), entries.size, filter))
        }
    }.map { it.copy(selected = it.filter == filter) }

    val (title, subtitle) = when (filter) {
        ExpenseFilter.All -> "Harcamalar" to monthSubtitle(month, today, relation)
        ExpenseFilter.Unplanned -> "Plan dışı" to "${month.label()} · aylık gideri olmayan kalemler"
        is ExpenseFilter.Category -> filter.category.label() to
            "${month.label()} · ${if (filter.category in budgets) "aylık gider" else "plan dışı"}"
    }

    val summary = when (filter) {
        ExpenseFilter.All -> ExpensesSummaryUi.Overview(
            total = Money.tlExact(flow.expenses),
            line = listOfNotNull(
                "${all.size} harcama",
                flow.spentInPlan.takeIf { it > 0.0 }?.let { "aylık giderlerden ${Money.tlExact(it)}" },
                flow.unplannedSpent.takeIf { it > 0.0 }?.let { "plan dışı ${Money.tlExact(it)}" },
            ).joinToString(" · "),
            split = buildList {
                plannedBySpend.forEach { (category, spent) ->
                    add(splitRow(category.label(), spent, flow.expenses, colorOf[category] ?: -1, ExpenseFilter.Category(category)))
                }
                if (flow.unplannedSpent > 0.0) {
                    val names = flow.unplannedCategories.take(SplitNameCount).joinToString(", ") { it.label() } +
                        if (flow.unplannedCategories.size > SplitNameCount) ", …" else ""
                    add(splitRow("Plan dışı · $names", flow.unplannedSpent, flow.expenses, -1, ExpenseFilter.Unplanned))
                }
            },
        )

        ExpenseFilter.Unplanned -> {
            val total = entries.sumOf { it.amount }
            ExpensesSummaryUi.Unbudgeted(
                total = Money.tlExact(total),
                line = "${entries.size} harcama · aylık gideri olmayan kalemler",
                stats = spendStats(entries, showCategory = true),
                split = entries.groupBy { it.category }
                    .map { (category, list) -> category to list.sumOf { it.amount } }
                    .sortedByDescending { it.second }
                    .map { (category, sum) -> splitRow(category.label(), sum, total, -1, ExpenseFilter.Category(category)) },
            )
        }

        is ExpenseFilter.Category -> {
            val spent = entries.sumOf { it.amount }
            val limit = budgets[filter.category]
            if (limit == null) {
                ExpensesSummaryUi.Unbudgeted(
                    total = Money.tlExact(spent),
                    line = "${entries.size} harcama · aylık gideri yok",
                    stats = spendStats(entries, showCategory = false).takeIf { entries.size > 1 }.orEmpty(),
                    split = emptyList(),
                )
            } else {
                budgetedSummary(spent, limit, entries, month, today, relation)
            }
        }
    }

    val daily = if (single) dailySpend(entries, month, today, relation) else null

    val lineOf = { e: ExpenseEntry -> line(e, single, sort, budgets, colorOf) }
    val groups = if (sort == ExpenseSort.Date) {
        entries.groupBy { it.date }
            .entries
            .sortedByDescending { it.key.toEpochDay() }
            .map { (date, list) ->
                val items = list.sortedByDescending { it.createdAt }
                ExpenseDayGroupUi(
                    title = "${date.day} ${date.monthName()} ${date.weekdayName()}",
                    tag = when {
                        date == today -> "bugün"
                        date.toEpochDay() > today.toEpochDay() -> "ileri tarihli"
                        else -> null
                    },
                    total = Money.tlExact(list.sumOf { it.amount }),
                    // Tek kalemde her satir kendi giris gununu yazar; ust yazi tekrar olurdu.
                    hint = if (single) null else advanceHint(date, items),
                    items = items.map(lineOf),
                )
            }
    } else {
        emptyList()
    }
    val ranked = if (sort == ExpenseSort.Amount) {
        entries.sortedWith(compareByDescending<ExpenseEntry> { it.amount }.thenByDescending { it.createdAt }).map(lineOf)
    } else {
        emptyList()
    }

    val emptyText = if (entries.isNotEmpty()) {
        null
    } else {
        val whenText = if (relation == Relation.Current) "bu ay" else trMonthLocative(month.month)
        when (filter) {
            ExpenseFilter.All -> "${whenText.replaceFirstChar { it.uppercase() }} harcama girilmedi."
            ExpenseFilter.Unplanned -> "${whenText.replaceFirstChar { it.uppercase() }} plan dışı harcama yok."
            is ExpenseFilter.Category -> "Bu kaleme $whenText harcama girilmedi."
        }
    }

    return ExpensesPageUi(
        title = title,
        subtitle = subtitle,
        chips = chips,
        summary = summary,
        daily = daily,
        countLabel = entries.size.takeIf { it > 0 }?.let { "$it harcama" },
        groups = groups,
        ranked = ranked,
        emptyText = emptyText,
        addLabel = if (single) "Bu kaleme harcama ekle" else "Harcama ekle",
        addCategory = (filter as? ExpenseFilter.Category)?.category,
    )
}

private enum class Relation { Past, Current, Future }

/** "Ekim 2026 · 24 gün kaldı" - Plan basligiyla ayni gun sayimi. */
private fun monthSubtitle(month: YearMonth, today: KefeDate, relation: Relation): String {
    if (relation != Relation.Current) return month.label()
    val left = daysInMonth(month.year, month.month) - today.day
    return "${month.label()} · " + if (left > 0) "$left gün kaldı" else "son gün"
}

private fun budgetedSummary(
    spent: Double,
    limit: Double,
    entries: List<ExpenseEntry>,
    month: YearMonth,
    today: KefeDate,
    relation: Relation,
): ExpensesSummaryUi.Budgeted {
    val ratio = if (limit > 0.0) spent / limit else 1.0
    val days = daysInMonth(month.year, month.month)
    val todayRatio = if (relation == Relation.Current) today.day.toDouble() / days else null
    val over = spent > limit + Tolerance
    val pace = when {
        over -> PaceUi("${Money.tlExact(spent - limit)} aşıldı", over = true)
        // Ayin gecen kismindan belirgin hizli: kullanici gunun birinde siniri asmadan gorsun.
        todayRatio != null && spent > 0.0 && ratio - todayRatio >= PaceMargin ->
            PaceUi("Hızlı gidiyor: harcanan ${percent(ratio)}, ayın geçen kısmı ${percent(todayRatio)}.", over = false)
        else -> null
    }
    val stats = buildList {
        if (over) {
            add(StatUi("AŞIM", Money.tlExact(spent - limit), "sınırın üstünde"))
        } else {
            add(StatUi("KALAN", Money.tlExact(limit - spent), "sınıra kadar"))
        }
        val left = limit - spent
        when (relation) {
            Relation.Current -> if (left > Tolerance) {
                val daysLeft = days - today.day
                if (daysLeft > 0) {
                    add(StatUi("GÜNDE", "≈ ${Money.tlExact(floor(left / daysLeft))}", "kalan $daysLeft gün için"))
                } else {
                    add(StatUi("BUGÜN", Money.tlExact(left), "ayın son günü"))
                }
            }
            Relation.Future -> add(StatUi("GÜNDE", "≈ ${Money.tlExact(floor(limit / days))}", "ay boyunca"))
            Relation.Past -> Unit
        }
        addAll(spendStats(entries, showCategory = false))
    }
    return ExpensesSummaryUi.Budgeted(
        spent = Money.tlExact(spent),
        limit = Money.tlExact(limit),
        ratio = ratio.coerceIn(0.0, 1.0).toFloat(),
        over = over,
        spentText = "${percent(ratio)} harcandı",
        todayRatio = todayRatio?.toFloat(),
        todayText = todayRatio?.let { "bugün · ${percent(it)}" },
        pace = pace,
        stats = stats,
    )
}

/** Ortalama ve en buyuk harcama; harcama yoksa bos. */
private fun spendStats(entries: List<ExpenseEntry>, showCategory: Boolean): List<StatUi> {
    if (entries.isEmpty()) return emptyList()
    val biggest = entries.maxWith(compareBy<ExpenseEntry> { it.amount }.thenBy { it.createdAt })
    val name = biggest.noteOrNull() ?: biggest.category.label().takeIf { showCategory } ?: "not yok"
    return listOf(
        StatUi("ORTALAMA", Money.tlExact(round(entries.sumOf { it.amount } / entries.size)), "${entries.size} harcama"),
        StatUi("EN BÜYÜK", Money.tlExact(biggest.amount), "$name · ${biggest.date.day} ${biggest.date.monthLabel()}"),
    )
}

private fun splitRow(label: String, amount: Double, total: Double, colorIndex: Int, filter: ExpenseFilter) = SplitRowUi(
    label = label,
    amount = Money.tlExact(amount),
    share = percent(if (total > 0.0) amount / total else 0.0),
    weight = if (total > 0.0) (amount / total).toFloat() else 0f,
    colorIndex = colorIndex,
    filter = filter,
)

/** Ayin her gunu bir cubuk; en yuksek gun tam boy. */
private fun dailySpend(entries: List<ExpenseEntry>, month: YearMonth, today: KefeDate, relation: Relation): DailySpendUi? {
    val days = daysInMonth(month.year, month.month)
    val byDay = entries.groupBy { it.date.day }.mapValues { (_, list) -> list.sumOf { it.amount } }
    val max = byDay.values.maxOrNull()?.takeIf { it > 0.0 } ?: return null
    val peakDay = byDay.entries.first { it.value == max }.key
    return DailySpendUi(
        bars = (1..days).map { day ->
            val amount = byDay[day] ?: 0.0
            DayBarUi(
                day = day,
                ratio = (amount / max).toFloat(),
                kind = when {
                    amount <= 0.0 -> DayBarKind.Empty
                    relation == Relation.Current && day == today.day -> DayBarKind.Today
                    relation == Relation.Future || (relation == Relation.Current && day > today.day) -> DayBarKind.Ahead
                    else -> DayBarKind.Spent
                },
            )
        },
        peak = "en yüksek $peakDay ${month.firstDay().monthLabel()} · ${Money.tlExact(max)}",
        axis = listOf("1", "8", "15", "22", "$days"),
    )
}

private fun line(
    e: ExpenseEntry,
    single: Boolean,
    sort: ExpenseSort,
    budgets: Map<ExpenseCategory, Double>,
    colorOf: Map<ExpenseCategory, Int>,
): ExpenseLineUi {
    val note = e.noteOrNull()
    val shortDate = "${e.date.day} ${e.date.monthLabel()}"
    val sub = when {
        sort == ExpenseSort.Amount -> if (!single && note != null) "$shortDate · ${e.category.label()}" else shortDate
        single -> entryMoment(e).orEmpty()
        note != null -> e.category.label()
        else -> entryMoment(e).orEmpty()
    }
    return ExpenseLineUi(
        id = e.id,
        title = note ?: if (single) "Not girilmedi" else e.category.label(),
        titleMuted = note == null && single,
        sub = sub,
        amount = Money.tlExact(e.amount),
        unplanned = !single && e.category !in budgets,
        initial = if (single) null else e.category.label().firstOrNull()?.let(::trUpper)?.toString(),
        colorIndex = colorOf[e.category] ?: -1,
    )
}

private fun ExpenseEntry.noteOrNull(): String? = note?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Giris ani: harcama gunu girildiyse saat ("11:49"), baska gun girildiyse o gun
 * ("29 Eylül'de girildi"). Damga yoksa (eski kayit) null.
 *
 * Saat Turkiye saatiyle (UTC+3, yaz saati yok - bkz. SystemKefeClock): ortak kodda
 * platform saat dilimi yok, uygulamanin kullanicisi Turkiye'de.
 */
internal fun entryMoment(e: ExpenseEntry): String? {
    if (e.createdAt <= 0L) return null
    val local = e.createdAt + IstanbulOffsetMillis
    val day = kefeDateOfEpochDay(floorDiv(local, DayMillis))
    if (day == e.date) {
        val minutes = floorMod(local, DayMillis) / MinuteMillis
        return "${pad2(minutes / 60)}:${pad2(minutes % 60)}"
    }
    return "${day.day} ${trMonthLocative(day.month)} girildi"
}

/** Gunden ONCE girilen harcamalar: "27–30 Eylül'de girildi" / "29 Eylül'de girildi". */
private fun advanceHint(date: KefeDate, items: List<ExpenseEntry>): String? {
    val entered = items.filter { it.createdAt > 0L }
        .map { kefeDateOfEpochDay(floorDiv(it.createdAt + IstanbulOffsetMillis, DayMillis)) }
        .filter { it.toEpochDay() < date.toEpochDay() }
    if (entered.isEmpty()) return null
    val first = entered.minBy { it.toEpochDay() }
    val last = entered.maxBy { it.toEpochDay() }
    return when {
        first == last -> "${first.day} ${trMonthLocative(first.month)} girildi"
        first.month == last.month && first.year == last.year ->
            "${first.day}–${last.day} ${trMonthLocative(last.month)} girildi"
        else -> "${first.day} ${first.monthName()} – ${last.day} ${trMonthLocative(last.month)} girildi"
    }
}

/** "%42"; sifirdan buyuk pay "%0" yazilmaz, eksik pay "%100" yazilmaz (Plan'daki kural). */
private fun percent(fraction: Double): String {
    val value = round(fraction * 100.0)
    return when {
        fraction > 0.0 && value < 1.0 -> "%<1"
        fraction < 1.0 && value >= 100.0 -> Money.ratio(99.0)
        else -> Money.ratio(value)
    }
}

private val WeekdayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

/** 1 Ocak 1970 bir Persembeydi: Pazartesi=0 sayiminda 3. */
internal fun KefeDate.weekdayName(): String = WeekdayNames[floorMod(toEpochDay() + 3, 7L).toInt()]

/** Turkce buyuk harf: "i" -> "İ" (Kotlin'in uppercase'i "I" verirdi). */
private fun trUpper(ch: Char): Char = when (ch) {
    'i' -> 'İ'
    'ı' -> 'I'
    else -> ch.uppercaseChar()
}

private fun pad2(value: Long): String = if (value < 10) "0$value" else "$value"

private fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if (a % b != 0L && (a xor b) < 0L) q - 1L else q
}

private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

private const val IstanbulOffsetMillis = 3L * 60L * 60L * 1000L
private const val DayMillis = 24L * 60L * 60L * 1000L
private const val MinuteMillis = 60L * 1000L

/** Kurus alti fark asim sayilmaz. */
private const val Tolerance = 0.005

/** Harcanan pay ayin gecen kismini bu kadar asarsa "hızlı gidiyor". */
private const val PaceMargin = 0.15

/** "Plan dışı · Diğer, Halisaha" satirinda adi gecen kalem sayisi. */
private const val SplitNameCount = 3

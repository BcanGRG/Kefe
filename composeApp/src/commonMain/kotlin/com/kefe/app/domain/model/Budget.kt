package com.kefe.app.domain.model

/**
 * GELIR, GIDER, BUTCE - hafif, kategori bazli.
 *
 * Banka entegrasyonu yok; her rakam elle giriliyor. Bu yuzden model bilerek
 * kaba: 9 hazir kategori ve kullanicinin adlandirdigi kalemler, ay basina
 * kategori butcesi, harcama tek tek ya da ay sonunda tek toplam olarak
 * girilebilir. Ince bir defter elle tutulmaz ve
 * yarim tutulan defter hic tutulmayandan daha cok yanlis soyler.
 *
 * Gelir KISI bazlidir (maas kimin), plan ve butce HANENIN.
 */

enum class IncomeKind {
    Salary,
    Extra;

    fun label(): String = when (this) {
        Salary -> "Maaş (net)"
        Extra -> "Ek gelir"
    }

    companion object {
        /** Bilinmeyen deger (daha yeni bir surumden) "Ek gelir" sayilir - kaybolmaz. */
        fun fromName(name: String): IncomeKind = entries.firstOrNull { it.name == name } ?: Extra
    }
}

/**
 * Giderin kategorisi: dokuz hazir kategori ya da kullanicinin adlandirdigi bir
 * kalem ("Düğün hediyesi", "Tatil").
 *
 * NEYDI: dokuz degerli bir enum'du. Her ay farkli seylere para veren hane icin
 * yetmedi; "Diğer"e yazilan her sey tek satirda toplaniyordu.
 *
 * Ozel kalem AYNI metin kolonunda saklanir: [name] "c:Tatil". Sunucu semasi,
 * senkron ve yedek degismez (kolonu duz metin olarak tasiyorlar); bu surumu
 * tanimayan eski bir kopya kalemi "Diğer"e dusurur, tutar toplamdan kaybolmaz.
 *
 * ESITLIK buyuk/kucuk harfe bakmaz: iki telefonda "Tatil" ve "tatil" yazilsa da
 * tek kalemdir (kart tek satir, butce tek kimlik - bkz. [idKey]).
 */
class ExpenseCategory private constructor(
    /** Diskteki ve sunucudaki metin: "Groceries" | "c:Tatil". */
    val name: String,
    private val fixedLabel: String?,
) {
    val isCustom: Boolean get() = fixedLabel == null

    fun label(): String = fixedLabel ?: name.removePrefix(CustomPrefix)

    /**
     * Butce kimliginin parcasi (bkz. budgetId). Hazir kategoride [name] - eski
     * kimlikler degismez. Ozel kalemde kucuk harfli ad, bosluklar tireyle:
     * "c_düğün-hediyesi".
     */
    val idKey: String get() = if (isCustom) "c_" + matchKey.replace(' ', '-') else name

    private val matchKey: String get() = if (isCustom) label().lowercase() else name

    override fun equals(other: Any?): Boolean = other is ExpenseCategory && matchKey == other.matchKey

    override fun hashCode(): Int = matchKey.hashCode()

    override fun toString(): String = name

    companion object {
        private const val CustomPrefix = "c:"

        /** Ozel kalem adinin en fazla uzunlugu - kart satirina ve cipe sigsin. */
        const val MaxCustomLength: Int = 32

        val Housing = ExpenseCategory("Housing", "Konut/Kira")
        val Bills = ExpenseCategory("Bills", "Faturalar")
        val Groceries = ExpenseCategory("Groceries", "Market")
        val Transport = ExpenseCategory("Transport", "Ulaşım")
        val Debt = ExpenseCategory("Debt", "Kredi/Kart ekstresi")
        val Health = ExpenseCategory("Health", "Sağlık")
        val Education = ExpenseCategory("Education", "Eğitim")
        val Leisure = ExpenseCategory("Leisure", "Keyif")
        val Other = ExpenseCategory("Other", "Diğer")

        /** Dokuz HAZIR kategori, ekrandaki sirayla. Ozel kalemler veriden gelir. */
        val entries: List<ExpenseCategory> =
            listOf(Housing, Bills, Groceries, Transport, Debt, Health, Education, Leisure, Other)

        /**
         * Yazilan addan kategori; bos ad null. Bosluklar sadelesir, ad kirpilir.
         * Hazir bir kategorinin adi yazildiysa ("market") o kategori doner -
         * ayni para iki satira bolunmesin.
         */
        fun custom(typed: String): ExpenseCategory? {
            val clean = typed.trim().split(Whitespace).filter { it.isNotEmpty() }.joinToString(" ")
                .take(MaxCustomLength).trim()
            if (clean.isEmpty()) return null
            entries.firstOrNull { it.label().lowercase() == clean.lowercase() }?.let { return it }
            return ExpenseCategory(CustomPrefix + clean, null)
        }

        /** Diskteki metinden. Bilinmeyen deger "Diğer"e duser - tutar toplamdan kaybolmaz. */
        fun fromName(name: String): ExpenseCategory =
            entries.firstOrNull { it.name == name }
                ?: name.takeIf { it.startsWith(CustomPrefix) }?.let { custom(it.removePrefix(CustomPrefix)) }
                ?: Other

        private val Whitespace = Regex("\\s+")
    }
}

data class IncomeEntry(
    val id: String,
    val month: YearMonth,
    val memberId: String,
    val kind: IncomeKind,
    val amount: Double,
)

data class ExpenseEntry(
    val id: String,
    /** Harcamanin sayildigi ay bu tarihin ayidir. */
    val date: KefeDate,
    val category: ExpenseCategory,
    val amount: Double,
    val note: String? = null,
    val addedByMemberId: String? = null,
    val createdAt: Long = 0L,
) {
    val month: YearMonth get() = YearMonth.of(date)
}

data class ExpenseBudget(
    val id: String,
    val month: YearMonth,
    val category: ExpenseCategory,
    val amount: Double,
)

/** Bir ayin elle girilen defteri. */
data class MonthBook(
    val month: YearMonth,
    val incomes: List<IncomeEntry> = emptyList(),
    val expenses: List<ExpenseEntry> = emptyList(),
    val budgets: List<ExpenseBudget> = emptyList(),
) {
    val isEmpty: Boolean get() = incomes.isEmpty() && expenses.isEmpty() && budgets.isEmpty()
}

/**
 * Ayin para akisi. null alanlar "bilinmiyor" demektir; ekran "—" yazar, 0 degil.
 *
 * AYLIK GIDER ile HARCAMA ayri seylerdir:
 * - Aylik gider ("butce" satiri) o ay icin AYRILAN paradir: Kira ₺25.000, Market
 *   ₺7.000. Hic harcama girilmese de tam sayilir - kirayi her ay harcama olarak
 *   girmek gerekmez.
 * - Harcama (gider kaydi) gun icinde girilen anlik odemedir. Aylik gideri olan bir
 *   kalemdeyse o kalemin ICINDEN yenir (Market ₺372 / ₺7.000) ve ayrica dusmez;
 *   kalemi asarsa yalniz ASIM eklenir. Aylik gideri olmayan kalemdeki harcama
 *   "plan disi"dir ve tam eklenir.
 *
 * Yani ayin gideri = aylik giderler + asimlar + plan disi harcamalar.
 * NEYDI: yalniz harcamalar sayiliyordu; butce bir sinirdi. Kira butcede dururken
 * gidere yansimiyor, kullanici onu harcama olarak girip siliyordu.
 */
data class MonthFlow(
    val income: Double?,
    val incomeByMember: Map<String, Double>,
    /** Girilen BUTUN harcamalar (anlik kayitlar). */
    val expenses: Double,
    val expensesByCategory: Map<ExpenseCategory, Double>,
    /** Aylik giderlerin toplami; hic aylik gider yoksa null. */
    val budgetTotal: Double?,
    val budgetByCategory: Map<ExpenseCategory, Double>,
    /** Aylik gideri olan kalemlerde harcanan (asim dahil). */
    val spentInPlan: Double,
    /** Aylik giderini asan kalemlerin asimlari toplami. */
    val overPlan: Double,
    /** Aylik gideri olmayan kalemlerdeki harcamalar. */
    val unplannedSpent: Double,
    /** Plan disi harcamanin kalemleri, harcanana gore. */
    val unplannedCategories: List<ExpenseCategory>,
    /** Ayin gideri: aylik giderler + asimlar + plan disi harcamalar. */
    val outgoing: Double,
    /** Alimlar - satislar, komisyon dahil: Ozet'teki "Bu ay eklenen" ile AYNI tanim. */
    val investedNet: Double,
    val grossBuys: Double,
    val sells: Double,
    val plannedInvest: Double?,
    /** Gelir - gider - yatirim. */
    val remaining: Double?,
    /** Gelir - aylik giderler - planlanan yatirim: dagitilmamis para. */
    val plannedRemaining: Double?,
    /** (Gelir - gider) / gelir. */
    val savingsRate: Double?,
    /** Yatirim / gelir. */
    val investRate: Double?,
)

fun monthFlow(
    book: MonthBook,
    transactions: List<Transaction>,
    /** Ayin planindaki TL toplami; plan yoksa null. */
    plannedInvest: Double?,
): MonthFlow {
    val month = book.month
    val incomeByMember = book.incomes
        .groupBy { it.memberId }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
    val income = book.incomes.takeIf { it.isNotEmpty() }?.sumOf { it.amount }
    val expensesByCategory = book.expenses
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
    val expenses = expensesByCategory.values.sum()
    val budgetByCategory = book.budgets
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
    val budgetTotal = budgetByCategory.takeIf { it.isNotEmpty() }?.values?.sum()

    val inPlan = expensesByCategory.filterKeys { it in budgetByCategory }
    val unplanned = expensesByCategory.filterKeys { it !in budgetByCategory }
    val overPlan = inPlan.entries.sumOf { (category, spent) -> (spent - budgetByCategory.getValue(category)).coerceAtLeast(0.0) }
    val unplannedSpent = unplanned.values.sum()
    val outgoing = (budgetTotal ?: 0.0) + overPlan + unplannedSpent

    val monthTx = transactions.filter { it.date in month }
    val invested = monthTx.netContributionIn(month.year, month.month)
    val buys = monthTx.filter { it.side == TradeSide.Buy }.sumOf { it.total }
    val sells = monthTx.filter { it.side == TradeSide.Sell }.sumOf { it.total }

    return MonthFlow(
        income = income,
        incomeByMember = incomeByMember,
        expenses = expenses,
        expensesByCategory = expensesByCategory,
        budgetTotal = budgetTotal,
        budgetByCategory = budgetByCategory,
        spentInPlan = inPlan.values.sum(),
        overPlan = overPlan,
        unplannedSpent = unplannedSpent,
        unplannedCategories = unplanned.entries.sortedByDescending { it.value }.map { it.key },
        outgoing = outgoing,
        investedNet = invested,
        grossBuys = buys,
        sells = sells,
        plannedInvest = plannedInvest,
        remaining = income?.let { it - outgoing - invested },
        plannedRemaining = income?.let { it - (budgetTotal ?: 0.0) - (plannedInvest ?: 0.0) },
        savingsRate = income?.takeIf { it > 0.0 }?.let { (it - outgoing) / it },
        investRate = income?.takeIf { it > 0.0 }?.let { invested / it },
    )
}

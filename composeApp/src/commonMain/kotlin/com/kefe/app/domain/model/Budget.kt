package com.kefe.app.domain.model

/**
 * GELIR, GIDER, BUTCE - hafif, kategori bazli.
 *
 * Banka entegrasyonu yok; her rakam elle giriliyor. Bu yuzden model bilerek
 * kaba: 9 sabit kategori, ay basina kategori butcesi, harcama tek tek ya da ay
 * sonunda tek toplam olarak girilebilir. Ince bir defter elle tutulmaz ve
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

enum class ExpenseCategory {
    Housing,
    Bills,
    Groceries,
    Transport,
    Debt,
    Health,
    Education,
    Leisure,
    Other;

    fun label(): String = when (this) {
        Housing -> "Konut/Kira"
        Bills -> "Faturalar"
        Groceries -> "Market"
        Transport -> "Ulaşım"
        Debt -> "Kredi/Kart ekstresi"
        Health -> "Sağlık"
        Education -> "Eğitim"
        Leisure -> "Keyif"
        Other -> "Diğer"
    }

    companion object {
        /** Bilinmeyen kategori "Diğer"e duser - tutar toplamdan kaybolmaz. */
        fun fromName(name: String): ExpenseCategory = entries.firstOrNull { it.name == name } ?: Other
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

/** Ayin para akisi. null alanlar "bilinmiyor" demektir; ekran "—" yazar, 0 degil. */
data class MonthFlow(
    val income: Double?,
    val incomeByMember: Map<String, Double>,
    val expenses: Double,
    val expensesByCategory: Map<ExpenseCategory, Double>,
    val budgetTotal: Double?,
    val budgetByCategory: Map<ExpenseCategory, Double>,
    /** Alimlar - satislar, komisyon dahil: Ozet'teki "Bu ay eklenen" ile AYNI tanim. */
    val investedNet: Double,
    val grossBuys: Double,
    val sells: Double,
    val plannedInvest: Double?,
    /** Gelir - gider - yatirim: nakitte kalan ya da girilmemis bir kayit. */
    val remaining: Double?,
    /** Gelir - butce - planlanan yatirim: dagitilmamis para. */
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
        investedNet = invested,
        grossBuys = buys,
        sells = sells,
        plannedInvest = plannedInvest,
        remaining = income?.let { it - expenses - invested },
        plannedRemaining = income?.let { it - (budgetTotal ?: 0.0) - (plannedInvest ?: 0.0) },
        savingsRate = income?.takeIf { it > 0.0 }?.let { (it - expenses) / it },
        investRate = income?.takeIf { it > 0.0 }?.let { invested / it },
    )
}

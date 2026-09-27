package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.ExtraKind
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.MonthPlanProgress
import com.kefe.app.domain.model.PlanAssetOption
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanItemProgress
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.assetKey
import com.kefe.app.domain.model.buyPrice
import com.kefe.app.domain.model.copySourceFor
import com.kefe.app.domain.model.currentUnitPrice
import com.kefe.app.domain.model.goalWealth
import com.kefe.app.domain.model.label
import com.kefe.app.domain.model.monthFlow
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.model.monthName
import com.kefe.app.domain.model.monthPlanProgress
import com.kefe.app.domain.model.otherGoalOf
import com.kefe.app.domain.model.parseAssetKey
import com.kefe.app.domain.model.planAssetOptions
import com.kefe.app.domain.model.planCopyDraft
import com.kefe.app.domain.model.priceKeyOfAsset
import com.kefe.app.domain.model.requiredMonthly
import com.kefe.app.domain.model.sellPrice
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.rawAmount

// Plan sekmesinin turetimi. SAF ve Compose'suz: ViewModel yalniz akislari
// toplar ve niyetleri isler, butun hesap buradadir. NEDEN: turetim ana is
// parcaciginin disinda (Dispatchers.Default) calisir ve durum okumadan yalniz
// girdiden cikmalidir; saf bir fonksiyon bunu yapisal olarak garanti eder.

/**
 * Bir turetimin butun girdisi - akislarin o anki TEK goruntusu.
 *
 * Govdedeki alanlar her emisyonda bir kez hesaplanir; baslik ve defter hep ayni
 * [PlanInputs]'tan cikar, biri digerinden bir kare geride kalamaz.
 */
internal data class PlanInputs(
    /** Kullanicinin sectigi ay; null = "bu ay" (gun donunce kendiliginden ilerler). */
    val selection: YearMonth?,
    /** YALNIZ gun akisindan (dayTicks) - clock.today() degil (bkz. PlanViewModel). */
    val today: KefeDate,
    /** Butun aylarin plan satirlari - seri ve kopyalama gecmise bakar. */
    val items: List<PlanItem>,
    val transactions: List<Transaction>,
    /** Silinmemis BUTUN pozisyonlar - satilip sifirlananlar dahil. */
    val positions: List<Position>,
    val goals: List<Goal>,
    val assignments: Map<String, GoalAssignment>,
    val members: List<Member>,
    val board: PriceBoard,
    val activeMemberId: String?,
    /** Butun aylarin defteri (gelir, gider, butce) - TEK abonelik. */
    val books: List<MonthBook>,
) {
    val held: List<Position> = positions.filter { it.quantity > 0.0 }
    val current: YearMonth = YearMonth.of(today)
    val bounds: ClosedRange<YearMonth> = planMonthBounds(today, items, transactions, books)

    /**
     * Secim sinirlarin DISINA dustu: VM kabukta yasar ve "Tüm verileri sil" ya da ilk
     * islem ayinin silinmesi sinirlari daraltir (orn. secili Agustos kalir). Turetim o
     * zaman bu ayi gosterir ([month]); VM de secimi birakir, ayin verisi geri gelince
     * sayfa kendiliginden oraya atlamasin.
     */
    val selectionClamped: Boolean = selection != null && selection !in bounds

    /** Gosterilen ay: sinir icindeki secim, yoksa bu ay (bkz. [selectionClamped]). */
    val month: YearMonth = selection?.takeIf { it in bounds } ?: current
    val book: MonthBook = books.firstOrNull { it.month == month } ?: MonthBook(month)
    val previousBook: MonthBook = month.previous().let { p -> books.firstOrNull { it.month == p } ?: MonthBook(p) }
}

/**
 * Ay gecisinin sinirlari.
 *
 * Bas: gecen ay ile ilk plan, ilk islem ve ilk defter ayinin EN ESKISI. Yalniz
 * geliri/gideri/butcesi olan bir ay da (orn. plansiz ve islemsiz bir Agustos
 * harcamasi) ulasilabilir kalir. Gecen ay her zaman acik: ay bittikten sonra
 * girilen gelir ve ay sonu gider toplami, o ayda hic kayit yokken de girilebilsin.
 *
 * Son: gelecek ay - EN FAZLA bir ay ileri (onayli karar). Daha ilerisi icin
 * plan yapmak once bu ayi bitirmeyi beklemeli.
 */
internal fun planMonthBounds(
    today: KefeDate,
    items: List<PlanItem>,
    transactions: List<Transaction>,
    books: List<MonthBook>,
): ClosedRange<YearMonth> {
    val current = YearMonth.of(today)
    val start = listOfNotNull(
        current.previous(),
        items.minOfOrNull { it.month },
        transactions.minOfOrNull { YearMonth.of(it.date) },
        books.filterNot { it.isEmpty }.minOfOrNull { it.month },
    ).min()
    return start..current.next()
}

internal fun monthRelation(month: YearMonth, today: KefeDate): MonthRelation {
    val current = YearMonth.of(today)
    return when {
        month < current -> MonthRelation.Past
        month > current -> MonthRelation.Future
        else -> MonthRelation.Current
    }
}

internal fun planHeader(month: YearMonth, today: KefeDate, bounds: ClosedRange<YearMonth>): PlanHeader {
    val relation = monthRelation(month, today)
    return PlanHeader(
        month = month,
        title = month.label(),
        subtitle = headerSubtitle(month, today),
        relation = relation,
        canGoBack = month > bounds.start,
        canGoForward = month < bounds.endInclusive,
        showThisMonthChip = relation != MonthRelation.Current,
    )
}

/** VM'in cagirdigi TEK giris: bir emisyonun butun turetilmis icerigi. */
internal fun planContent(inputs: PlanInputs): PlanContent {
    val month = inputs.month
    val relation = monthRelation(month, inputs.today)
    val progress = monthPlanProgress(month, inputs.items, inputs.transactions, inputs.positions, inputs.today)
    // Kopyalama girisi YALNIZ kaynakta bu ayda henuz olmayan bir varlik varken.
    // NEDEN: ilk kopyadan sonra butun satirlar hedeftedir. Giris acik kalsaydi
    // yeniden acmak ayni eksik icin "Taşı"yi tekrar sunardi (planCopyDraft hedefte
    // olan satirda da eksigi tutar) ve toItems onu IKINCI kez eklerdi (14 -> 18).
    val copy = copyDraftOf(inputs, emptySet())?.takeIf { it.hasNewRows }
    return PlanContent(
        header = planHeader(month, inputs.today, inputs.bounds),
        investment = investmentCard(inputs, progress, relation, copy),
        emptyPlan = if (progress.items.isEmpty()) emptyPlanCard(month, copy) else null,
        extras = extrasCard(progress),
        goalContributions = goalContributionRows(inputs, progress, relation),
        flow = moneyFlowCard(inputs, progress, relation),
        expenses = expensesCard(inputs.book),
    )
}

// --- Kartlar -----------------------------------------------------------------

internal fun investmentCard(
    inputs: PlanInputs,
    progress: MonthPlanProgress,
    relation: MonthRelation,
    copy: CopyDraftUi?,
): InvestmentCard? {
    if (progress.items.isEmpty()) return null
    val future = relation == MonthRelation.Future
    val count = progress.items.size
    // Gelecek ayda "0/5" bir olgu degil: henuz hicbir sey beklenmiyor.
    val counted = if (future) "$count kalem" else "${progress.doneCount}/$count kalem"
    // Hicbir kalemin agirligi bilinmiyorsa "₺0 planlandı" yazilmaz.
    val summary = if (progress.plannedTl > 0.0) "$counted · ${Money.tl(progress.plannedTl)} planlandı" else counted
    val score = if (future) null else progress.score
    return InvestmentCard(
        scoreText = scoreText(score),
        score = score?.toFloat(),
        summary = summary,
        rows = progress.items.map { planRow(inputs, it, relation) },
        copyLabel = copy?.let { copyButtonLabel(it.source, progress.month) },
    )
}

private fun planRow(inputs: PlanInputs, p: PlanItemProgress, relation: MonthRelation): PlanRowUi {
    val item = p.item
    return PlanRowUi(
        itemId = item.id,
        assetKey = item.assetKey,
        assetClass = item.assetClass,
        // Eldeki pozisyonun adi once gelir: elde olmadan "AFA" diye planlanan bir fon,
        // alindiktan sonra kullanicinin gordugu adla ("AFA · Ak Portföy Altın") gorunsun.
        name = heldPositionOf(inputs.held, item.assetKey)?.name ?: item.assetName,
        goalName = item.goalId?.let { id -> inputs.goals.firstOrNull { it.id == id }?.name },
        progressText = progressText(p),
        ratio = p.ratio.toFloat(),
        status = p.status,
        notes = rowNotes(p, relation),
        // Ekleme sayfasinda tarih secici yok, her alim BUGUNE yazilir: baska bir ayin
        // satirindaki "Al" o aya sayilmazdi. Upcoming yalniz gelecek ayda olur.
        canBuy = relation == MonthRelation.Current && p.status != PlanItemStatus.Done,
    )
}

/** Anahtarin eldeki en buyuk (degerce) pozisyonu - ad ve "Al" icin. */
internal fun heldPositionOf(held: List<Position>, assetKey: String): Position? =
    held.filter { it.assetKey() == assetKey }.maxByOrNull { it.value }

internal fun emptyPlanCard(month: YearMonth, copy: CopyDraftUi?): EmptyPlanCard = EmptyPlanCard(
    title = "${month.monthName()} için plan yok",
    // "Bu ay" degil ayin adi: sayfa gecmis ve gelecek ayi da gosterir.
    body = "${month.monthName()} için almak istediklerinizi ekleyin; yapılanlar işlem defterinden sayılır.",
    copyLabel = copy?.let { emptyCopyLabel(it.source, month) },
)

internal fun extrasCard(progress: MonthPlanProgress): ExtrasCard? {
    if (progress.extras.isEmpty()) return null
    return ExtrasCard(
        total = Money.tl(progress.extrasTl),
        rows = progress.extras.map { e ->
            val assetClass = parseAssetKey(e.assetKey)?.assetClass
            val kind = when (e.kind) {
                ExtraKind.Unplanned -> "Planda yok"
                ExtraKind.OverPlan -> "Hedefin üstünde"
            }
            // Nakitte miktar zaten TL - sagdaki tutarla ayni rakami iki kez yazmaz.
            val quantity = e.quantity
                ?.takeIf { assetClass != AssetClass.Cash }
                ?.let { " · " + quantityLabel(it, e.assetKey) }
                .orEmpty()
            ExtraRowUi(
                assetKey = e.assetKey,
                assetClass = assetClass,
                name = e.name,
                detail = kind + quantity,
                amount = Money.tl(e.tl),
            )
        },
    )
}

/**
 * Ayin kalemlerinin baglandigi hedefler, hedef sirasiyla (silinmis hedef yok).
 *
 * "gereken" (hedef tarihine yetismek icin ayda gereken) gecmis ayda yazilmaz:
 * bugunku birikimle hesaplanir, gecmis bir ayin satirinda yanlis aya isaret eder.
 */
internal fun goalContributionRows(
    inputs: PlanInputs,
    progress: MonthPlanProgress,
    relation: MonthRelation,
): List<GoalContributionRow> {
    val planned = progress.items.filter { it.item.goalId != null }.groupBy { it.item.goalId }
    if (planned.isEmpty()) return emptyList()
    return inputs.goals.filter { it.id in planned }.map { goal ->
        val weights = planned.getValue(goal.id).mapNotNull { it.plannedTl }
        // Hicbir kalemin agirligi bilinmiyorsa "₺0" uydurulmaz.
        val plannedTl = if (weights.isEmpty()) null else weights.sum()
        val monthly = goal.monthlyContribution
        val required = if (relation == MonthRelation.Past) {
            null
        } else {
            goal.requiredMonthly(goalWealth(goal, inputs.held, inputs.assignments), inputs.today)
                ?.takeIf { it > 0.0 }
        }
        val line = buildString {
            append("planlanan ${plannedTl?.let { Money.tl(it) } ?: "—"}")
            if (monthly > 0.0) append(" / aylık katkı ${Money.tl(monthly)}")
            if (required != null) append(" · gereken ${Money.tl(required)}")
        }
        GoalContributionRow(
            goalId = goal.id,
            name = goal.name,
            iconKey = goal.iconKey,
            line = line,
            ratio = if (monthly > 0.0 && plannedTl != null) (plannedTl / monthly).coerceIn(0.0, 1.0).toFloat() else null,
        )
    }
}

// --- Para akisi ve giderler --------------------------------------------------

/**
 * "Para akışı": plan ve gerceklesen yan yana.
 *
 * monthFlow hic gider girilmemisken de gideri 0 verir; o yuzden tasarruf orani,
 * kalan ve planli kalan tek baslarina olgu sayilmaz. Gider girilmemisken
 * "Kalan = gelir - yatirim" ve "Tasarruf oranı %100" uydurma rakam olurdu; butce
 * ve plan yokken planli Kalan da gelirin kendisini "dagitilmamis" diye yazardi.
 * Gelecek ayda gerceklesen hicbir sey yoktur: sutun "—" (0 bir olgu degil).
 */
internal fun moneyFlowCard(inputs: PlanInputs, progress: MonthPlanProgress, relation: MonthRelation): MoneyFlowCard {
    val book = inputs.book
    // Plan yoksa ya da hicbir kalemin agirligi bilinmiyorsa planli yatirim "—": yatirim
    // kartinin "₺0 planlandı" yazmamasiyla ayni kural.
    val plannedInvest = progress.plannedTl.takeIf { progress.items.isNotEmpty() && it > 0.0 }
    val flow = monthFlow(book, inputs.transactions, plannedInvest)
    val future = relation == MonthRelation.Future
    val hasExpenses = book.expenses.isNotEmpty()
    val income = flow.income

    val nothingToCompare = income == null && flow.budgetTotal == null && plannedInvest == null &&
        !hasExpenses && flow.investedNet == 0.0
    val table = if (nothingToCompare) {
        null
    } else {
        listOf(
            flowRow("Gelir", income, income.takeUnless { future }),
            flowRow("Gider", flow.budgetTotal, flow.expenses.takeIf { hasExpenses && !future }),
            flowRow("Yatırım", plannedInvest, flow.investedNet.takeUnless { future }),
            flowRow(
                "Kalan",
                flow.plannedRemaining.takeIf { income != null && (flow.budgetTotal != null || plannedInvest != null) },
                flow.remaining.takeIf { hasExpenses && !future },
            ),
        )
    }

    val savingsLine = if (income != null && income > 0.0 && hasExpenses && !future) {
        val rate = (income - flow.expenses) / income
        if (rate >= 0.0) "Tasarruf oranı ${Money.ratioOf(rate)}" else "Gider gelirin ${trPercentOf(flow.expenses / income)}"
    } else {
        null
    }
    // Gelecek ayda da yazilir: ikisi de plan.
    val planShareLine = if (plannedInvest != null && income != null && income > 0.0) {
        "Plan gelirin ${trPercentOf(plannedInvest / income)}"
    } else {
        null
    }

    return MoneyFlowCard(
        table = table,
        savingsLine = savingsLine,
        planShareLine = planShareLine,
        salesLine = flow.sells.takeIf { it > 0.0 }?.let { "Satışlar ${Money.tl(it)}" },
        incomeRows = inputs.members.mapIndexed { index, member ->
            IncomeRowUi(
                memberId = member.id,
                name = member.name,
                initials = member.initials,
                index = index,
                amount = flow.incomeByMember[member.id]?.let { Money.tl(it) } ?: "—",
            )
        },
    )
}

private fun flowRow(label: String, planned: Double?, actual: Double?): FlowRow {
    val plannedText = planned?.let { Money.tl(it) } ?: "—"
    val actualText = actual?.let { Money.tl(it) } ?: "—"
    return FlowRow(
        label = label,
        planned = plannedText,
        actual = actualText,
        spoken = "$label: plan ${spokenAmount(plannedText)}, gerçekleşen ${spokenAmount(actualText)}",
    )
}

/** Ekran okuyucu "—"yu "tire" diye okur; bilinmeyen rakam "yok" diye soylenir. */
private fun spokenAmount(text: String): String = if (text == "—") "yok" else text

/**
 * "Giderler" - bos ayda da kurulur (harcama ve butcenin giris noktasi). "Bu ay"
 * denmez: sayfa baska aylari da gosterir.
 */
internal fun expensesCard(book: MonthBook): ExpensesCard {
    val spentBy = book.expenses.totalsByCategory()
    val budgetBy = book.budgets.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    val spent = spentBy.values.sum()
    val budget = budgetBy.takeIf { it.isNotEmpty() }?.values?.sum()

    val categories = ExpenseCategory.entries.mapNotNull { category ->
        val categorySpent = spentBy[category] ?: 0.0
        val categoryBudget = budgetBy[category]
        if (categorySpent == 0.0 && categoryBudget == null) return@mapNotNull null
        CategoryRowUi(
            category = category,
            label = category.label(),
            amounts = categoryBudget?.let { "${Money.tl(categorySpent)} / ${Money.tl(it)}" } ?: Money.tl(categorySpent),
            ratio = categoryBudget?.let { spentRatio(categorySpent, it) },
            overText = overText(categorySpent, categoryBudget),
        )
    }

    return ExpensesCard(
        totalLine = when {
            budget != null -> "${Money.tl(spent)} / ${Money.tl(budget)}"
            book.expenses.isNotEmpty() -> Money.tl(spent)
            else -> "Harcama girilmedi."
        },
        totalRatio = budget?.let { spentRatio(spent, it) },
        totalOverText = overText(spent, budget),
        categories = categories,
        recent = book.expenses
            .sortedWith(NewestFirst)
            .take(RecentExpenseCount)
            .map { e ->
                val note = e.note?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                ExpenseRowUi(
                    id = e.id,
                    title = e.category.label(),
                    subtitle = "${e.date.day} ${e.date.monthLabel()}$note",
                    amount = Money.tl(e.amount),
                )
            },
    )
}

private fun List<ExpenseEntry>.totalsByCategory(): Map<ExpenseCategory, Double> =
    groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }

private fun spentRatio(spent: Double, budget: Double): Float? =
    if (budget > 0.0) (spent / budget).coerceIn(0.0, 1.0).toFloat() else null

/** "₺2.300 aşıldı" - yalniz butce varken ve asildiysa. */
private fun overText(spent: Double, budget: Double?): String? =
    if (budget != null && spent > budget) "${Money.tl(spent - budget)} aşıldı" else null

/** En yeni once: tarih, ayni gunde giris ani. */
private val NewestFirst: Comparator<ExpenseEntry> =
    compareByDescending<ExpenseEntry> { it.date.year }
        .thenByDescending { it.date.month }
        .thenByDescending { it.date.day }
        .thenByDescending { it.createdAt }

/** "Son girişler"de gosterilen en fazla giris. */
private const val RecentExpenseCount = 5

// --- Defter editorleri -------------------------------------------------------

/** Uyenin gosterilen aydaki geliri; uye yoksa (silinmis) null. */
internal fun incomeEditorOf(inputs: PlanInputs, memberId: String): IncomeEditor? {
    val member = inputs.members.firstOrNull { it.id == memberId } ?: return null
    return IncomeEditor(
        month = inputs.month,
        memberId = member.id,
        memberName = member.name,
        salaryText = rawAmount(inputs.book.incomeOf(memberId, IncomeKind.Salary) ?: 0.0),
        extraText = rawAmount(inputs.book.incomeOf(memberId, IncomeKind.Extra) ?: 0.0),
        lastSalary = inputs.previousBook.incomeOf(memberId, IncomeKind.Salary),
        lastExtra = inputs.previousBook.incomeOf(memberId, IncomeKind.Extra),
    )
}

/** Uyenin o turdeki geliri; girilmemisse null. */
private fun MonthBook.incomeOf(memberId: String, kind: IncomeKind): Double? =
    incomes.filter { it.memberId == memberId && it.kind == kind }
        .takeIf { it.isNotEmpty() }
        ?.sumOf { it.amount }
        ?.takeIf { it > 0.0 }

/**
 * Yeni harcama. Kimlik BURADA, editor acilirken gelir ([id]). Tarih: bu ayda
 * bugun, baska ayda o ayin son gunu (onayli kural). Ekleyen: bu cihazin profili,
 * yoksa ilk uye (ekleme sayfasiyla ayni kural).
 */
internal fun newExpenseEditor(inputs: PlanInputs, id: String): ExpenseEditor = ExpenseEditor(
    month = inputs.month,
    id = id,
    isNew = true,
    category = null,
    amountText = "",
    note = "",
    date = if (inputs.month == inputs.current) inputs.today else inputs.month.lastDay(),
    addedByMemberId = inputs.activeMemberId ?: inputs.members.firstOrNull()?.id,
)

/** Kayitli harcama: kimligi, tarihi ve giris ani korunur (sira degismez). */
internal fun expenseEditorOf(entry: ExpenseEntry): ExpenseEditor = ExpenseEditor(
    month = entry.month,
    id = entry.id,
    isNew = false,
    category = entry.category,
    amountText = rawAmount(entry.amount),
    note = entry.note.orEmpty(),
    date = entry.date,
    createdAt = entry.createdAt,
    addedByMemberId = entry.addedByMemberId,
)

internal fun budgetEditorOf(inputs: PlanInputs): BudgetEditor = BudgetEditor(
    month = inputs.month,
    texts = inputs.book.budgets.groupBy { it.category }
        .mapValues { (_, list) -> rawAmount(list.sumOf { it.amount }) },
    lastSpent = inputs.previousBook.expenses.totalsByCategory(),
    lastBudgets = inputs.previousBook.budgets.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } },
    income = monthFlow(inputs.book, emptyList(), plannedInvest = null).income,
)

// --- Fiyat -------------------------------------------------------------------

/**
 * Kaydetme anindaki ALIS birim fiyati: kuyumcunun SATIS kotasyonu (Price.buyPrice).
 * Has/Kulce iki yonde alis kotasyonuyla islem gorur (ekleme sayfasiyla ayni kural);
 * nakit 1; tabloda yoksa eldeki pozisyonun guncel fiyati. Hicbiri yoksa null -
 * fiyat uydurulmaz, plan agirligi alan kurallarina birakilir.
 */
internal fun buyPriceOf(key: String, board: PriceBoard, positions: List<Position>): Double? {
    val info = parseAssetKey(key)
    if (info?.assetClass == AssetClass.Cash) return 1.0
    val quote = priceKeyOfAsset(key)?.let { board.byKey(it) }?.let { price ->
        if (info?.goldSubtype == GoldSubtype.Bullion) price.sellPrice() else price.buyPrice()
    }
    return quote?.takeIf { it > 0.0 } ?: currentUnitPrice(key, positions)
}

// --- Kalem editoru -----------------------------------------------------------

/**
 * Editorun varlik secenekleri: eldekiler + katalog, ARTI bu ay planli olup
 * listede olmayan her anahtar (elde olmayan fon/hisse, 18/14 ayar gram, 22 disi
 * ayarda taki, satilip sifirlanan varlik).
 *
 * NEDEN: planAssetOptions bunlarin cipini vermez. Olmasa (a) boyle bir kalem
 * duzenlemeye acildiginda hicbir cip secili gorunmezdi ve (b) o kaleme ancak
 * kodunu yeniden yazarak ulasilabilirdi. Boylece ayin her kaleminin cipi vardir;
 * ayni varligi secmek o kaleme gecer (bkz. ItemSelectAsset).
 */
internal fun planItemOptions(inputs: PlanInputs, month: YearMonth): List<PlanAssetOption> {
    val base = planAssetOptions(inputs.positions)
    val known = base.mapTo(mutableSetOf()) { it.assetKey }
    val planned = inputs.items
        .filter { it.month == month && it.assetKey !in known }
        .distinctBy { it.assetKey }
        .mapNotNull { item ->
            parseAssetKey(item.assetKey)?.let {
                PlanAssetOption(item.assetKey, item.assetName, it.assetClass, it.unit, held = false)
            }
        }
    return base + planned
}

internal fun newItemEditor(inputs: PlanInputs): PlanItemEditor =
    PlanItemEditor(month = inputs.month).withContext(inputs)

/** Kayitli kalemin editoru. Kod alani acilmaz: kalemin cipi her zaman vardir (planItemOptions). */
internal fun itemEditorOf(inputs: PlanInputs, item: PlanItem): PlanItemEditor = PlanItemEditor(
    month = item.month,
    editingId = item.id,
    assetKey = item.assetKey,
    mode = item.mode,
    targetText = rawAmount(item.target),
    goalId = item.goalId,
).withContext(inputs)

/**
 * Editorun BAGLAMINI tazeler: secenekler, hedef cipleri, guncel fiyat ve cakisma.
 * Kullanicinin yazdigi hicbir alana dokunmaz - tek istisna silinmis bir hedef:
 * secim "Hedefsiz"e duser, olmayan bir hedefin kimligi geri yazilmasin.
 */
internal fun PlanItemEditor.withContext(inputs: PlanInputs): PlanItemEditor {
    val goal = goalId?.let { id -> inputs.goals.firstOrNull { it.id == id } }
    val key = resolvedKey()
    // Atama tekildir ve plan onu DEGISTIRMEZ; yine de varligin baska bir hedefte
    // durdugunu bilmeden plani o hedefe baglamak yaniltir - uyari gosterilir.
    val conflict = if (goal == null || key == null) {
        null
    } else {
        inputs.held
            .filter { it.assetKey() == key }
            .firstNotNullOfOrNull { otherGoalOf(it.id, goal, inputs.assignments) }
            ?.let { other -> inputs.goals.firstOrNull { it.id == other }?.name }
    }
    return copy(
        goalId = goal?.id,
        options = planItemOptions(inputs, month),
        // Tamamlanmis hedef listede yok - ama bu kalemin hedefiyse sessizce dusmesin.
        goalChips = inputs.goals
            .filter { it.status != GoalStatus.Completed || it.id == goal?.id }
            .map { GoalChipUi(it.id, it.name) },
        unitPrice = key?.let { buyPriceOf(it, inputs.board, inputs.positions) },
        conflictGoalName = conflict,
    )
}

/**
 * Artir/azalt adimi: tutarda 500 TL; miktarda birimin dogal adimi (1 gr, 1 adet,
 * 10 pay, 100 birim doviz).
 */
internal fun planTargetStep(mode: PlanTargetMode, unit: QuantityUnit?): Double = when (mode) {
    PlanTargetMode.Amount -> 500.0
    PlanTargetMode.Quantity -> when (unit) {
        QuantityUnit.Share -> 10.0
        QuantityUnit.Currency -> 100.0
        else -> 1.0
    }
}

// --- Kopyala/Devir -----------------------------------------------------------

/**
 * Gosterilen aya kopyalama taslagi; kaynak (bu aydan onceki, satiri olan en yakin
 * ay) yoksa null. Sayfanin girisi de ayni taslaga bakar (bkz. planContent).
 */
internal fun copyDraftOf(inputs: PlanInputs, carry: Set<String>): CopyDraftUi? {
    val target = inputs.month
    val source = copySourceFor(target, inputs.items) ?: return null
    val sourceProgress = monthPlanProgress(source, inputs.items, inputs.transactions, inputs.positions, inputs.today)
    val rows = planCopyDraft(sourceProgress, target, inputs.items, inputs.today)
    val keys = rows.mapTo(mutableSetOf()) { it.assetKey }
    return CopyDraftUi(source = source, target = target, rows = rows, carry = carry.filterTo(mutableSetOf()) { it in keys })
}

/**
 * Acik sheet'in BAGLAMINI yeni girdiyle tazeler. Yalniz sheet'in ayi gosterilen
 * aysa: gun donunce sayfa yeni aya gecebilir, sheet ise acildigi ayda kalir ve
 * oraya yazar. Kullanicinin sectigi/yazdigi hicbir sey degismez.
 */
internal fun PlanSheet.refreshed(inputs: PlanInputs): PlanSheet = when (this) {
    is PlanSheet.Item ->
        if (editor.month == inputs.month) PlanSheet.Item(editor.withContext(inputs)) else this

    is PlanSheet.Copy ->
        if (draft.target == inputs.month) {
            // Kaynak ay bu arada bosaldiysa yazilacak bir sey kalmaz ("Kopyalanacak kalem yok").
            PlanSheet.Copy(copyDraftOf(inputs, draft.carry) ?: draft.copy(rows = emptyList(), carry = emptySet()))
        } else {
            this
        }

    // Yalniz ipuclari (gecen ay) tazelenir; yazilan tutarlar yerinde kalir.
    is PlanSheet.Income ->
        if (editor.month == inputs.month) {
            PlanSheet.Income(
                editor.copy(
                    lastSalary = inputs.previousBook.incomeOf(editor.memberId, IncomeKind.Salary),
                    lastExtra = inputs.previousBook.incomeOf(editor.memberId, IncomeKind.Extra),
                ),
            )
        } else {
            this
        }

    is PlanSheet.Budget ->
        if (editor.month == inputs.month) {
            val fresh = budgetEditorOf(inputs)
            PlanSheet.Budget(editor.copy(lastSpent = fresh.lastSpent, lastBudgets = fresh.lastBudgets, income = fresh.income))
        } else {
            this
        }

    // Harcamanin baglami yok: kategori, tutar, not ve tarih kullanicinin.
    is PlanSheet.Expense -> this
}

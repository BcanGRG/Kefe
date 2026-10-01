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
import com.kefe.app.domain.model.MonthFlow
import com.kefe.app.domain.model.MonthPlanProgress
import com.kefe.app.domain.model.PlanAssetOption
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanItemProgress
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.PlanStreak
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
import com.kefe.app.domain.model.planStreak
import com.kefe.app.domain.model.priceKeyOfAsset
import com.kefe.app.domain.model.requiredMonthly
import com.kefe.app.domain.model.sellPrice
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.rawAmount
import kotlin.math.round

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
    // Seri gosterilen aydan bagimsiz, bugune gore sayilir (ilk plandan bu aya).
    val streak = planStreak(inputs.items, inputs.transactions, inputs.positions, inputs.today)
    // Rozet BU AYIN acik kalemlerini sayar; bu ay gosteriliyorsa ayni ilerleme kullanilir.
    val currentProgress = if (month == inputs.current) {
        progress
    } else {
        monthPlanProgress(inputs.current, inputs.items, inputs.transactions, inputs.positions, inputs.today)
    }
    return PlanContent(
        header = planHeader(month, inputs.today, inputs.bounds),
        investment = investmentCard(inputs, progress, relation, copy)
            ?.copy(streakText = streakBadgeText(streak, relation)),
        emptyPlan = if (progress.items.isEmpty()) emptyPlanCard(month, copy) else null,
        extras = extrasCard(progress),
        goalContributions = goalContributionRows(inputs, progress, relation),
        flow = moneyFlowCard(inputs, progress, relation),
        expenses = expensesCard(inputs.book),
        streak = streak?.let(::streakCard),
        currentMonthOpenCount = currentProgress.items.count { !it.isDone },
        currentMonth = currentMonthPlan(inputs, currentProgress),
    )
}

/**
 * Bu ayin plani, Plan sekmesinin kartiyla AYNI metinlerle (skor, "3/5 kalem",
 * satirlar): Ozet'te baska, Plan'da baska bir rakam okunmasin.
 */
internal fun currentMonthPlan(inputs: PlanInputs, progress: MonthPlanProgress): CurrentMonthPlan? {
    val card = investmentCard(inputs, progress, MonthRelation.Current, copy = null) ?: return null
    val goals = progress.items
        .groupBy { it.item.goalId }
        .mapNotNull { (goalId, lines) ->
            val goal = goalId?.let { id -> inputs.goals.firstOrNull { it.id == id } } ?: return@mapNotNull null
            val weights = lines.mapNotNull { it.plannedTl }
            // Hicbir kalemin agirligi bilinmiyorsa "₺0" uydurulmaz.
            val planned = if (weights.isEmpty()) null else weights.sum()
            val summary = buildString {
                append("Planlanan ${planned?.let { Money.tlExact(it) } ?: "—"}")
                if (goal.monthlyContribution > 0.0) append(" · Aylık katkı ${Money.tlExact(goal.monthlyContribution)}")
            }
            val required = goal.requiredMonthly(goalWealth(goal, inputs.held, inputs.assignments), inputs.today)
                ?.takeIf { it > 0.0 }
                ?.let { "Gereken aylık ≈ ${Money.tlExact(it)} (${monthsToTarget(goal, inputs.today)} ay)" }
            GoalMonthPlan(
                goalId = goal.id,
                // Hedef cipi yazilmaz: kart zaten o hedefin sayfasinda.
                rows = lines.map { planRow(inputs, it, MonthRelation.Current).copy(goalName = null) },
                summary = summary,
                requiredLine = required,
            )
        }
    val count = progress.items.size
    return CurrentMonthPlan(
        title = "${progress.month.monthName()} planı",
        scoreText = card.scoreText,
        score = card.score,
        countText = "${progress.doneCount}/$count kalem",
        summary = card.summary,
        goals = goals,
    )
}

/** requiredMonthly ile AYNI ay sayimi (ay farki, en az 1). */
private fun monthsToTarget(goal: Goal, today: KefeDate): Int =
    ((goal.targetDate.year - today.year) * 12 + (goal.targetDate.month - today.month)).coerceAtLeast(1)

/** "Seri" karti; hucreler eskiden yeniye, son hucre bu ay. */
internal fun streakCard(streak: PlanStreak): StreakCard = StreakCard(
    headline = streakHeadline(streak),
    detail = streakDetail(streak),
    cells = streak.grid.mapIndexed { index, (month, cell) ->
        StreakCellUi(
            label = month.firstDay().monthLabel(),
            cell = cell,
            description = "${month.label()}: ${cell.spoken()}",
            isCurrent = index == streak.grid.lastIndex,
        )
    },
)

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
    val summary = if (progress.plannedTl > 0.0) "$counted · ${Money.tlExact(progress.plannedTl)} planlandı" else counted
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
        total = Money.tlExact(progress.extrasTl),
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
                amount = Money.tlExact(e.tl),
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
            append("planlanan ${plannedTl?.let { Money.tlExact(it) } ?: "—"}")
            if (monthly > 0.0) append(" / aylık katkı ${Money.tlExact(monthly)}")
            if (required != null) append(" · gereken ${Money.tlExact(required)}")
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
 * "Para akışı": gelir - giderler - yatirima giden = elde kalan (bkz. MoneyFlowCard).
 *
 * monthFlow hic gider girilmemisken de gideri 0 verir; o yuzden gider "—" ve elde
 * kalan tek basina olgu sayilmaz. Gider girilmemisken "gelir - yatirim" elde kalan
 * diye yazilsaydi uydurma bir rakam olurdu; butce ve plan yokken gelecek ayin
 * "kalacak"i da gelirin kendisini dagitilmamis diye yazardi. Gelecek ayda
 * gerceklesen hicbir sey yoktur: satirlar planin kendisidir.
 */
internal fun moneyFlowCard(inputs: PlanInputs, progress: MonthPlanProgress, relation: MonthRelation): MoneyFlowCard {
    val book = inputs.book
    // Plan yoksa ya da hicbir kalemin agirligi bilinmiyorsa planli yatirim "—": yatirim
    // kartinin "₺0 planlandı" yazmamasiyla ayni kural.
    val plannedInvest = progress.plannedTl.takeIf { progress.items.isNotEmpty() && it > 0.0 }
    val flow = monthFlow(book, inputs.transactions, plannedInvest)
    val incomeRows = inputs.members.mapIndexed { index, member ->
        IncomeRowUi(
            memberId = member.id,
            name = member.name,
            initials = member.initials,
            index = index,
            amount = flow.incomeByMember[member.id]?.let { Money.tlExact(it) } ?: "—",
        )
    }
    val hasExpenses = book.expenses.isNotEmpty()
    val nothingToShow = flow.income == null && flow.budgetTotal == null && plannedInvest == null &&
        !hasExpenses && flow.investedNet == 0.0 && flow.sells == 0.0
    return when {
        nothingToShow -> MoneyFlowCard(
            caption = flowCaption(relation),
            lines = null,
            emptyHint = "Gelir ve harcamaları girince ay burada hesaplanır.",
            split = null,
            planLine = null,
            incomeRows = incomeRows,
        )
        relation == MonthRelation.Future -> plannedFlowCard(flow, plannedInvest, incomeRows)
        else -> actualFlowCard(flow, plannedInvest, hasExpenses, relation, incomeRows)
    }
}

private fun flowCaption(relation: MonthRelation): String = when (relation) {
    MonthRelation.Current -> "Bu ay şimdiye kadar"
    MonthRelation.Past -> "Ay sonu"
    MonthRelation.Future -> "Plan"
}

/**
 * Bu ay ve gecmis ay. Aylik giderler AYRILAN para olarak tam dusulur, harcamalar
 * kendi kaleminin icinden yenir; aylik gideri olmayan harcama "plan dışı" olarak
 * ayrica dusulur (bkz. MonthFlow). Kalan, gelir ve en az bir gider (aylik gider ya
 * da harcama) girildiyse hesaplanir - hic gider girilmemisken "gelir - yatirim"
 * kalan diye yazilsaydi girilmemis giderler yokmus gibi okunurdu.
 */
private fun actualFlowCard(
    flow: MonthFlow,
    plannedInvest: Double?,
    hasExpenses: Boolean,
    relation: MonthRelation,
    incomeRows: List<IncomeRowUi>,
): MoneyFlowCard {
    val income = flow.income
    val anyCost = hasExpenses || flow.budgetTotal != null
    val remaining = flow.remaining.takeIf { anyCost }
    val investNote = listOfNotNull(
        plannedInvest?.let { "planlanan ${Money.tlExact(it)}" },
        // Yatirim NET: ayni ay satilan dusulur, yoksa alimlarin toplamiyla karisirdi.
        flow.sells.takeIf { it > 0.0 }?.let { "${Money.tlExact(it)} satış düşüldü" },
    ).joinToString(" · ").ifEmpty { null }
    val plannedNote = flow.budgetTotal?.let {
        listOfNotNull(
            "${flow.budgetByCategory.size} kalem",
            flow.spentInPlan.takeIf { it > 0.0 }?.let { "harcanan ${Money.tlExact(it)}" },
            flow.overPlan.takeIf { it > 0.0 }?.let { "${Money.tlExact(it)} aşım dahil" },
        ).joinToString(" · ")
    } ?: "eklenmedi"
    val unplannedNote = flow.unplannedCategories.takeIf { it.isNotEmpty() }?.let { list ->
        list.take(UnplannedNameCount).joinToString(", ") { it.label() } + if (list.size > UnplannedNameCount) ", …" else ""
    }
    val lines = listOf(
        flowLine(FlowLineKind.Income, "Gelir", income, note = if (income == null) "girilmedi" else null),
        flowLine(
            FlowLineKind.Expense,
            "Aylık giderler",
            flow.budgetTotal?.let { it + flow.overPlan },
            note = plannedNote,
        ),
        flowLine(FlowLineKind.Unplanned, "Plan dışı harcamalar", flow.unplannedSpent, note = unplannedNote),
        flowLine(FlowLineKind.Invest, "Yatırım", flow.investedNet, note = investNote),
        flowLine(
            FlowLineKind.Remaining,
            "Kalan",
            remaining,
            note = when {
                remaining != null && remaining < 0.0 -> "gelirin üstünde"
                income == null -> "gelir girilince hesaplanır"
                !anyCost -> "giderler girilince hesaplanır"
                else -> null
            },
        ),
    )
    return MoneyFlowCard(
        caption = flowCaption(relation),
        lines = lines,
        emptyHint = null,
        split = if (income != null && income > 0.0 && anyCost) flowSplit(income, flow.outgoing, flow.investedNet) else null,
        planLine = if (relation == MonthRelation.Current) planLine(remaining, flow.investedNet, plannedInvest) else null,
        incomeRows = incomeRows,
    )
}

/** Gelecek ay: gerceklesen bir sey yok, satirlar planin kendisi (butce, planlanan yatirim). */
private fun plannedFlowCard(flow: MonthFlow, plannedInvest: Double?, incomeRows: List<IncomeRowUi>): MoneyFlowCard {
    val income = flow.income
    // Butce de plan da yoksa "kalacak" gelirin kendisi olurdu - dagitilmamis para bir plan degil.
    val remaining = flow.plannedRemaining.takeIf { income != null && (flow.budgetTotal != null || plannedInvest != null) }
    val lines = listOf(
        flowLine(FlowLineKind.Income, "Gelir", income, note = if (income == null) "girilmedi" else null),
        flowLine(FlowLineKind.Expense, "Aylık giderler", flow.budgetTotal, note = if (flow.budgetTotal == null) "eklenmedi" else null),
        flowLine(FlowLineKind.Invest, "Planlanan yatırım", plannedInvest, note = if (plannedInvest == null) "plan yok" else null),
        flowLine(
            FlowLineKind.Remaining,
            "Kalacak",
            remaining,
            note = if (remaining != null && remaining < 0.0) "plan gelirin üstünde" else null,
        ),
    )
    return MoneyFlowCard(
        caption = flowCaption(MonthRelation.Future),
        lines = lines,
        emptyHint = null,
        split = null,
        planLine = null,
        incomeRows = incomeRows,
    )
}

private fun flowLine(kind: FlowLineKind, label: String, value: Double?, note: String?): FlowLine {
    val negative = kind == FlowLineKind.Remaining && value != null && value < 0.0
    val amount = when {
        value == null -> "—"
        negative -> "−${Money.tlExact(-value)}"
        else -> Money.tlExact(value)
    }
    // "—" ekran okuyucuda "tire" diye okunur; bilinmeyen rakam soylenmez, not soyler.
    val spoken = listOfNotNull(label, amount.takeIf { value != null }, note).joinToString(", ")
    return FlowLine(kind = kind, label = label, amount = amount, note = note, negative = negative, spoken = spoken)
}

/**
 * Gelirin dagilimi. Gider + yatirim geliri astiysa cubuk ikisini toplamla olcekler
 * (kalan 0) ve asimi metinle soyler; renk tek basina sinyal degil.
 */
internal fun flowSplit(income: Double, expenses: Double, investedNet: Double): FlowSplit {
    val invest = investedNet.coerceAtLeast(0.0)
    val used = expenses + invest
    val scale = if (used > income) used else income
    val left = (income - used).coerceAtLeast(0.0)
    return FlowSplit(
        expense = (expenses / scale).toFloat(),
        invest = (invest / scale).toFloat(),
        remaining = (left / scale).toFloat(),
        expenseText = sharePercent(expenses / income),
        investText = sharePercent(invest / income),
        remainingText = sharePercent(left / income),
        deficitText = (used - income).takeIf { it > 0.0 }?.let { "Gelirin ${Money.tlExact(it)} üstünde" },
    )
}

/**
 * Pay yuzdesi: 0'dan buyuk bir pay "%0" yazilmaz ("%1'den az" - "%<1"); %100'e
 * yuvarlanan eksik pay "%100" yazilmaz (skorla ayni kural).
 */
private fun sharePercent(fraction: Double): String {
    val percent = round(fraction * 100.0)
    return when {
        fraction > 0.0 && percent < 1.0 -> "%<1"
        fraction < 1.0 && percent >= 100.0 -> Money.ratio(99.0)
        else -> Money.ratio(percent)
    }
}

/**
 * Bu ayin yatirim plani tek cumlede: "Yatırım planı tamamlanınca ₺10.851 kalır."
 * Kalan, planin henuz yapilmamis kismi da dusulerek bulunur. Kalan bilinmiyorsa
 * (gelir ya da gider girilmedi) ya da yatirim plani yoksa null.
 */
internal fun planLine(remaining: Double?, investedNet: Double, plannedInvest: Double?): String? {
    val planned = plannedInvest ?: return null
    val now = remaining ?: return null
    val left = now - (planned - investedNet.coerceAtLeast(0.0)).coerceAtLeast(0.0)
    return if (left >= 0.0) {
        "Yatırım planı tamamlanınca ${Money.tlExact(left)} kalır."
    } else {
        "Yatırım planı tamamlanırsa gelir ${Money.tlExact(-left)} aşılır."
    }
}

/**
 * "Aylık giderler" ve "Harcamalar" kartlari (bkz. ExpensesCard) - bos ayda da
 * kurulur: aylik gider ve harcamanin giris noktasi. "Bu ay" denmez: sayfa baska
 * aylari da gosterir.
 */
internal fun expensesCard(book: MonthBook): ExpensesCard {
    val flow = monthFlow(book, emptyList(), plannedInvest = null)
    val spentBy = flow.expensesByCategory
    val budgetBy = flow.budgetByCategory

    // Aylik giderler ayrilana gore - en buyuk kalem ustte.
    val categories = budgetBy.entries.sortedByDescending { it.value }.map { (category, planned) ->
        val spent = spentBy[category] ?: 0.0
        CategoryRowUi(
            category = category,
            label = category.label(),
            // Harcama yokken "₺0 / ₺25.000" yazilmaz: kira bir sinir degil, ayrilan para.
            amounts = if (spent > 0.0) "${Money.tlExact(spent)} / ${Money.tlExact(planned)}" else Money.tlExact(planned),
            ratio = if (spent > 0.0) spentRatio(spent, planned) else null,
            overText = overText(spent, planned),
        )
    }
    val plannedLine = flow.budgetTotal?.let {
        buildString {
            append("${categories.size} kalem")
            if (flow.spentInPlan > 0.0) append(" · harcanan ${Money.tlExact(flow.spentInPlan)}")
            if (flow.overPlan > 0.0) append(" · ${Money.tlExact(flow.overPlan)} aşıldı")
        }
    }

    return ExpensesCard(
        plannedTotal = flow.budgetTotal?.let { Money.tlExact(it) },
        plannedLine = plannedLine,
        categories = categories,
        totalLine = if (book.expenses.isEmpty()) "Harcama girilmedi." else Money.tlExact(flow.expenses),
        unplannedLine = flow.unplannedSpent.takeIf { it > 0.0 }?.let { "Plan dışı ${Money.tlExact(it)}" },
        recent = book.expenses
            .sortedWith(NewestFirst)
            .take(RecentExpenseCount)
            .map { e ->
                val note = e.note?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                ExpenseRowUi(
                    id = e.id,
                    title = e.category.label(),
                    subtitle = "${e.date.day} ${e.date.monthLabel()}$note",
                    amount = Money.tlExact(e.amount),
                    unplanned = e.category !in budgetBy,
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
    if (budget != null && spent > budget) "${Money.tlExact(spent - budget)} aşıldı" else null

/** En yeni once: tarih, ayni gunde giris ani. */
private val NewestFirst: Comparator<ExpenseEntry> =
    compareByDescending<ExpenseEntry> { it.date.year }
        .thenByDescending { it.date.month }
        .thenByDescending { it.date.day }
        .thenByDescending { it.createdAt }

/** "Son girişler"de gosterilen en fazla giris. */
private const val RecentExpenseCount = 10

/** Plan disi satirinin notunda adi gecen kalem sayisi. */
private const val UnplannedNameCount = 3

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
    customCategories = customCategoriesOf(inputs.books),
    plannedCategories = plannedCategoriesOf(inputs.book),
)

/** Ayin aylik gideri olan kalemler, ayrilana gore - harcama ciplerinin basi. */
internal fun plannedCategoriesOf(book: MonthBook): List<ExpenseCategory> =
    book.budgets.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }.map { it.key }

/** Kayitli harcama: kimligi, tarihi ve giris ani korunur (sira degismez). */
internal fun expenseEditorOf(entry: ExpenseEntry, books: List<MonthBook>): ExpenseEditor = ExpenseEditor(
    month = entry.month,
    id = entry.id,
    isNew = false,
    category = entry.category,
    amountText = rawAmount(entry.amount),
    note = entry.note.orEmpty(),
    date = entry.date,
    createdAt = entry.createdAt,
    addedByMemberId = entry.addedByMemberId,
    customCategories = customCategoriesOf(books),
    plannedCategories = books.firstOrNull { it.month == entry.month }?.let(::plannedCategoriesOf).orEmpty(),
)

/**
 * Daha once kullanilan ozel kalemler, EN YENI once: harcamalar tarihe, butceler
 * aya gore. Her ay farkli seylere para veren hane gecen ayin kalemini tek
 * dokunusla bulsun; yazim farki ("Tatil"/"tatil") tek cip olur (bkz. ExpenseCategory).
 */
internal fun customCategoriesOf(books: List<MonthBook>): List<ExpenseCategory> {
    val used = books.flatMap { book ->
        book.expenses.map { it.category to it.date.toEpochDay() } +
            book.budgets.map { it.category to book.month.lastDay().toEpochDay() }
    }
    return used.filter { it.first.isCustom }.sortedByDescending { it.second }.map { it.first }.distinct()
}

internal fun budgetEditorOf(inputs: PlanInputs): BudgetEditor {
    val categories = ExpenseCategory.entries + customCategoriesOf(listOf(inputs.book, inputs.previousBook))
    return budgetEditorOf(inputs, categories)
}

private fun budgetEditorOf(inputs: PlanInputs, categories: List<ExpenseCategory>): BudgetEditor = BudgetEditor(
    month = inputs.month,
    categories = categories,
    olderCustom = customCategoriesOf(inputs.books) - categories.toSet(),
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

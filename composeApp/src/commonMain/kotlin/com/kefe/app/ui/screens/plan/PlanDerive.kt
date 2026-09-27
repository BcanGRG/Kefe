package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.label
import com.kefe.app.domain.repository.PriceBoard

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
internal fun planContent(inputs: PlanInputs): PlanContent =
    PlanContent(header = planHeader(inputs.month, inputs.today, inputs.bounds))

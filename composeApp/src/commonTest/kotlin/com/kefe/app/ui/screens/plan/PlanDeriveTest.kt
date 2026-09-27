package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.ExpenseBudget
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.IncomeEntry
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.planItemId
import com.kefe.app.domain.model.toItems
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan sekmesinin kartlari ve editor baglami - saf turetim.
 *
 * Sabitlenen: ozet satiri yapilan/toplam kalemi ve bilinen TL agirligini yazar;
 * bos kart cizilmez (plan disi alim yoksa kart yok); "gereken" gecmis ayda
 * yazilmaz; kopyalama girisi YALNIZ kaynakta bu ayda olmayan bir varlik varken
 * acilir (ikinci kopya ayni eksigi bir daha tasirdi); elde olmadan planlanan fon,
 * alininca eldeki adiyla gorunur; ayin her kaleminin editorde bir cipi vardir.
 *
 * Para akisi: hic girilmemis rakam "—" kalir, 0 degil - gider girilmemis ay
 * "Tasarruf oranı %100" demez, gelecek ayda gerceklesen sutunu bostur; butce ve
 * plan yokken planli Kalan yazilmaz. Giderler: bos ay "Harcama girilmedi." der,
 * harcamasi ve butcesi olmayan kategori listelenmez, son girisler en yeni 5.
 */
class PlanDeriveTest {

    private val today = KefeDate(2026, 10, 22)
    private val oct = YearMonth(2026, 10)
    private val sep = YearMonth(2026, 9)
    private val nov = YearMonth(2026, 11)

    // --- Yatirim plani -------------------------------------------------------

    @Test
    fun `ozet satiri yapilan kalemi ve planlanan tutari yazar`() {
        val content = planContent(fiveItemMonth())
        val card = assertNotNull(content.investment)
        assertEquals("3/5 kalem · ₺70.330 planlandı", card.summary)
        assertNull(content.emptyPlan)
        // Butun alimlar planli ve hedefin ustunde degil: plan disi kart yok.
        assertNull(content.extras)
        assertEquals(5, card.rows.size)
        assertEquals("10 / 10 gr", card.rows.first { it.assetKey == "gold_gram" }.progressText)
    }

    @Test
    fun `bu ayda yalniz tamamlanmamis kalemde Al var`() {
        val rows = assertNotNull(planContent(fiveItemMonth()).investment).rows
        assertFalse(rows.first { it.assetKey == "gold_gram" }.canBuy)
        assertEquals(PlanItemStatus.Done, rows.first { it.assetKey == "gold_gram" }.status)
        val usd = rows.first { it.assetKey == "usd_try" }
        assertEquals(PlanItemStatus.Waiting, usd.status)
        assertTrue(usd.canBuy)
    }

    @Test
    fun `gecmis ayda Al yok, kacan kalem Kacti der`() {
        val content = planContent(inputs(selection = sep, items = listOf(item(sep, "gold_gram", 10.0))))
        val row = assertNotNull(content.investment).rows.single()
        assertEquals(PlanItemStatus.Missed, row.status)
        assertFalse(row.canBuy)
    }

    @Test
    fun `gelecek ayda skor yok, kalemler yakinda ve Al yok`() {
        val content = planContent(
            inputs(selection = nov, items = listOf(item(nov, "gold_gram", 10.0, price = 6_733.0), item(nov, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount))),
        )
        val card = assertNotNull(content.investment)
        assertEquals("—", card.scoreText)
        assertNull(card.score)
        // "0/2" bir olgu degil: gelecek ayda henuz hicbir sey beklenmiyor.
        assertEquals("2 kalem · ₺68.330 planlandı", card.summary)
        assertTrue(card.rows.all { it.status == PlanItemStatus.Upcoming && !it.canBuy })
    }

    @Test
    fun `agirligi bilinmeyen plan tutar yazmaz`() {
        val content = planContent(inputs(items = listOf(item(oct, "silver_gram", 10.0))))
        assertEquals("0/1 kalem", assertNotNull(content.investment).summary)
    }

    @Test
    fun `elde olmadan AFA diye planlanan fon eldeki adiyla gorunur`() {
        val planned = item(oct, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount, name = "AFA")
        val unheld = planContent(inputs(items = listOf(planned)))
        assertEquals("AFA", assertNotNull(unheld.investment).rows.single().name)

        val held = planContent(inputs(items = listOf(planned), positions = listOf(afa(quantity = 100.0))))
        assertEquals("AFA · Ak Portföy Altın", assertNotNull(held.investment).rows.single().name)
    }

    // --- Plan disi alimlar ---------------------------------------------------

    @Test
    fun `plan disi alim ve hedefin ustu listelenir`() {
        val inputs = inputs(
            items = listOf(item(oct, "gold_gram", 2.0, price = 6_700.0)),
            positions = listOf(gram(quantity = 3.0), usd(quantity = 100.0)),
            transactions = listOf(
                buy("tx_gram", GramId, 3.0, 6_700.0, KefeDate(2026, 10, 5)),
                buy("tx_usd", UsdId, 100.0, 40.0, KefeDate(2026, 10, 6)),
            ),
        )
        val extras = assertNotNull(planContent(inputs).extras)
        assertEquals("₺10.700", extras.total)
        val usd = extras.rows.first { it.assetKey == "usd_try" }
        assertEquals("Planda yok · 100 $", usd.detail)
        assertEquals("₺4.000", usd.amount)
        val over = extras.rows.first { it.assetKey == "gold_gram" }
        assertEquals("Hedefin üstünde · 1 gr", over.detail)
        assertEquals("₺6.700", over.amount)
    }

    // --- Hedeflere katki -----------------------------------------------------

    @Test
    fun `hedef satiri planlanan, aylik katki ve gereken`() {
        val inputs = inputs(
            items = listOf(item(oct, "gold_gram", 10.0, price = 6_733.0, goalId = "g_car")),
            positions = listOf(gram(quantity = 10.0)),
            goals = listOf(car()),
            assignments = mapOf(GramId to GoalAssignment("g_car")),
        )
        val row = planContent(inputs).goalContributions.single()
        // Birikim 67.000 (10 gr x 6.700); 12 ayda 500.000'e: (500.000 - 67.000) / 12.
        assertEquals("planlanan ₺67.330 / aylık katkı ₺50.000 · gereken ₺36.083", row.line)
        assertEquals(1f, row.ratio)
        assertEquals("Araba", row.name)
    }

    @Test
    fun `bu ayin plani secili aydan bagimsiz, hedefe gore gruplanir`() {
        val inputs = inputs(
            // Sekme gecen aya bakiyor; Ozet ve hedef detayi yine BU AYI gorur.
            selection = sep,
            items = listOf(
                item(oct, "gold_gram", 10.0, price = 6_733.0, goalId = "g_car"),
                item(oct, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount),
                item(sep, "gold_gram", 5.0, price = 6_000.0),
            ),
            positions = listOf(gram(quantity = 10.0)),
            goals = listOf(car()),
            assignments = mapOf(GramId to GoalAssignment("g_car")),
        )
        val month = assertNotNull(planContent(inputs).currentMonth)
        assertEquals("Ekim planı", month.title)
        assertEquals("0/2 kalem", month.countText)
        assertTrue(month.summary.startsWith("0/2 kalem · "))

        val goal = month.goals.single()
        assertEquals("g_car", goal.goalId)
        assertEquals(listOf("gold_gram"), goal.rows.map { it.assetKey })
        assertTrue(goal.rows.single().canBuy)
        assertNull(goal.rows.single().goalName)
        assertEquals("Planlanan ₺67.330 · Aylık katkı ₺50.000", goal.summary)
        // requiredMonthly ile ayni ay sayimi: Ekim 2026 -> Ekim 2027 = 12 ay.
        assertEquals("Gereken aylık ≈ ₺36.083 (12 ay)", goal.requiredLine)
    }

    @Test
    fun `bu ayin plani yoksa ozet satiri yok`() {
        val inputs = inputs(items = listOf(item(sep, "gold_gram", 5.0, price = 6_000.0)), selection = sep)
        assertNull(planContent(inputs).currentMonth)
    }

    @Test
    fun `gecmis ayda gereken yazilmaz`() {
        val inputs = inputs(
            selection = sep,
            items = listOf(item(sep, "gold_gram", 5.0, price = 6_000.0, goalId = "g_car")),
            positions = listOf(gram(quantity = 10.0)),
            goals = listOf(car()),
            assignments = mapOf(GramId to GoalAssignment("g_car")),
        )
        val row = planContent(inputs).goalContributions.single()
        assertEquals("planlanan ₺30.000 / aylık katkı ₺50.000", row.line)
        assertEquals(0.6f, row.ratio)
    }

    @Test
    fun `hedefsiz ya da silinmis hedefli kalemde katki karti yok`() {
        val none = inputs(items = listOf(item(oct, "gold_gram", 10.0)))
        assertTrue(planContent(none).goalContributions.isEmpty())
        val deleted = inputs(items = listOf(item(oct, "gold_gram", 10.0, goalId = "g_gone")), goals = listOf(car()))
        assertTrue(planContent(deleted).goalContributions.isEmpty())
        assertNull(assertNotNull(planContent(deleted).investment).rows.single().goalName)
    }

    // --- Bos plan ve kopyalama -----------------------------------------------

    @Test
    fun `bos ay - ayin adiyla baslik ve gecen ayi kopyala`() {
        val content = planContent(inputs(items = listOf(item(sep, "gold_gram", 10.0))))
        assertNull(content.investment)
        val empty = assertNotNull(content.emptyPlan)
        assertEquals("Ekim için plan yok", empty.title)
        assertEquals("Ekim için almak istediklerinizi ekleyin; yapılanlar işlem defterinden sayılır.", empty.body)
        assertEquals("Geçen ayı kopyala (Eylül)", empty.copyLabel)
    }

    @Test
    fun `hic plan yoksa kopyalama da yok`() {
        val empty = assertNotNull(planContent(inputs()).emptyPlan)
        assertNull(empty.copyLabel)
    }

    @Test
    fun `kaynagin her varligi bu ayda planliysa kopyalama girisi kapanir`() {
        val sepItem = item(sep, "gold_gram", 10.0)
        val partial = planContent(inputs(items = listOf(sepItem, item(sep, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount), item(oct, "gold_gram", 14.0))))
        assertEquals("Geçen aydan kopyala", assertNotNull(partial.investment).copyLabel)

        val all = planContent(inputs(items = listOf(sepItem, item(oct, "gold_gram", 14.0))))
        assertNull(assertNotNull(all.investment).copyLabel)
    }

    @Test
    fun `devir varsayilan birakir, tasininca eksik eklenir`() {
        // Eylul 10 gr planlandi, 6 gr alindi: 4 gr eksik.
        val inputs = inputs(
            items = listOf(item(sep, "gold_gram", 10.0)),
            positions = listOf(gram(quantity = 6.0)),
            transactions = listOf(buy("tx_sep", GramId, 6.0, 6_700.0, KefeDate(2026, 9, 10))),
        )
        val draft = assertNotNull(copyDraftOf(inputs, emptySet()))
        assertEquals(sep, draft.source)
        val row = draft.rows.single()
        assertEquals(4.0, row.shortfall)
        assertTrue(draft.hasNewRows)
        assertEquals(1, draft.writableCount())
        assertEquals(10.0, draft.rows.toItems(oct, draft.carry) { null }.single().target)
        assertEquals(14.0, draft.rows.toItems(oct, setOf("gold_gram")) { null }.single().target)
        // Taslakta olmayan anahtar devir kumesine girmez.
        assertEquals(setOf("gold_gram"), assertNotNull(copyDraftOf(inputs, setOf("gold_gram", "fund_xyz"))).carry)
    }

    // --- Editor baglami ------------------------------------------------------

    @Test
    fun `secenekler ayin katalog disi kalemlerini de tasir`() {
        val inputs = inputs(
            items = listOf(
                item(oct, "gold_k18", 5.0, name = "18 ayar Gram Altın"),
                item(oct, "gold_jewelry_585", 10.0, name = "14 ayar Takı"),
                item(oct, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount, name = "AFA"),
                // Katalogda zaten var: ikinci bir cip olmaz.
                item(oct, "gold_gram", 1.0),
                // Baska ayin kalemi bu ayin editorune girmez.
                item(sep, "fund_xyz", 500.0, mode = PlanTargetMode.Amount, name = "XYZ"),
            ),
        )
        val options = planItemOptions(inputs, oct)
        val added = options.filter { it.assetKey in setOf("gold_k18", "gold_jewelry_585", "fund_afa") }
        assertEquals(3, added.size)
        assertTrue(added.none { it.held })
        assertEquals("AFA", added.first { it.assetKey == "fund_afa" }.name)
        assertEquals(AssetClass.Fund, added.first { it.assetKey == "fund_afa" }.assetClass)
        assertEquals(1, options.count { it.assetKey == "gold_gram" })
        assertTrue(options.none { it.assetKey == "fund_xyz" })
    }

    @Test
    fun `elde olmayan fon kalemi duzenlenince cipi secili acilir`() {
        val afaItem = item(oct, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount, name = "AFA")
        val editor = itemEditorOf(inputs(items = listOf(afaItem)), afaItem)
        assertEquals("fund_afa", editor.assetKey)
        assertNull(editor.codeClass)
        assertTrue(editor.options.any { it.assetKey == "fund_afa" })
        assertEquals("1000", editor.targetText)
        assertEquals(AssetClass.Fund to listOf("fund_afa"), editor.groups().first { it.first == AssetClass.Fund }.let { (c, o) -> c to o.map { it.assetKey } })
    }

    @Test
    fun `fon ve hisse grubu bos olsa da vardir`() {
        val editor = newItemEditor(inputs())
        val classes = editor.groups().map { it.first }
        assertTrue(AssetClass.Fund in classes)
        assertTrue(AssetClass.Stock in classes)
        assertTrue(editor.groups().first { it.first == AssetClass.Stock }.second.isEmpty())
    }

    @Test
    fun `varlik baska hedefteyse uyari, hedef silinmisse Hedefsiz`() {
        val home = car().copy(id = "g_home", name = "Ev")
        val inputs = inputs(
            positions = listOf(gram(quantity = 10.0)),
            goals = listOf(car(), home),
            assignments = mapOf(GramId to GoalAssignment("g_home")),
        )
        val editor = PlanItemEditor(month = oct, assetKey = "gold_gram", goalId = "g_car").withContext(inputs)
        assertEquals("Ev", editor.conflictGoalName)
        // Ayni hedefe baglamak uyarmaz.
        assertNull(editor.copy(goalId = "g_home").withContext(inputs).conflictGoalName)
        // Silinmis hedefin kimligi geri yazilmaz.
        assertNull(editor.copy(goalId = "g_gone").withContext(inputs).goalId)
    }

    @Test
    fun `tamamlanmis hedef yalniz secili ise cip olur`() {
        val done = car().copy(id = "g_done", name = "Tatil", status = GoalStatus.Completed)
        val inputs = inputs(goals = listOf(car(), done))
        assertEquals(listOf("g_car"), newItemEditor(inputs).goalChips.map { it.goalId })
        val selected = PlanItemEditor(month = oct, goalId = "g_done").withContext(inputs)
        assertEquals(listOf("g_car", "g_done"), selected.goalChips.map { it.goalId })
    }

    @Test
    fun `adim - tutarda 500, gramda 1, fonda 10 pay, dovizde 100`() {
        assertEquals(500.0, planTargetStep(PlanTargetMode.Amount, QuantityUnit.Gram))
        assertEquals(1.0, planTargetStep(PlanTargetMode.Quantity, QuantityUnit.Gram))
        assertEquals(1.0, planTargetStep(PlanTargetMode.Quantity, QuantityUnit.Piece))
        assertEquals(10.0, planTargetStep(PlanTargetMode.Quantity, QuantityUnit.Share))
        assertEquals(100.0, planTargetStep(PlanTargetMode.Quantity, QuantityUnit.Currency))
    }

    // --- Para akisi -----------------------------------------------------------

    @Test
    fun `karsilastiracak bir sey yoksa tablo yok, gelir satirlari yine var`() {
        val flow = assertNotNull(planContent(inputs(members = members)).flow)
        assertNull(flow.table)
        assertNull(flow.savingsLine)
        assertNull(flow.planShareLine)
        assertEquals(listOf("—", "—"), flow.incomeRows.map { it.amount })
        assertEquals(listOf("Burak", "Ayşe"), flow.incomeRows.map { it.name })
    }

    @Test
    fun `gider girilmemisse gider ve kalan yok, tasarruf orani uydurulmaz`() {
        val book = MonthBook(oct, incomes = listOf(income("member_owner", 85_000.0)))
        val flow = assertNotNull(planContent(inputs(members = members, books = listOf(book))).flow)
        val table = assertNotNull(flow.table).associateBy { it.label }
        assertEquals("₺85.000", table.getValue("Gelir").planned)
        assertEquals("₺85.000", table.getValue("Gelir").actual)
        assertEquals("—", table.getValue("Gider").actual)
        // Butce de plan da yok: planli Kalan gelirin kendisini "dagitilmamis" diye yazmaz.
        assertEquals("—", table.getValue("Kalan").planned)
        assertEquals("—", table.getValue("Kalan").actual)
        assertNull(flow.savingsLine)
        assertEquals("Gider: plan yok, gerçekleşen yok", table.getValue("Gider").spoken)
        assertEquals("Gelir: plan ₺85.000, gerçekleşen ₺85.000", table.getValue("Gelir").spoken)
        assertEquals(listOf("₺85.000", "—"), flow.incomeRows.map { it.amount })
    }

    @Test
    fun `tasarruf orani ve gelirden fazla gider`() {
        val saving = MonthBook(oct, incomes = listOf(income("member_owner", 100_000.0)), expenses = listOf(expense("e1", 14, 68_000.0)))
        assertEquals("Tasarruf oranı %32", planContent(inputs(books = listOf(saving))).flow?.savingsLine)

        val over = MonthBook(oct, incomes = listOf(income("member_owner", 50_000.0)), expenses = listOf(expense("e1", 14, 56_000.0)))
        assertEquals("Gider gelirin %112'si", planContent(inputs(books = listOf(over))).flow?.savingsLine)
    }

    @Test
    fun `plan gelirin payi ve planli kalan`() {
        val book = MonthBook(oct, incomes = listOf(income("member_owner", 185_000.0)))
        val flow = assertNotNull(planContent(fiveItemMonth().copy(books = listOf(book))).flow)
        assertEquals("Plan gelirin %38'i", flow.planShareLine)
        val table = assertNotNull(flow.table).associateBy { it.label }
        assertEquals("₺70.330", table.getValue("Yatırım").planned)
        assertEquals("₺114.670", table.getValue("Kalan").planned)
    }

    @Test
    fun `gelecek ayda gerceklesen sutunu bos, tasarruf orani yok`() {
        val book = MonthBook(
            nov,
            incomes = listOf(income("member_owner", 85_000.0, month = nov)),
            expenses = listOf(expense("e_kasim", 30, 2_000.0, month = nov)),
            budgets = listOf(ExpenseBudget("b1", nov, ExpenseCategory.Groceries, 10_000.0)),
        )
        val flow = assertNotNull(planContent(inputs(selection = nov, books = listOf(book))).flow)
        val table = assertNotNull(flow.table)
        assertTrue(table.all { it.actual == "—" })
        assertEquals("₺85.000", table.first { it.label == "Gelir" }.planned)
        assertNull(flow.savingsLine)
    }

    // --- Giderler ------------------------------------------------------------

    @Test
    fun `bos ayda gider karti harcama girilmedi der`() {
        val card = assertNotNull(planContent(inputs()).expenses)
        assertEquals("Harcama girilmedi.", card.totalLine)
        assertTrue(card.isEmpty, "bos durum not olarak cizilir, tutar basligi olarak degil")
        assertNull(card.totalRatio)
        assertTrue(card.categories.isEmpty())
        assertTrue(card.recent.isEmpty())
    }

    @Test
    fun `butce asimi metinle, harcamasi ve butcesi olmayan kategori yok`() {
        val book = MonthBook(
            oct,
            expenses = listOf(expense("e1", 3, 12_300.0), expense("e2", 5, 1_000.0, category = ExpenseCategory.Transport)),
            budgets = listOf(
                ExpenseBudget("b1", oct, ExpenseCategory.Groceries, 10_000.0),
                ExpenseBudget("b2", oct, ExpenseCategory.Leisure, 2_000.0),
            ),
        )
        val card = assertNotNull(planContent(inputs(books = listOf(book))).expenses)
        assertEquals("₺13.300 / ₺12.000", card.totalLine)
        assertEquals("₺1.300 aşıldı", card.totalOverText)
        assertEquals(
            listOf(ExpenseCategory.Groceries, ExpenseCategory.Transport, ExpenseCategory.Leisure),
            card.categories.map { it.category },
        )
        val groceries = card.categories.first { it.category == ExpenseCategory.Groceries }
        assertEquals("₺12.300 / ₺10.000", groceries.amounts)
        assertEquals("₺2.300 aşıldı", groceries.overText)
        assertEquals("₺1.000", card.categories.first { it.category == ExpenseCategory.Transport }.amounts)
        assertNull(card.categories.first { it.category == ExpenseCategory.Transport }.ratio)
        assertEquals("₺0 / ₺2.000", card.categories.first { it.category == ExpenseCategory.Leisure }.amounts)
    }

    @Test
    fun `son girisler en yeni bes`() {
        val book = MonthBook(
            oct,
            expenses = (1..7).map { day -> expense("e$day", day, 100.0 * day, note = if (day == 7) "market" else null) },
        )
        val recent = assertNotNull(planContent(inputs(books = listOf(book))).expenses).recent
        assertEquals(listOf("e7", "e6", "e5", "e4", "e3"), recent.map { it.id })
        assertEquals("7 Eki · market", recent.first().subtitle)
        assertEquals("Market", recent.first().title)
        assertEquals("6 Eki", recent[1].subtitle)
    }

    // --- Yardimcilar ---------------------------------------------------------

    /**
     * Ekim: 5 kalem, 3'u tamam. Agirliklar 67.330 (10 gr x 6.733) + 1.000 + 500 +
     * 1.500 (50 $ x 30); gumusun fiyati hic bilinmiyor - toplama girmez.
     */
    private fun fiveItemMonth() = inputs(
        items = listOf(
            item(oct, "gold_gram", 10.0, price = 6_733.0),
            item(oct, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount),
            item(oct, "cash", 500.0, mode = PlanTargetMode.Amount),
            item(oct, "usd_try", 50.0, price = 30.0),
            item(oct, "silver_gram", 10.0),
        ),
        positions = listOf(gram(quantity = 10.0), afa(quantity = 100.0), cash(500.0)),
        transactions = listOf(
            buy("tx_gram", GramId, 10.0, 6_700.0, KefeDate(2026, 10, 5)),
            buy("tx_afa", AfaId, 100.0, 10.0, KefeDate(2026, 10, 6)),
            buy("tx_cash", CashId, 500.0, 1.0, KefeDate(2026, 10, 7)),
        ),
    )

    private fun item(
        month: YearMonth,
        key: String,
        target: Double,
        mode: PlanTargetMode = PlanTargetMode.Quantity,
        price: Double? = null,
        goalId: String? = null,
        name: String = key,
    ) = PlanItem(
        id = planItemId(month, key),
        month = month,
        assetKey = key,
        assetName = name,
        mode = mode,
        target = target,
        goalId = goalId,
        unitPriceAtPlan = price,
    )

    private fun buy(id: String, positionId: String, quantity: Double, price: Double, date: KefeDate) = Transaction(
        id = id,
        positionId = positionId,
        date = date,
        side = TradeSide.Buy,
        quantity = quantity,
        unitPrice = price,
        addedByMemberId = "member_owner",
    )

    private fun gram(quantity: Double) = Position(
        id = GramId,
        name = "Gram Altın",
        assetClass = AssetClass.Gold,
        subtype = GoldSubtype.Gram,
        quantity = quantity,
        unit = QuantityUnit.Gram,
        unitPrice = 6_700.0,
        value = quantity * 6_700.0,
        cost = quantity * 6_700.0,
    )

    private fun afa(quantity: Double) = Position(
        id = AfaId,
        name = "AFA · Ak Portföy Altın",
        assetClass = AssetClass.Fund,
        quantity = quantity,
        unit = QuantityUnit.Share,
        unitPrice = 10.0,
        value = quantity * 10.0,
        cost = quantity * 10.0,
    )

    private fun usd(quantity: Double) = Position(
        id = UsdId,
        name = "Amerikan Doları",
        assetClass = AssetClass.Fx,
        quantity = quantity,
        unit = QuantityUnit.Currency,
        unitPrice = 40.0,
        value = quantity * 40.0,
        cost = quantity * 40.0,
    )

    private fun cash(amount: Double) = Position(
        id = CashId,
        name = "Nakit",
        assetClass = AssetClass.Cash,
        quantity = amount,
        unit = QuantityUnit.Currency,
        unitPrice = 1.0,
        value = amount,
        cost = amount,
    )

    private fun car() = Goal(
        id = "g_car",
        name = "Araba",
        iconKey = "car",
        amount = 500_000.0,
        unit = GoalUnit.Try,
        targetDate = KefeDate(2027, 10, 1),
        monthlyContribution = 50_000.0,
    )

    private val members = listOf(
        Member(id = "member_owner", name = "Burak", initials = "B"),
        Member(id = "member_partner", name = "Ayşe", initials = "A"),
    )

    private fun income(memberId: String, amount: Double, month: YearMonth = oct) = IncomeEntry(
        id = "inc_${month.month}_$memberId",
        month = month,
        memberId = memberId,
        kind = IncomeKind.Salary,
        amount = amount,
    )

    private fun expense(
        id: String,
        day: Int,
        amount: Double,
        month: YearMonth = oct,
        category: ExpenseCategory = ExpenseCategory.Groceries,
        note: String? = null,
    ) = ExpenseEntry(
        id = id,
        date = KefeDate(month.year, month.month, day),
        category = category,
        amount = amount,
        note = note,
        createdAt = day.toLong(),
    )

    private fun inputs(
        selection: YearMonth? = null,
        items: List<PlanItem> = emptyList(),
        positions: List<Position> = emptyList(),
        transactions: List<Transaction> = emptyList(),
        goals: List<Goal> = emptyList(),
        assignments: Map<String, GoalAssignment> = emptyMap(),
        members: List<Member> = emptyList(),
        books: List<MonthBook> = emptyList(),
    ) = PlanInputs(
        selection = selection,
        today = today,
        items = items,
        transactions = transactions,
        positions = positions,
        goals = goals,
        assignments = assignments,
        members = members,
        board = PriceBoard(emptyList(), "", PriceFreshness.Offline),
        activeMemberId = null,
        books = books,
    )

    private companion object {
        const val GramId = "pos_gold_gram"
        const val AfaId = "pos_fund_afa"
        const val UsdId = "pos_usd_try"
        const val CashId = "pos_cash"
    }
}

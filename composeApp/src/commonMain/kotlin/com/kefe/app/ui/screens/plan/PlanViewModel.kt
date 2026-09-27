package com.kefe.app.ui.screens.plan

import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.catalogName
import com.kefe.app.domain.model.defaultPlanMode
import com.kefe.app.domain.model.newId
import com.kefe.app.domain.model.parseAssetKey
import com.kefe.app.domain.model.planItemId
import com.kefe.app.domain.model.toItems
import com.kefe.app.domain.repository.PlanRepository
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.ui.format.parseTrAmountOrNull
import com.kefe.app.ui.format.rawAmount
import com.kefe.app.ui.mvi.MviViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Plan sekmesi. Akislari toplar ve niyetleri isler; butun hesap saf
 * turetimdedir (bkz. PlanDerive.kt).
 *
 * KABUKTA yasar (GoalsViewModel gibi): sheet'leri kabuk tek yerde cizer ve yan
 * menunun Plan rozeti de buradan beslenir. Bu yuzden surec boyunca yasar; secili
 * ay, sinirlar daralinca (ayin tek islemi silindi) sinirlarin disinda kalabilir -
 * turetim onu bu aya kirpar (bkz. PlanInputs.month) ve secim birakilir. Gecen ay
 * veri olmadan da sinir icinde kaldigi icin "Tüm verileri sil"de kabuk secimi
 * ayrica sifirlar (ThisMonth).
 *
 * "BUGUN" NEREDEN GELIR: yalniz [dayTicks]'ten. Saat ([clock]) tek bir kez, gun
 * akisini tohumlamak icin okunur. NEDEN: testler gun akisini elle ilerletir
 * (sabit saat 22 Ekim'de kalir); saatten okuyan tek bir satir gece yarisi
 * donumunde ve gun donumu testlerinde sayfayla farkli bir "bugun" gorurdu.
 *
 * YAZMA KURALI (her Kaydet / Sil / Kopyala; gelir, gider ve butce dahil): dogrulama ESZAMANLI yapilir, gecerliyse
 * sheet HEMEN kapanir ve yazma ancak ondan sonra baslar. NEDEN: sheet yazma bitince
 * kapansaydi cift dokunus ikinci kez yazardi - Kopyala'da ilk yazmanin emisyonu
 * taslagi tazeleyip "Taşı"yi korudugu icin ikinci dokunus eksigi bir daha ekler
 * (14 -> 18). Her kayit niyeti acik sheet'i okuyarak baslar; ikinci dokunus sheet
 * bulamaz ve hicbir sey yapmaz. Bedeli: yazma basarisiz olursa girilenler kaybolur
 * (serit soyler) - Hedefler ile ayni.
 */
class PlanViewModel(
    private val planRepository: PlanRepository,
    private val portfolioRepository: PortfolioRepository,
    private val priceRepository: PriceRepository,
    private val preferences: PreferencesRepository,
    clock: KefeClock,
    /** Testler sabit/elle ilerleyen bir akis verir; sonsuz delay dongusu runTest'i kilitlemesin. */
    dayTicks: Flow<KefeDate> = deviceDays(clock),
) : MviViewModel<PlanUiState, PlanIntent, PlanEffect>(PlanUiState()) {

    // SIRA ONEMLI: Kotlin ozellik ilklendiricilerini ve init bloklarini METIN
    // sirasiyla calistirir. observe() init icinde baslar ve viewModelScope'ta
    // hemen kosabilir (uygulamada Main.immediate, testte Unconfined); init'in
    // ALTINDA tanimlanan bir ozellik o anda henuz null olurdu.

    private val today: StateFlow<KefeDate> =
        dayTicks.stateIn(viewModelScope, SharingStarted.Eagerly, clock.today())

    /** null = "bu ay": gun donunce sayfa kendiliginden yeni aya gecer. */
    private val selection = MutableStateFlow<YearMonth?>(null)

    /** Son turetimin girdisi; niyetler bunu okur (null iken yuklenme suruyor, yok sayilir). */
    private var latest: PlanInputs? = null

    init {
        // Yuklenirken de baslik dogru ayi gosterir; gecis dugmeleri Ready'ye kadar kapali.
        val day = today.value
        val bounds = planMonthBounds(day, items = emptyList(), transactions = emptyList(), books = emptyList())
        setState { copy(content = PlanContent(header = planHeader(YearMonth.of(day), day, bounds))) }
        observe()
    }

    override fun onIntent(intent: PlanIntent) {
        when (intent) {
            // Girdiye bakmaz: secimi bosaltmak yuklenirken de dogrudur. Kabuk onu
            // "Tüm verileri sil"den sonra da gonderir (bkz. App.kt).
            PlanIntent.ThisMonth -> selection.value = null
            // Gecisler son turetimin ayina gore; yuklenirken (latest null) yok sayilir.
            PlanIntent.PreviousMonth -> latest?.let { inputs ->
                if (current.content.header.canGoBack) selectMonth(inputs.month.previous(), inputs)
            }
            PlanIntent.NextMonth -> latest?.let { inputs ->
                if (current.content.header.canGoForward) selectMonth(inputs.month.next(), inputs)
            }

            PlanIntent.DismissSheet -> reduce { copy(sheet = null) }

            PlanIntent.AddItem -> latest?.let { inputs ->
                reduce { copy(sheet = PlanSheet.Item(newItemEditor(inputs))) }
            }
            is PlanIntent.EditItem -> latest?.let { inputs ->
                val item = inputs.items.firstOrNull { it.id == intent.itemId } ?: return
                reduce { copy(sheet = PlanSheet.Item(itemEditorOf(inputs, item))) }
            }
            is PlanIntent.Buy -> buy(intent.itemId)
            PlanIntent.OpenCopy -> openCopy()

            is PlanIntent.ItemSelectAsset -> selectAsset(intent.assetKey)
            is PlanIntent.ItemOpenCode -> openCode(intent.assetClass)
            is PlanIntent.ItemCode -> withItemContext {
                it.copy(codeText = intent.value.trim().uppercase(), assetError = null, switchedToExisting = false)
            }
            is PlanIntent.ItemMode -> updateItem {
                // Birim degisir; sayiyi tasimak yaniltirdi ("10" gr -> "10" TL).
                if (it.mode == intent.mode || !it.showModeSwitch()) it
                else it.copy(mode = intent.mode, targetText = "", targetError = null)
            }
            is PlanIntent.ItemTarget -> updateItem { it.copy(targetText = intent.value, targetError = null) }
            is PlanIntent.ItemStep -> updateItem { editor ->
                val step = planTargetStep(editor.mode, editor.selectedUnit())
                val now = editor.targetText.parseTrAmountOrNull() ?: 0.0
                val next = (if (intent.up) now + step else now - step).coerceAtLeast(0.0)
                editor.copy(targetText = rawAmount(next), targetError = null)
            }
            is PlanIntent.ItemGoal -> withItemContext { it.copy(goalId = intent.goalId) }
            PlanIntent.SaveItem -> saveItem()
            PlanIntent.DeleteItem -> deleteItem()

            is PlanIntent.CopyCarry -> reduce {
                val open = sheet as? PlanSheet.Copy ?: return@reduce this
                val carry = if (intent.carry) open.draft.carry + intent.assetKey else open.draft.carry - intent.assetKey
                copy(sheet = PlanSheet.Copy(open.draft.copy(carry = carry)))
            }
            PlanIntent.ConfirmCopy -> confirmCopy()

            is PlanIntent.EditIncome -> latest?.let { inputs ->
                val editor = incomeEditorOf(inputs, intent.memberId) ?: return
                reduce { copy(sheet = PlanSheet.Income(editor)) }
            }
            is PlanIntent.IncomeSalary -> updateIncome { it.copy(salaryText = intent.value) }
            is PlanIntent.IncomeExtra -> updateIncome { it.copy(extraText = intent.value) }
            PlanIntent.IncomeUseLastSalary -> updateIncome { e ->
                e.lastSalary?.let { e.copy(salaryText = rawAmount(it)) } ?: e
            }
            PlanIntent.IncomeUseLastExtra -> updateIncome { e ->
                e.lastExtra?.let { e.copy(extraText = rawAmount(it)) } ?: e
            }
            PlanIntent.SaveIncome -> saveIncome()

            // Kimlik editor ACILIRKEN uretilir: cift dokunusla gelen ikinci kayit ayni
            // satiri yeniden yazar, ikinci bir satir olusturmaz.
            PlanIntent.AddExpense -> latest?.let { inputs ->
                reduce { copy(sheet = PlanSheet.Expense(newExpenseEditor(inputs, newId()))) }
            }
            is PlanIntent.EditExpense -> latest?.let { inputs ->
                val entry = inputs.books.firstNotNullOfOrNull { book -> book.expenses.firstOrNull { it.id == intent.id } }
                    ?: return
                reduce { copy(sheet = PlanSheet.Expense(expenseEditorOf(entry))) }
            }
            is PlanIntent.ExpenseSelectCategory -> updateExpense { it.copy(category = intent.category, categoryError = false) }
            is PlanIntent.ExpenseAmount -> updateExpense { it.copy(amountText = intent.value, amountError = false) }
            is PlanIntent.ExpenseNote -> updateExpense { it.copy(note = intent.value) }
            PlanIntent.SaveExpense -> saveExpense()
            PlanIntent.DeleteExpense -> deleteExpense()

            PlanIntent.EditBudget -> latest?.let { inputs ->
                reduce { copy(sheet = PlanSheet.Budget(budgetEditorOf(inputs))) }
            }
            is PlanIntent.BudgetAmount -> updateBudget { it.copy(texts = it.texts + (intent.category to intent.value)) }
            PlanIntent.BudgetCopyLastMonth -> updateBudget { e ->
                e.copy(texts = e.lastBudgets.mapValues { (_, amount) -> rawAmount(amount) })
            }
            PlanIntent.SaveBudget -> saveBudget()
        }
    }

    /**
     * Bu aya donuluyorsa secim BOSALTILIR: "bu ay" gun donunce kendiliginden
     * ilerlemeli; sabit bir Ekim secimi Kasim'da gecmis ay olarak kalirdi.
     * Sinir disi secim burada degil turetimde kirpilir (bkz. PlanInputs.month) ve
     * toplayicida birakilir.
     */
    private fun selectMonth(target: YearMonth, inputs: PlanInputs) {
        selection.value = target.takeIf { it != inputs.current }
    }

    // --- Yatirim plani -------------------------------------------------------

    /**
     * "Al": ekleme sayfasi, varligin eldeki EN BUYUK pozisyonuyla; elde yoksa varlik
     * secimiyle acilir. Miktar ve hedef onsecimi 5/5'te.
     */
    private fun buy(itemId: String) {
        val inputs = latest ?: return
        val item = inputs.items.firstOrNull { it.id == itemId } ?: return
        emitEffect(PlanEffect.OpenAddTransaction(heldPositionOf(inputs.held, item.assetKey)?.id))
    }

    /** Taslak yalniz kaynakta bu ayda olmayan bir varlik varken acilir (bkz. planContent). */
    private fun openCopy() {
        val inputs = latest ?: return
        val draft = copyDraftOf(inputs, emptySet())?.takeIf { it.hasNewRows } ?: return
        reduce { copy(sheet = PlanSheet.Copy(draft)) }
    }

    private fun confirmCopy() {
        val inputs = latest ?: return
        val draft = (current.sheet as? PlanSheet.Copy)?.draft ?: return
        // Birim fiyat kopya aninda yeniden okunur: yeni ayin agirligi bugunku alis
        // fiyatiyla sabitlenir (fiyat yoksa kaynaktaki anlik goruntu). Gecmis ayda
        // ZATEN planli kalem ise kayitli fiyatini korur - saveItem'daki kuralin aynisi:
        // eksigi devretmek o ayin TL agirligini ve skorunu bugunku fiyatla yazmamali.
        val past = draft.target < inputs.current
        val existing = inputs.items.filter { it.month == draft.target }.associateBy { it.assetKey }
        val items = draft.rows.toItems(draft.target, draft.carry) { key ->
            existing[key]?.unitPriceAtPlan?.takeIf { past } ?: buyPriceOf(key, inputs.board, inputs.positions)
        }
        if (items.isEmpty()) return
        reduce { copy(sheet = null) }
        write {
            planRepository.upsertPlanItems(items)
            emitEffect(PlanEffect.Message("${items.size} kalem kopyalandı."))
        }
    }

    // --- Kalem editoru -------------------------------------------------------

    /**
     * Cip secimi.
     *
     * 1. Secili cipe yeniden dokunmak HICBIR SEY yapmaz (hedef, birim, hedef cipi kalir).
     * 2. Varlik bu ay zaten planliysa editor O KALEME gecer (onayli kural): ayni ayda
     *    bir varlik icin tek satir vardir, ikinci bir satir birincisini ezerdi.
     * 3. Degilse varlik degisir; yazilan hedef AYNI SEYI ifade ediyorsa korunur
     *    (tutarda hep ₺; miktarda birim ayniysa - gram -> 22 ayar gram). Aksi halde
     *    sinifin varsayilan birimine gecilir ve hedef bosaltilir (gram -> ceyrek).
     */
    private fun selectAsset(key: String) {
        val inputs = latest ?: return
        val editor = (current.sheet as? PlanSheet.Item)?.editor ?: return
        if (key == editor.assetKey && editor.codeClass == null) return

        val other = inputs.items.firstOrNull {
            it.month == editor.month && it.assetKey == key && it.id != editor.editingId
        }
        if (other != null) {
            reduce { copy(sheet = PlanSheet.Item(itemEditorOf(inputs, other).copy(switchedToExisting = true))) }
            return
        }

        val newClass = parseAssetKey(key)?.assetClass
        val oldKey = editor.resolvedKey()
        val sameMeaning = editor.targetText.isNotBlank() && when (editor.mode) {
            PlanTargetMode.Amount -> true
            PlanTargetMode.Quantity -> oldKey != null && planUnitLabel(oldKey) == planUnitLabel(key)
        }
        val (mode, target) = when {
            // Nakitte miktar TL'dir: hep tutar. Tutar yazilmissa anlami ayni kalir.
            newClass == AssetClass.Cash ->
                PlanTargetMode.Amount to editor.targetText.takeIf { editor.mode == PlanTargetMode.Amount }.orEmpty()
            sameMeaning -> editor.mode to editor.targetText
            else -> (newClass?.let(::defaultPlanMode) ?: editor.mode) to ""
        }
        val next = editor.copy(
            assetKey = key,
            codeClass = null,
            codeText = "",
            mode = mode,
            targetText = target,
            switchedToExisting = false,
            assetError = null,
            targetError = null,
        ).withContext(inputs)
        reduce { copy(sheet = PlanSheet.Item(next)) }
    }

    /** "+ Fon kodu" / "+ Hisse sembolü": fon ve hisse tutarla planlanir (pay adedi anlamsiz). */
    private fun openCode(assetClass: AssetClass) = withItemContext { editor ->
        if (editor.codeClass == assetClass) {
            editor
        } else {
            editor.copy(
                codeClass = assetClass,
                assetKey = null,
                codeText = "",
                mode = PlanTargetMode.Amount,
                targetText = editor.targetText.takeIf { editor.mode == PlanTargetMode.Amount }.orEmpty(),
                switchedToExisting = false,
                assetError = null,
                targetError = null,
            )
        }
    }

    private fun saveItem() {
        val inputs = latest ?: return
        val editor = (current.sheet as? PlanSheet.Item)?.editor ?: return

        val key = editor.resolvedKey()
        val target = editor.targetText.parseTrAmountOrNull()?.takeIf { it > 0.0 }
        val pieceFraction = key != null && target != null &&
            editor.mode == PlanTargetMode.Quantity &&
            parseAssetKey(key)?.unit == QuantityUnit.Piece && target % 1.0 != 0.0
        if (key == null || target == null || pieceFraction) {
            updateItem {
                it.copy(
                    assetError = when {
                        key != null -> null
                        it.codeClass != null -> "Geçerli bir kod girin."
                        else -> "Bir varlık seçin."
                    },
                    targetError = when {
                        target == null -> "${it.targetLabel()} girin."
                        pieceFraction -> "Adetle alınan altında tam sayı girin."
                        else -> null
                    },
                )
            }
            return
        }

        // Elle yazilan ve bu ay ZATEN planli bir kod: hicbir sey yazilmaz, editor o
        // kaleme gecer. NEDEN: ayni kimlikle upsert o kalemi sessizce ezerdi, baska bir
        // kalemden replace de ustune yazardi. Kayitta bakilir, tus tus degil: "AFA"
        // yazilirken gecilen "AF" baska bir kaleme atlatmamali.
        val other = inputs.items.firstOrNull {
            it.month == editor.month && it.assetKey == key && it.id != editor.editingId
        }
        if (other != null) {
            reduce { copy(sheet = PlanSheet.Item(itemEditorOf(inputs, other).copy(switchedToExisting = true))) }
            return
        }

        val existing = inputs.items.firstOrNull { it.id == editor.editingId }
        // Fiyat anlik goruntusu: yeni kalem, degisen varlik, bu ay ya da gelecek ay ->
        // guncel ALIS fiyati (onayli kural). Gecmis aydaki AYNI varlik ise kayitli
        // fiyatini korur. NEDEN: gecmis bir kalemin yalniz hedefini ya da hedef cipini
        // degistirmek, ayin TL agirligini ve skorunu bugunku fiyatla yeniden yazardi.
        val keepSnapshot = existing != null && existing.assetKey == key &&
            editor.month < inputs.current && existing.unitPriceAtPlan != null
        val item = PlanItem(
            id = planItemId(editor.month, key),
            month = editor.month,
            assetKey = key,
            assetName = heldPositionOf(inputs.held, key)?.name
                ?: editor.options.firstOrNull { it.assetKey == key }?.name
                ?: catalogName(key),
            mode = editor.mode,
            target = target,
            goalId = editor.goalId,
            unitPriceAtPlan = if (keepSnapshot) existing?.unitPriceAtPlan else buyPriceOf(key, inputs.board, inputs.positions),
        )
        val replacing = editor.editingId?.takeIf { it != item.id }

        reduce { copy(sheet = null) }
        write {
            // Varlik degistiyse kimlik de degisti (varliktan turer): eskisi mezar
            // taslanir, yenisi yazilir - tek islemde.
            if (replacing != null) {
                planRepository.replacePlanItem(replacing, item)
            } else {
                planRepository.upsertPlanItem(item)
            }
        }
    }

    private fun deleteItem() {
        val editor = (current.sheet as? PlanSheet.Item)?.editor ?: return
        val id = editor.editingId ?: return
        reduce { copy(sheet = null) }
        write { planRepository.deletePlanItem(id) }
    }

    // --- Defter: gelir, gider, butce ----------------------------------------

    /** Bos alan "girilmedi"dir: depo satiri siler (0 yazmaz). */
    private fun saveIncome() {
        val editor = (current.sheet as? PlanSheet.Income)?.editor ?: return
        val salary = editor.salaryText.parseTrAmountOrNull()
        val extra = editor.extraText.parseTrAmountOrNull()
        reduce { copy(sheet = null) }
        write("Gelir kaydedilemedi.") {
            planRepository.setIncome(editor.month, editor.memberId, IncomeKind.Salary, salary)
            planRepository.setIncome(editor.month, editor.memberId, IncomeKind.Extra, extra)
        }
    }

    private fun saveExpense() {
        val editor = (current.sheet as? PlanSheet.Expense)?.editor ?: return
        val category = editor.category
        val amount = editor.amountText.parseTrAmountOrNull()?.takeIf { it > 0.0 }
        if (category == null || amount == null) {
            updateExpense { it.copy(categoryError = category == null, amountError = amount == null) }
            return
        }
        val entry = ExpenseEntry(
            id = editor.id,
            date = editor.date,
            category = category,
            amount = amount,
            note = editor.note.trim().takeIf { it.isNotEmpty() },
            addedByMemberId = editor.addedByMemberId,
            // 0 ise depo kayit anini damgalar; duzenlemede eski an korunur.
            createdAt = editor.createdAt,
        )
        reduce { copy(sheet = null) }
        write("Harcama kaydedilemedi.") { planRepository.upsertExpense(entry) }
    }

    private fun deleteExpense() {
        val editor = (current.sheet as? PlanSheet.Expense)?.editor ?: return
        if (editor.isNew) return
        reduce { copy(sheet = null) }
        write("Harcama kaydedilemedi.") { planRepository.deleteExpense(editor.id) }
    }

    /** Dokuz kategori tek islemde; bos ya da sifir alan o kategoriyi butceden cikarir. */
    private fun saveBudget() {
        val editor = (current.sheet as? PlanSheet.Budget)?.editor ?: return
        val amounts: Map<ExpenseCategory, Double?> =
            ExpenseCategory.entries.associateWith { editor.texts[it]?.parseTrAmountOrNull() }
        reduce { copy(sheet = null) }
        write("Bütçe kaydedilemedi.") { planRepository.setBudgets(editor.month, amounts) }
    }

    // --- Yardimci ------------------------------------------------------------

    /**
     * Durum ATOMIK indirgenir (karsilastir-yaz). NEDEN: testte ana dagitici
     * Unconfined'dir ve toplayici turetimden sonra Default is parcaciginda devam
     * eder; bir niyetle ayni anda yazabilir. Oku-yaz (setState) arasinda digerinin
     * yazdigi kaybolurdu. Uygulamada ikisi de Main'de; orada fark yok.
     */
    private fun reduce(block: PlanUiState.() -> PlanUiState) = _state.update { it.block() }

    /** Acik kalem editorunu donusturur; kalem editoru acik degilse dokunmaz. */
    private fun updateItem(block: (PlanItemEditor) -> PlanItemEditor) = reduce {
        val open = sheet as? PlanSheet.Item ?: return@reduce this
        copy(sheet = PlanSheet.Item(block(open.editor)))
    }

    private fun updateIncome(block: (IncomeEditor) -> IncomeEditor) = reduce {
        val open = sheet as? PlanSheet.Income ?: return@reduce this
        copy(sheet = PlanSheet.Income(block(open.editor)))
    }

    private fun updateExpense(block: (ExpenseEditor) -> ExpenseEditor) = reduce {
        val open = sheet as? PlanSheet.Expense ?: return@reduce this
        copy(sheet = PlanSheet.Expense(block(open.editor)))
    }

    private fun updateBudget(block: (BudgetEditor) -> BudgetEditor) = reduce {
        val open = sheet as? PlanSheet.Budget ?: return@reduce this
        copy(sheet = PlanSheet.Budget(block(open.editor)))
    }

    /** [updateItem] + baglam: secilen varlik ya da hedef degisince fiyat ve cakisma yeniden hesaplanir. */
    private fun withItemContext(block: (PlanItemEditor) -> PlanItemEditor) {
        val inputs = latest ?: return
        updateItem { block(it).withContext(inputs) }
    }

    /** Yazma arka planda; hata seritte soylenir (yerel SQLite'ta pratikte olmaz). */
    private fun write(failure: String = "Plan kaydedilemedi.", block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                emitEffect(PlanEffect.Message(failure))
            }
        }
    }

    private fun observe() {
        val ledger = combine(
            planRepository.observePlanItems(),
            portfolioRepository.observeAllTransactions(),
            portfolioRepository.observeAllPositions(),
            portfolioRepository.observeGoals(),
            portfolioRepository.observeGoalAssets(),
        ) { items, transactions, positions, goals, assignments ->
            Ledger(items, transactions, positions, goals, assignments)
        }

        val context = combine(
            portfolioRepository.observeMembers(),
            priceRepository.observePrices(),
            preferences.observeAll().map { it[PreferenceKeys.ActiveMemberId] }.distinctUntilChanged(),
        ) { members, board, active -> Context(members, board, active) }

        viewModelScope.launch {
            // Defter TEK abonelikle gelir (observeAllBooks); ay ve gun secimi yalniz yeniden
            // TURETIR. NEDEN: baslik ve defter hep ayni PlanInputs'tan cikar - aya gore ayri
            // abonelikte baslik yeni ayi, defter bir kare eski ayi gosterebilirdi.
            combine(ledger, context, planRepository.observeAllBooks(), selection, today) { l, c, books, sel, day ->
                PlanInputs(
                    selection = sel,
                    today = day,
                    items = l.items,
                    transactions = l.transactions,
                    positions = l.positions,
                    goals = l.goals,
                    assignments = l.assignments,
                    members = c.members,
                    board = c.board,
                    activeMemberId = c.activeMemberId,
                    books = books,
                )
            }
                // Turetim (butun aylarin serisi dahil) ana is parcaciginda yapilmaz: fiyat tiki hem
                // pozisyonlari hem fiyat tablosunu yeniden yayar ve iki turetim ust uste gelir.
                // conflate: yetisilemeyen ara sonuc atlanir, en yenisi cizilir.
                .map { inputs -> inputs to planContent(inputs) }
                .flowOn(Dispatchers.Default)
                .conflate()
                .collect { (inputs, content) ->
                    // Sinirin disina dusup bu aya kirpilan secim BIRAKILIR. NEDEN: kirpma
                    // yalniz turetimde kalsaydi, ayin verisi geri geldiginde (esitleme,
                    // yedekten donus) sayfa kullanici bir sey yapmadan o aya atlardi.
                    // Yalniz secim hala buysa: arada secilen yeni bir aya dokunulmaz.
                    // Durumdan ONCE: bu durumu goren her okuyucu secimi de bos gorur.
                    // Gun donumuyle bu aya donusen secim (31 Ekim'de secilen Kasim) de
                    // birakilir: bos secim "bu ay" demek, Aralik'ta kendiliginden
                    // ilerlemeli; tutulsaydi sayfa Kasim'da gecmis ay olarak kalirdi.
                    if (inputs.selectionClamped || inputs.selection == inputs.current) {
                        selection.compareAndSet(inputs.selection, null)
                    }
                    latest = inputs
                    // Durum YALNIZ burada, indirgeme icinde birlesir: o anda acik olan sheet
                    // okunur, yazilan alanlari yerinde kalir, yalniz baglami (fiyat,
                    // secenekler) tazelenir. Donusum (map) icinde durum okunmaz - Default'ta
                    // calisir ve arada acilan bir editoru ezerdi.
                    reduce { copy(stage = PlanStage.Ready, content = content, sheet = sheet?.refreshed(inputs)) }
                }
        }
    }
}

/**
 * Cihazin gunu - dakikada bir bakilir, YALNIZ gun degisince yayilir.
 *
 * SummaryViewModel.dayTicker ile ayni gerekce (gece acik kalan uygulama dunun
 * ayinda asili kalmasin), ama yalniz CIHAZ gunu: plan ayi cihaz gunune gore
 * belirlenir (bkz. monthPlanProgress) - piyasa gunu ay donumunde bir alimi iki
 * aydan da dusururdu.
 */
internal fun deviceDays(clock: KefeClock): Flow<KefeDate> = flow {
    while (true) {
        emit(clock.today())
        delay(DayCheckMillis)
    }
}.distinctUntilChanged()

/** combine() bes akisa kadar tiplenir; defter tarafi tek tasiyicida toplanir. */
private data class Ledger(
    val items: List<PlanItem>,
    val transactions: List<Transaction>,
    val positions: List<Position>,
    val goals: List<Goal>,
    val assignments: Map<String, GoalAssignment>,
)

/** Kimlik ve fiyat tarafi: uyeler, fiyat tablosu, bu cihazin profili. */
private data class Context(
    val members: List<Member>,
    val board: PriceBoard,
    val activeMemberId: String?,
)

/** Gun degisimini yakalamak icin yeterli siklik - dakikada bir. */
private const val DayCheckMillis = 60_000L

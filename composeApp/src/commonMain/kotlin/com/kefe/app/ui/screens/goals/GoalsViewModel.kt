package com.kefe.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.goalProjection
import com.kefe.app.domain.model.goalWealth
import com.kefe.app.domain.model.newId
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.plusMonths
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.rawAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Hedefler listesi. MVI-lite: tek [GoalsUiState] akisi + tek [onIntent] girisi.
 *
 * Ilerleme her hedefin KENDINE atanan varliklarindan olculur (kati atama);
 * atama yoksa %0. Hedefsiz varlik hicbir hedefe sayilmaz.
 * Fiyat deposu yalniz sayfadaki birim cevrimi icin gerekir (gram altin / USD
 * cinsinden hedef); boylece cevrim piyasa tablosuyla ayni kaynaktan beslenir.
 */
class GoalsViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val priceRepository: PriceRepository,
    private val clock: KefeClock,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    // Editor tutar cevrimi icin son bilinen kur. observePrices bunlari surekli
    // tazeler; newEditor/editorOf editor'u bunlarla tohumlar. Aksi halde editor
    // kur GELMEDEN acilirsa (fiyat emisyonu editor'den once oldu) altin/dolar
    // hedefinde 0 kurla acilir ve onizleme "₺0 · gram ₺0" gosterir.
    private var latestGoldPrice = 0.0
    private var latestUsdPrice = 0.0

    init {
        observeData()
        observePrices()
    }

    fun onIntent(intent: GoalsIntent) {
        when (intent) {
            GoalsIntent.ToggleCompleted -> update {
                it.copy(completedExpanded = !it.completedExpanded)
            }

            GoalsIntent.ToggleSortMode -> update { it.copy(sortMode = !it.sortMode) }

            is GoalsIntent.Reorder -> viewModelScope.launch {
                portfolioRepository.reorderGoals(intent.orderedIds)
            }

            is GoalsIntent.CreateGoal -> update {
                it.copy(editor = newEditor(it, intent.suggestion))
            }

            is GoalsIntent.EditGoal -> update { current ->
                val goal = (current.goals + current.completed)
                    .firstOrNull { it.id == intent.goalId }
                if (goal == null) current else current.copy(editor = editorOf(current, goal))
            }

            GoalsIntent.DismissEditor -> update { it.copy(editor = null) }

            is GoalsIntent.EditorName -> editor { it.copy(name = intent.value, nameError = false) }
            is GoalsIntent.EditorIcon -> editor { it.copy(iconKey = intent.key) }
            // Deger HAM tutulur (ayraçsız); binlik ayraci ekranda
            // ThousandsSeparatorTransformation ekler. Metne yazilsaydi imlec her
            // tusta sona atlar, ortadaki rakam duzenlenemezdi.
            is GoalsIntent.EditorAmount -> editor {
                it.copy(amountText = intent.value, amountError = false)
            }

            is GoalsIntent.EditorUnit -> editor { it.convertTo(intent.unit) }
            is GoalsIntent.EditorContribution -> editor {
                it.copy(contributionText = intent.value)
            }

            is GoalsIntent.EditorMain -> editor { it.copy(isMain = intent.value) }

            GoalsIntent.ToggleEditorDatePicker -> editor {
                it.copy(datePickerOpen = !it.datePickerOpen)
            }

            is GoalsIntent.EditorShiftTargetDate -> editor {
                // Gecmise cekilemez: gecmis tarihli bir hedef icin "tahmini varis"
                // ve "gecikme" hesaplari anlamsiz.
                val shifted = it.targetDate.plusMonths(intent.months)
                if (shifted.toEpochDay() < clock.today().toEpochDay()) it
                else it.copy(targetDate = shifted)
            }
            GoalsIntent.ToggleEditorAdvanced -> editor {
                it.copy(advancedExpanded = !it.advancedExpanded)
            }

            GoalsIntent.SaveEditor -> save()
            GoalsIntent.DeleteEditorGoal -> delete()
        }
    }

    // --- Veri ---------------------------------------------------------------

    private fun observeData() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observeGoals(),
                portfolioRepository.observePositions(),
                portfolioRepository.observeGoalAssets(),
            ) { goals, positions, assignments ->
                // Her hedefin KENDI karsiligi: yalniz kendine atanan varliklar.
                val wealth = goals.associate { it.id to goalWealth(it, positions, assignments) }
                val today = clock.today()
                // Tamamlananlar ayri gruba iner; vadesi gecmis hedef ACIKTIR -
                // tasarimda "Hedef duruyor" der, kapatilmis degil.
                _state.value.copy(
                    stage = if (goals.isEmpty()) GoalsStage.Empty else GoalsStage.Ready,
                    goals = goals.filter { it.status != GoalStatus.Completed },
                    completed = goals.filter { it.status == GoalStatus.Completed },
                    totalWealth = positions.sumOf { it.value },
                    wealthByGoal = wealth,
                    // Tahmini varis HESAPLANIR. Once kalici bir alandi ama onu
                    // uretecek kod yolu yoktu; kart herkese "henüz
                    // hesaplanmadı" diyordu.
                    arrivalByGoal = goals.associate {
                        it.id to goalProjection(it, wealth[it.id] ?: 0.0, today).arrival
                    },
                )
            }.collect { _state.value = it }
        }
    }

    private fun observePrices() {
        viewModelScope.launch {
            priceRepository.observePrices().collect { board ->
                // Gecerli kur geldiginde sakla; gecici bos emisyon iyi degeri silmesin.
                board.byKey("gold_gram")?.ask?.takeIf { it > 0.0 }?.let { latestGoldPrice = it }
                board.byKey("usd_try")?.ask?.takeIf { it > 0.0 }?.let { latestUsdPrice = it }
                update { current ->
                    val editor = current.editor?.copy(
                        goldGramPrice = latestGoldPrice,
                        usdPrice = latestUsdPrice,
                    )
                    // Kur beklerken TL olarak acilmis bir hedef varsa gecis
                    // SIMDI tamamlanir - tutar dogru kurla cevrilir.
                    current.copy(
                        editor = editor?.pendingUnit?.let { editor.convertTo(it) } ?: editor,
                    )
                }
            }
        }
    }

    // --- Sayfa --------------------------------------------------------------

    private fun newEditor(current: GoalsUiState, suggestion: GoalSuggestion?) = GoalEditorState(
        goalId = null,
        name = suggestion?.name.orEmpty(),
        iconKey = suggestion?.iconKey ?: GoalIconKeys.first(),
        amountText = "",
        unit = GoalUnit.Try,
        targetDate = current.goals.firstOrNull()?.targetDate ?: KefeDate(2028, 12, 1),
        contributionText = "",
        isMain = current.goals.none { it.isMain },
        advancedExpanded = true,
        goldGramPrice = latestGoldPrice,
        usdPrice = latestUsdPrice,
        openGoalCount = current.goals.size,
    )

    private fun editorOf(current: GoalsUiState, goal: Goal): GoalEditorState {
        val base = GoalEditorState(
            goalId = goal.id,
            name = goal.name,
            iconKey = goal.iconKey,
            amountText = rawAmount(goal.amount),
            unit = GoalUnit.Try,
            targetDate = goal.targetDate,
            contributionText = rawAmount(goal.monthlyContribution),
            isMain = goal.isMain,
            advancedExpanded = true,
            goldGramPrice = latestGoldPrice,
            usdPrice = latestUsdPrice,
            openGoalCount = current.goals.size,
        )
        // Hedef TL disi bir birime sabitlenmisse tutar o birime cevrilerek acilir.
        // Kur henuz gelmediyse cevrim YAPILMAZ: alan TL olarak acilir ve gecis
        // pendingUnit'e yazilir, observePrices kur gelince tamamlar.
        if (goal.unit == GoalUnit.Try) return base
        val converted = base.convertTo(goal.unit)
        return if (converted.unit == goal.unit) converted else base.copy(pendingUnit = goal.unit)
    }

    /**
     * Tutari TL uzerinden yeni birime cevirir; yazilan deger anlamini korur.
     *
     * Iki kurdan biri bile bilinmiyorsa CEVRIM YAPILMAZ ve birim degismez -
     * beklemek, yanlis bir sayi yazmaktan iyidir. Cagiran gerekiyorsa
     * [GoalEditorState.pendingUnit] ile gecisi kur gelene kadar erteler.
     */
    private fun GoalEditorState.convertTo(target: GoalUnit): GoalEditorState {
        if (target == unit) return this
        val from = rateOrNull(unit) ?: return this
        val to = rateOrNull(target) ?: return this
        // Cevrim uzun ondalik uretebilir (22 TL -> 0,003523162... gr altin);
        // birime yakisan inceliğe yuvarlanir, yoksa alanda 18 haneli sayi belirir.
        val converted = ((amountText.parseAmount() * from) / to).roundTo(target.editDecimals())
        return copy(unit = target, amountText = rawAmount(converted), pendingUnit = null)
    }

    private fun save() {
        val editor = _state.value.editor ?: return
        // Kur bilinmiyorsa null doner ve kayit ENGELLENIR: 1.0 kuruyla yazmak
        // "400 gram" hedefini ₺400 olarak kaydediyordu.
        val amount = editor.amountInTryOrNull()
        // Bos zorunlu alan SESSIZCE yutulmaz: hangisiyse isaretlenir (ekran kirmizi
        // cizip o alana kaydirir) ve kayit yapilmaz.
        val nameBlank = editor.name.isBlank()
        val amountEmpty = amount == null || amount <= 0.0
        if (nameBlank || amountEmpty) {
            this.editor { it.copy(nameError = nameBlank, amountError = amountEmpty) }
            return
        }

        // TAMAMLANANLAR DA ARANIR. Duzenleme tamamlanmis bir hedeften de
        // acilabiliyor (bkz. EditGoal); yalniz aciklara bakilinca `existing` null
        // kaliyor ve kayit hedefi Active'e cekip tamamlandi isaretini, sirasini ve
        // tahmini varisini siliyordu.
        val all = _state.value.goals + _state.value.completed
        val existing = all.firstOrNull { it.id == editor.goalId }
        val goal = Goal(
            // Yeni hedef UUID alir. Once "goal_<ikon>_<sayi>" idi ve silme sonrasi
            // tekrar ediyordu; INSERT OR REPLACE yuzunden yasayan bir hedefi
            // sessizce eziyordu. Sayaci "kullanilmayan ilk sirayi bul" diye
            // duzeltmistik ama o cozum TEK CIHAZA gore: iki telefon ayni sirayi
            // bagimsiz olarak uretir ve esitlemede yine carpisirlar.
            id = editor.goalId ?: newId(),
            name = editor.name.trim(),
            iconKey = editor.iconKey,
            amount = amount,
            unit = editor.unit,
            targetDate = editor.targetDate,
            monthlyContribution = editor.contributionText.parseAmount(),
            isMain = editor.isMain,
            status = existing?.status ?: GoalStatus.Active,
            order = existing?.order ?: _state.value.goals.size,
        )

        viewModelScope.launch {
            // Ana hedef tek olabilir: yenisi isaretlendiginde eskisinin isareti
            // kalkar. TAMAMLANANLAR DA TARANIR - `goals` yalniz aciklari tutuyor
            // ve tamamlanmis bir hedef ana hedef olarak kalabiliyordu; iki ana
            // hedef ayni anda isaretli oluyordu.
            if (goal.isMain) {
                (_state.value.goals + _state.value.completed)
                    .filter { it.isMain && it.id != goal.id }
                    .forEach { portfolioRepository.upsertGoal(it.copy(isMain = false)) }
            }
            portfolioRepository.upsertGoal(goal)
        }
        update { it.copy(editor = null) }
    }


    private fun delete() {
        val id = _state.value.editor?.goalId ?: return
        viewModelScope.launch { portfolioRepository.deleteGoal(id) }
        update { it.copy(editor = null) }
    }

    // --- Yardimci -----------------------------------------------------------

    private inline fun update(block: (GoalsUiState) -> GoalsUiState) {
        _state.value = block(_state.value)
    }

    private inline fun editor(block: (GoalEditorState) -> GoalEditorState) {
        val current = _state.value.editor ?: return
        _state.value = _state.value.copy(editor = block(current))
    }
}

/** Birim degistirince cevrilen tutarin yuvarlanacagi ondalik hane sayisi. */
private fun GoalUnit.editDecimals(): Int = when (this) {
    GoalUnit.Try -> 0
    GoalUnit.GoldGram -> 4
    GoalUnit.Usd -> 2
}

/** [decimals] haneye yuvarlar - cevrimdeki kayan nokta kuyrugunu keser. */
private fun Double.roundTo(decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    return kotlin.math.round(this * factor) / factor
}

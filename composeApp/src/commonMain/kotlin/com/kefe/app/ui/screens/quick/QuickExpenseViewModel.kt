package com.kefe.app.ui.screens.quick

import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.newId
import com.kefe.app.domain.repository.PlanRepository
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.parseTrAmountOrNull
import com.kefe.app.ui.format.rawAmount
import com.kefe.app.ui.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class QuickDay { Today, Yesterday }

data class QuickSavedUi(
    val id: String,
    /** "₺120 eklendi" */
    val title: String,
    /** "Kredi Kartı Limit · Su · bugün" */
    val sub: String,
    val line: QuickLineUi?,
)

data class QuickExpenseUiState(
    /** Defter gelene kadar true - pencere iskelet cizmez, bos kalir. */
    val loading: Boolean = true,
    val categories: List<QuickCategoryUi> = emptyList(),
    val selected: ExpenseCategory? = null,
    val day: QuickDay = QuickDay.Today,
    /** Tus takiminin ham metni ("1050", "12,5"). */
    val amountText: String = "",
    val note: String = "",
    val suggestions: List<NoteSuggestionUi> = emptyList(),
    val line: QuickLineUi? = null,
    val saved: QuickSavedUi? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val amount: Double? get() = amountText.parseTrAmountOrNull()?.takeIf { it > 0.0 }
    val shownAmount: String get() = shownAmount(amountText)
    val canSave: Boolean get() = amount != null && selected != null && !saving
    val saveText: String get() = amount?.let { "Kaydet · ${Money.tlExact(it)}" } ?: "Tutar girin"
}

sealed interface QuickExpenseIntent {
    data class Key(val key: QuickKey) : QuickExpenseIntent
    data class SelectCategory(val category: ExpenseCategory) : QuickExpenseIntent
    data class SelectDay(val day: QuickDay) : QuickExpenseIntent
    data class Note(val text: String) : QuickExpenseIntent
    /** Not cipi: not ve tutar o notun son girisiyle dolar. */
    data class PickSuggestion(val suggestion: NoteSuggestionUi) : QuickExpenseIntent
    data object Save : QuickExpenseIntent
    /** Kaydedileni siler, form degerleriyle geri gelir. */
    data object Undo : QuickExpenseIntent
    /** Ayni kalemde yeni harcama: tutar ve not bosalir. */
    data object Again : QuickExpenseIntent
    data object Close : QuickExpenseIntent
}

sealed interface QuickExpenseEffect {
    data object Close : QuickExpenseEffect
}

/**
 * Ana ekrandaki hizli giris penceresi (widget, kisayol, hizli ayar).
 *
 * Kalem sirasi pencere ACILIRKEN bir kez kurulur: kaydedince cipler yer
 * degistirmesin. Satir ve not onerileri defterle birlikte tazelenir. Kayittan
 * sonra pencere [autoCloseMillis] icinde kendiliginden kapanir; "Geri al" ya da
 * "Bir tane daha" sayaci durdurur.
 *
 * Kilit SORULMAZ (kullanici karari, Ekim 2026): pencere yalniz harcama ekler ve
 * secili kalemin kalanini gosterir; varliklar gorunmez, telefonun kilidi yeter.
 */
class QuickExpenseViewModel(
    private val planRepository: PlanRepository,
    private val portfolioRepository: PortfolioRepository,
    private val preferences: PreferencesRepository,
    private val clock: KefeClock,
    private val initialCategory: ExpenseCategory?,
    private val autoCloseMillis: Long = AutoCloseMillis,
) : MviViewModel<QuickExpenseUiState, QuickExpenseIntent, QuickExpenseEffect>(QuickExpenseUiState()) {

    private var books: List<MonthBook> = emptyList()
    private var closing: Job? = null

    init {
        viewModelScope.launch {
            planRepository.observeAllBooks().collect {
                books = it
                if (current.loading) setUp()
                refresh()
            }
        }
    }

    override fun onIntent(intent: QuickExpenseIntent) {
        when (intent) {
            is QuickExpenseIntent.Key -> setState { copy(amountText = typeKey(amountText, intent.key), error = null) }
            is QuickExpenseIntent.SelectCategory -> setState { copy(selected = intent.category, error = null) }
            is QuickExpenseIntent.SelectDay -> setState { copy(day = intent.day) }
            is QuickExpenseIntent.Note -> setState { copy(note = intent.text.take(MaxNoteLength)) }
            is QuickExpenseIntent.PickSuggestion -> setState {
                copy(note = intent.suggestion.note, amountText = rawAmount(intent.suggestion.amount), error = null)
            }
            QuickExpenseIntent.Save -> save()
            QuickExpenseIntent.Undo -> undo()
            QuickExpenseIntent.Again -> {
                closing?.cancel()
                setState { copy(saved = null, amountText = "", note = "") }
            }
            QuickExpenseIntent.Close -> emitEffect(QuickExpenseEffect.Close)
        }
        refresh()
    }

    private fun setUp() {
        val today = clock.today()
        val order = quickCategories(books, today)
        // Sira widget'takiyle ayni kalir; gelen kalem listede yoksa (eski bir ozel kalem) one eklenir.
        val categories = if (initialCategory != null && initialCategory !in order) listOf(initialCategory) + order else order
        setState {
            copy(
                loading = false,
                categories = quickCategoryUis(books, today, categories),
                selected = initialCategory ?: categories.firstOrNull(),
            )
        }
    }

    private fun refresh() {
        val state = current
        val category = state.selected ?: return
        val date = dateOf(state.day)
        setState {
            copy(
                suggestions = noteSuggestions(books, category, clock.today()),
                line = quickLine(bookOf(date), category, amount ?: 0.0),
            )
        }
    }

    private fun save() {
        val state = current
        val category = state.selected ?: return
        val amount = state.amount ?: return
        if (state.saving || state.saved != null) return
        val date = dateOf(state.day)
        val note = state.note.trim().takeIf { it.isNotEmpty() }
        setState { copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val entry = ExpenseEntry(
                    id = newId(),
                    date = date,
                    category = category,
                    amount = amount,
                    note = note,
                    addedByMemberId = memberId(),
                )
                // Satir KAYITTAN ONCEKI defterden: kayit akisi geri gelince harcama iki kez dusmesin.
                val line = quickSavedLine(bookOf(date), category, amount)
                planRepository.upsertExpense(entry)
                setState {
                    copy(
                        saving = false,
                        saved = QuickSavedUi(
                            id = entry.id,
                            title = "${Money.tlExact(amount)} eklendi",
                            sub = listOfNotNull(
                                category.label(),
                                note,
                                if (state.day == QuickDay.Today) "bugün" else "dün",
                            ).joinToString(" · "),
                            line = line,
                        ),
                    )
                }
                closing = launch {
                    delay(autoCloseMillis)
                    emitEffect(QuickExpenseEffect.Close)
                }
            } catch (error: Exception) {
                setState { copy(saving = false, error = "Harcama kaydedilemedi.") }
            }
        }
    }

    private fun undo() {
        val saved = current.saved ?: return
        closing?.cancel()
        setState { copy(saved = null) }
        viewModelScope.launch {
            try {
                planRepository.deleteExpense(saved.id)
            } catch (error: Exception) {
                setState { copy(error = "Harcama geri alınamadı.") }
            }
        }
    }

    /** Bu cihazin profili, yoksa ilk uye (uygulamadaki harcama formuyla ayni kural). */
    private suspend fun memberId(): String? =
        preferences.get(PreferenceKeys.ActiveMemberId)
            ?: portfolioRepository.observeMembers().first().firstOrNull()?.id

    private fun dateOf(day: QuickDay): KefeDate {
        val today = clock.today()
        return if (day == QuickDay.Today) today else today.yesterday()
    }

    private fun bookOf(date: KefeDate): MonthBook {
        val month = YearMonth.of(date)
        return books.firstOrNull { it.month == month } ?: MonthBook(month)
    }

    companion object {
        /** Kayittan sonra pencerenin acik kaldigi sure (tasarim: 3 saniye). */
        const val AutoCloseMillis = 3_000L
        private const val MaxNoteLength = 80
    }
}

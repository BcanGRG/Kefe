package com.kefe.app.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanExpensesUiState(
    val month: YearMonth,
    val filter: ExpenseFilter,
    val sort: ExpenseSort = ExpenseSort.Date,
    /** Defter gelene kadar null - ekran iskelet cizer. */
    val page: ExpensesPageUi? = null,
)

sealed interface PlanExpensesIntent {
    /** Cip ya da dagilim satiri: sayfa yerinde suzulur, yeni sayfa acilmaz. */
    data class SelectFilter(val filter: ExpenseFilter) : PlanExpensesIntent
    data class SelectSort(val sort: ExpenseSort) : PlanExpensesIntent
}

/**
 * "Harcamalar" sayfasi - bir ayin defteri, suzgec ve siralama.
 *
 * Duzenleme ve ekleme formlari burada DEGIL: Plan VM'inin sheet'leri kabukta, her
 * ekranin ustunde cizilir (bkz. PlanSheets). Bu sayfa yalniz okur; kaydedilen
 * harcama defter akisindan buraya kendiliginden gelir.
 */
class PlanExpensesViewModel(
    private val planRepository: PlanRepository,
    private val clock: KefeClock,
    private val month: YearMonth,
    initialFilter: ExpenseFilter,
) : ViewModel() {

    val state: StateFlow<PlanExpensesUiState>
        field = MutableStateFlow(PlanExpensesUiState(month = month, filter = initialFilter))

    private var book: MonthBook? = null

    init {
        viewModelScope.launch {
            planRepository.observeMonthBook(month).collect {
                book = it
                rebuild()
            }
        }
    }

    fun onIntent(intent: PlanExpensesIntent) {
        when (intent) {
            is PlanExpensesIntent.SelectFilter -> state.update { it.copy(filter = intent.filter) }
            is PlanExpensesIntent.SelectSort -> state.update { it.copy(sort = intent.sort) }
        }
        rebuild()
    }

    private fun rebuild() {
        val current = book ?: return
        state.update { it.copy(page = expensesPage(current, clock.today(), it.filter, it.sort)) }
    }
}

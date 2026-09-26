package com.kefe.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.kefe.app.data.db.toDomain
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.budgetId
import com.kefe.app.domain.model.incomeId
import com.kefe.app.domain.repository.PlanRepository
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Plan/butce deposu.
 *
 * Dort tablo da yaprak (bkz. 12.sqm), OR REPLACE guvenli. Her yazma
 * `updatedAt`'i ilerletir - ilerletmeyen yazma esitlemede hic gorunmez.
 *
 * Aylara gore SQL'de degil burada suzulur: tablolar ayda birkac satir buyuyor,
 * ayrica sorgu yazmak her ay degisiminde yeni bir akis acmak demekti.
 */
class SqlDelightPlanRepository(
    private val database: KefeDatabase,
    private val clock: KefeClock,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : PlanRepository {

    private val planQueries = database.planItemQueries
    private val incomeQueries = database.incomeQueries
    private val expenseQueries = database.expenseQueries

    override fun observePlanItems(): Flow<List<PlanItem>> =
        planQueries.selectPlanItems().asFlow().mapToList(dispatcher)
            .map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeAllBooks(): Flow<List<MonthBook>> = combine(
        incomeQueries.selectAllIncome().asFlow().mapToList(dispatcher),
        expenseQueries.selectAllExpenses().asFlow().mapToList(dispatcher),
        expenseQueries.selectAllBudgets().asFlow().mapToList(dispatcher),
    ) { incomeRows, expenseRows, budgetRows ->
        val incomes = incomeRows.mapNotNull { it.toDomain() }.groupBy { it.month }
        val expenses = expenseRows.map { it.toDomain() }.groupBy { it.month }
        val budgets = budgetRows.mapNotNull { it.toDomain() }.groupBy { it.month }
        (incomes.keys + expenses.keys + budgets.keys).sorted().map { m ->
            MonthBook(m, incomes[m].orEmpty(), expenses[m].orEmpty(), budgets[m].orEmpty())
        }
    }

    override fun observeMonthBook(month: YearMonth): Flow<MonthBook> =
        observeAllBooks().map { books -> books.firstOrNull { it.month == month } ?: MonthBook(month) }

    override suspend fun upsertPlanItem(item: PlanItem) {
        withContext(dispatcher) { writePlanItem(item, clock.nowEpochMillis()) }
    }

    override suspend fun replacePlanItem(oldId: String, item: PlanItem) {
        withContext(dispatcher) {
            database.transaction {
                val now = clock.nowEpochMillis()
                if (oldId != item.id) planQueries.deletePlanItemById(deletedAt = now, id = oldId)
                writePlanItem(item, now)
            }
        }
    }

    override suspend fun upsertPlanItems(items: List<PlanItem>) {
        withContext(dispatcher) {
            database.transaction {
                val now = clock.nowEpochMillis()
                items.forEach { writePlanItem(it, now) }
            }
        }
    }

    private fun writePlanItem(item: PlanItem, now: Long) {
        planQueries.upsertPlanItem(
            id = item.id,
            periodYear = item.month.year.toLong(),
            periodMonth = item.month.month.toLong(),
            assetKey = item.assetKey,
            assetName = item.assetName,
            mode = item.mode.name,
            target = item.target,
            goalId = item.goalId,
            unitPriceAtPlan = item.unitPriceAtPlan,
            updatedAt = now,
        )
    }

    override suspend fun deletePlanItem(id: String) {
        withContext(dispatcher) { planQueries.deletePlanItemById(deletedAt = clock.nowEpochMillis(), id = id) }
    }

    override suspend fun setIncome(month: YearMonth, memberId: String, kind: IncomeKind, amount: Double?) {
        withContext(dispatcher) {
            val id = incomeId(month, memberId, kind)
            val now = clock.nowEpochMillis()
            if (amount == null || amount <= 0.0) {
                incomeQueries.deleteIncomeById(deletedAt = now, id = id)
            } else {
                incomeQueries.upsertIncome(
                    id = id,
                    periodYear = month.year.toLong(),
                    periodMonth = month.month.toLong(),
                    memberId = memberId,
                    kind = kind.name,
                    amount = amount,
                    updatedAt = now,
                )
            }
        }
    }

    override suspend fun upsertExpense(entry: ExpenseEntry) {
        withContext(dispatcher) {
            val now = clock.nowEpochMillis()
            expenseQueries.upsertExpense(
                id = entry.id,
                dateYear = entry.date.year.toLong(),
                dateMonth = entry.date.month.toLong(),
                dateDay = entry.date.day.toLong(),
                category = entry.category.name,
                amount = entry.amount,
                note = entry.note?.takeIf { it.isNotBlank() },
                addedByMemberId = entry.addedByMemberId,
                // Duzenleme ilk giris sirasini korur.
                createdAt = entry.createdAt.takeIf { it > 0L } ?: now,
                updatedAt = now,
            )
        }
    }

    override suspend fun deleteExpense(id: String) {
        withContext(dispatcher) { expenseQueries.deleteExpenseById(deletedAt = clock.nowEpochMillis(), id = id) }
    }

    override suspend fun setBudgets(month: YearMonth, amounts: Map<ExpenseCategory, Double?>) {
        withContext(dispatcher) {
            database.transaction {
                val now = clock.nowEpochMillis()
                amounts.forEach { (category, amount) ->
                    val id = budgetId(month, category)
                    if (amount == null || amount <= 0.0) {
                        expenseQueries.deleteBudgetById(deletedAt = now, id = id)
                    } else {
                        expenseQueries.upsertBudget(
                            id = id,
                            periodYear = month.year.toLong(),
                            periodMonth = month.month.toLong(),
                            category = category.name,
                            amount = amount,
                            updatedAt = now,
                        )
                    }
                }
            }
        }
    }
}

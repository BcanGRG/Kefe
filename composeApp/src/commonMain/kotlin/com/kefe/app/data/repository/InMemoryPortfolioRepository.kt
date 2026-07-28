package com.kefe.app.data.repository

import com.kefe.app.data.sample.SampleData
import com.kefe.app.data.sample.SampleSeries
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.ActivityKind
import com.kefe.app.domain.model.DailySnapshot
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Portfolio
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.costBasis
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Bellek ici portfoy deposu - ornek veriyle tohumlanir.
 *
 * ISLEM DEFTERI TEK GERCEK KAYNAKTIR: pozisyonun miktari ve maliyeti hicbir yerde
 * elle guncellenmez, her degisiklikten sonra [costBasis] ile defterden yeniden
 * hesaplanir. Boylece "8 ceyregi bes ayri tarihte aldim" durumu dogru calisir ve
 * bir islem silindiginde ozet rakamlari da kendiliginden duzelir.
 *
 * Kalici depolama (SQLDelight) ve cihazlar arasi esitleme sonraki adim; arayuz
 * yerel-oncelikli sozlesmeye uydugu icin gecis ekranlari etkilemeyecek.
 */
class InMemoryPortfolioRepository : PortfolioRepository {

    private val portfolioState = MutableStateFlow(SampleData.portfolio)
    private val membersState = MutableStateFlow(SampleData.members)
    private val positionsState = MutableStateFlow(SampleData.positions)
    private val goalsState = MutableStateFlow(SampleData.goals)
    private val activityState = MutableStateFlow(SampleData.activity)
    private val snapshotsState = MutableStateFlow(SampleSeries.netWorthSnapshots)
    private val transactionsState = MutableStateFlow(SampleData.transactions)

    override fun observePortfolio(): Flow<Portfolio> = portfolioState.asStateFlow()

    override fun observeMembers(): Flow<List<Member>> = membersState.asStateFlow()

    override fun observePositions(): Flow<List<Position>> = positionsState.asStateFlow()

    override fun observeGoals(): Flow<List<Goal>> =
        goalsState.map { goals -> goals.sortedBy { it.order } }

    override fun observeActivity(): Flow<List<ActivityEvent>> = activityState.asStateFlow()

    override fun observeSnapshots(): Flow<List<DailySnapshot>> = snapshotsState.asStateFlow()

    override fun observeTransactions(positionId: String): Flow<List<Transaction>> =
        transactionsState.map { all -> all.filter { it.positionId == positionId } }

    override fun observeAllTransactions(): Flow<List<Transaction>> = transactionsState.asStateFlow()

    // --- Islemler -----------------------------------------------------------

    override suspend fun addTransaction(transaction: Transaction) {
        transactionsState.value = transactionsState.value + transaction
        recomputePosition(transaction.positionId)
        appendActivity(transaction)
    }

    override suspend fun deleteTransaction(transactionId: String) {
        val removed = transactionsState.value.firstOrNull { it.id == transactionId } ?: return
        transactionsState.value = transactionsState.value.filterNot { it.id == transactionId }
        recomputePosition(removed.positionId)
    }

    private val onboardedState = MutableStateFlow(false)

    override fun observeOnboarded(): Flow<Boolean> = onboardedState.asStateFlow()

    override suspend fun markOnboarded() {
        onboardedState.value = true
    }

    override suspend fun deleteAllData() {
        positionsState.value = emptyList()
        transactionsState.value = emptyList()
        goalsState.value = emptyList()
        activityState.value = emptyList()
        snapshotsState.value = emptyList()
        onboardedState.value = false
    }

    override suspend fun recordSnapshot(snapshot: DailySnapshot) {
        // Gunde tek satir: ayni gune ikinci yazma satiri tazeler, cogaltmaz.
        snapshotsState.value = snapshotsState.value
            .filterNot { it.date == snapshot.date }
            .plus(snapshot)
            .sortedBy { it.date.toEpochDay() }
    }

    /**
     * Pozisyonu defterden yeniden kurar.
     *
     * Meta bilgi (ad, sinif, birim, guncel birim fiyat) pozisyonda kalir; yalniz
     * miktar, maliyet ve deger defterden gelir. Miktar sifirlanirsa - yani varliktan
     * tamamen cikildiysa - pozisyon listeden dusulur.
     */
    private fun recomputePosition(positionId: String) {
        val basis = transactionsState.value
            .filter { it.positionId == positionId }
            .costBasis()

        val existing = positionsState.value.firstOrNull { it.id == positionId }

        if (basis.quantity <= 0.0) {
            if (existing != null) {
                positionsState.value = positionsState.value.filterNot { it.id == positionId }
            }
            return
        }

        // Pozisyon tanimli degilse islem oksuz kalir; cagiran taraf once
        // upsertPosition ile varligi tanitmali.
        if (existing == null) return

        positionsState.value = positionsState.value.map { position ->
            if (position.id != positionId) {
                position
            } else {
                position.copy(
                    quantity = basis.quantity,
                    cost = basis.totalCost,
                    value = basis.quantity * position.unitPrice,
                )
            }
        }
    }

    /** Yeni islem Aktivite akisina da dusmeli - tasarimda "kim ne ekledi" oradan okunur. */
    private fun appendActivity(transaction: Transaction) {
        val position = positionsState.value.firstOrNull { it.id == transaction.positionId }
        val name = position?.name ?: "Varlık"
        val quantity = formatQuantity(transaction.quantity)

        val event = ActivityEvent(
            id = "act_${transaction.id}",
            memberId = transaction.addedByMemberId,
            kind = when (transaction.side) {
                TradeSide.Buy -> ActivityKind.AddTransaction
                TradeSide.Sell -> ActivityKind.SellTransaction
            },
            description = when (transaction.side) {
                TradeSide.Buy -> "$quantity $name ekledi"
                TradeSide.Sell -> "$quantity $name sattı"
            },
            amount = transaction.total,
            timeLabel = "az önce",
            dayGroup = "BUGÜN",
        )
        activityState.value = listOf(event) + activityState.value
    }

    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')

    // --- Pozisyonlar --------------------------------------------------------

    override suspend fun upsertPosition(position: Position) {
        val existing = positionsState.value
        positionsState.value = if (existing.any { it.id == position.id }) {
            existing.map { if (it.id == position.id) position else it }
        } else {
            existing + position
        }
        // Defter zaten doluysa miktar/maliyet oradan gelsin.
        recomputePosition(position.id)
    }

    override suspend fun deletePosition(positionId: String) {
        positionsState.value = positionsState.value.filterNot { it.id == positionId }
        transactionsState.value = transactionsState.value.filterNot { it.positionId == positionId }
    }

    // --- Hedefler -----------------------------------------------------------

    override suspend fun upsertGoal(goal: Goal) {
        val existing = goalsState.value
        goalsState.value = if (existing.any { it.id == goal.id }) {
            existing.map { if (it.id == goal.id) goal else it }
        } else {
            existing + goal
        }
    }

    override suspend fun deleteGoal(goalId: String) {
        goalsState.value = goalsState.value.filterNot { it.id == goalId }
    }

    override suspend fun reorderGoals(orderedIds: List<String>) {
        val byId = goalsState.value.associateBy { it.id }
        // Listede olmayan hedefler sirayi bozmasin diye sona eklenir.
        val reordered = orderedIds.mapNotNull(byId::get).mapIndexed { index, goal ->
            goal.copy(order = index)
        }
        val rest = goalsState.value.filterNot { it.id in orderedIds }
        goalsState.value = reordered + rest
    }
}

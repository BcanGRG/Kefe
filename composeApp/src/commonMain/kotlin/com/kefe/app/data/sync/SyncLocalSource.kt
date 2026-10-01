package com.kefe.app.data.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.kefe.app.db.KefeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/** Tek tablonun push yuku: hangi tablo, gonderilecek JSON dizisi. */
data class TableBatch(val table: String, val rowsJson: String)

/**
 * Push'un YEREL yani: neyin degistigini okur, sunucu bicimine (snake_case JSON)
 * cevirir; ayri olarak "yerelde bir sey degisti" sinyalini uretir.
 *
 * Turetilen/cihaz-yerel alanlar burada DUSER - pozisyonun miktar/maliyet/degeri,
 * gunluk degisim, hedefin tahmini tarihi, islemin syncState'i sunucuya gitmez.
 */
class SyncLocalSource(
    private val database: KefeDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) {

    // NULL'lar acikca yazilir: silinen satir dirildiginde (deletedAt tekrar null)
    // sunucudaki mezar tasi ancak "deleted_at: null" gonderilirse temizlenir.
    private val json = Json { explicitNulls = true; encodeDefaults = true }

    /**
     * [since]'ten beri degisen her satiri tablo tablo toplar. Bos tablo atlanir.
     * deletedAt FILTRELENMEZ - mezar taslari da gider.
     *
     * Sira: ust once (members, positions) alt sonra, plan tablolari en sonda.
     * Sunucuda tablolar arasi yabanci anahtar yok, yani sira zorunlu degil;
     * yalniz okunurluk icin.
     */
    suspend fun changesSince(since: Long, userId: String): List<TableBatch> =
        withContext(dispatcher) {
            buildList {
                database.portfolioQueries.selectMembersChangedSince(since).executeAsList()
                    .map { r ->
                        MemberDto(
                            id = r.id,
                            userId = userId,
                            name = r.name,
                            initials = r.initials,
                            sortOrder = r.sortOrder,
                            updatedAt = r.updatedAt,
                        )
                    }
                    .let { batch("members", it) }

                database.positionQueries.selectPositionsChangedSince(since).executeAsList()
                    .map { r ->
                        PositionDto(
                            id = r.id,
                            userId = userId,
                            name = r.name,
                            assetClass = r.assetClass.name,
                            subtype = r.subtype?.name,
                            karat = r.karat?.name,
                            unit = r.unit.name,
                            unitPrice = r.unitPrice,
                            manualPrice = r.manualPrice,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                        )
                    }
                    .let { batch("positions", it) }

                database.transactionQueries.selectTransactionsChangedSince(since).executeAsList()
                    .map { r ->
                        TransactionDto(
                            id = r.id,
                            userId = userId,
                            positionId = r.positionId,
                            dateYear = r.dateYear,
                            dateMonth = r.dateMonth,
                            dateDay = r.dateDay,
                            side = r.side.name,
                            quantity = r.quantity,
                            unitPrice = r.unitPrice,
                            fee = r.fee,
                            note = r.note,
                            storage = r.storage,
                            addedByMemberId = r.addedByMemberId,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                            createdAt = r.createdAt,
                            goalId = r.goalId,
                            goalDelta = r.goalDelta,
                        )
                    }
                    .let { batch("transactions", it) }

                database.goalQueries.selectGoalsChangedSince(since).executeAsList()
                    .map { r ->
                        GoalDto(
                            id = r.id,
                            userId = userId,
                            name = r.name,
                            iconKey = r.iconKey,
                            amount = r.amount,
                            unit = r.unit.name,
                            targetYear = r.targetYear,
                            targetMonth = r.targetMonth,
                            targetDay = r.targetDay,
                            monthlyContribution = r.monthlyContribution,
                            isMain = r.isMain,
                            status = r.status.name,
                            sortOrder = r.sortOrder,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                            anchorUnit = r.anchorUnit,
                            anchorAmount = r.anchorAmount,
                            spentAt = r.spentAt,
                            contributionAnchor = r.contributionAnchor,
                        )
                    }
                    .let { batch("goals", it) }

                database.goalAssetQueries.selectGoalAssetsChangedSince(since).executeAsList()
                    .map { r ->
                        GoalAssetDto(
                            positionId = r.positionId,
                            userId = userId,
                            goalId = r.goalId,
                            quantity = r.quantity,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                        )
                    }
                    .let { batch("goal_assets", it) }

                database.snapshotQueries.selectSnapshotsChangedSince(since).executeAsList()
                    .map { r ->
                        SnapshotDto(
                            userId = userId,
                            dateYear = r.dateYear,
                            dateMonth = r.dateMonth,
                            dateDay = r.dateDay,
                            totalValue = r.totalValue,
                            principal = r.principal,
                            updatedAt = r.updatedAt,
                        )
                    }
                    .let { batch("daily_snapshots", it) }

                database.activityQueries.selectActivityChangedSince(since).executeAsList()
                    .map { r ->
                        ActivityDto(
                            id = r.id,
                            userId = userId,
                            memberId = r.memberId,
                            kind = r.kind.name,
                            description = r.description,
                            amount = r.amount,
                            isManualPrice = r.isManualPrice,
                            occurredYear = r.occurredYear,
                            occurredMonth = r.occurredMonth,
                            occurredDay = r.occurredDay,
                            timeLabel = r.timeLabel,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                        )
                    }
                    .let { batch("activity_events", it) }

                // Plan tablolari. mode/kind/category DUZ METIN aynen gider -
                // bu telefonun tanimadigi bir deger de (bkz. SyncDtos).
                database.planItemQueries.selectPlanItemsChangedSince(since).executeAsList()
                    .map { r ->
                        PlanItemDto(
                            id = r.id,
                            userId = userId,
                            periodYear = r.periodYear,
                            periodMonth = r.periodMonth,
                            assetKey = r.assetKey,
                            assetName = r.assetName,
                            mode = r.mode,
                            target = r.target,
                            goalId = r.goalId,
                            unitPriceAtPlan = r.unitPriceAtPlan,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                        )
                    }
                    .let { batch("plan_items", it) }

                database.incomeQueries.selectIncomeChangedSince(since).executeAsList()
                    .map { r ->
                        IncomeEntryDto(
                            id = r.id,
                            userId = userId,
                            periodYear = r.periodYear,
                            periodMonth = r.periodMonth,
                            memberId = r.memberId,
                            kind = r.kind,
                            amount = r.amount,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                        )
                    }
                    .let { batch("income_entries", it) }

                database.expenseQueries.selectExpensesChangedSince(since).executeAsList()
                    .map { r ->
                        ExpenseEntryDto(
                            id = r.id,
                            userId = userId,
                            dateYear = r.dateYear,
                            dateMonth = r.dateMonth,
                            dateDay = r.dateDay,
                            category = r.category,
                            amount = r.amount,
                            note = r.note,
                            addedByMemberId = r.addedByMemberId,
                            createdAt = r.createdAt,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                        )
                    }
                    .let { batch("expense_entries", it) }

                database.expenseQueries.selectBudgetsChangedSince(since).executeAsList()
                    .map { r ->
                        ExpenseBudgetDto(
                            id = r.id,
                            userId = userId,
                            periodYear = r.periodYear,
                            periodMonth = r.periodMonth,
                            category = r.category,
                            amount = r.amount,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
                        )
                    }
                    .let { batch("expense_budgets", it) }
            }
        }

    /**
     * Hesaba henuz gonderilmemis bir yazma var mi: [since] (push watermark'i)
     * ve sonrasinda degismis satir. [since] null = hic push olmadi; o zaman
     * GERCEK bir yazma (damgasi > 0) aranir - kurulumun adsiz profilleri
     * (damga 0) "gonderilmemis degisiklik" sayilmaz.
     *
     * Yaklasiktir: karsi telefondan cekilmis ve watermark'tan yeni damgali bir
     * satir da sayilir (bir sonraki push onu da yeniden gonderir). "Hesaptan
     * çık" uyarisi icin fazla temkinli olmak, eksik uyarmaktan iyidir.
     *
     * Plan, gelir, gider ve butce de bakilir: esitlenen her tablo burada da
     * olmali, yoksa yalniz butcenin degistigi bir gunde cikis uyarmazdi.
     */
    suspend fun hasChangesSince(since: Long?): Boolean = withContext(dispatcher) {
        val from = since ?: 1L
        database.portfolioQueries.selectMembersChangedSince(from).executeAsList().isNotEmpty() ||
            database.positionQueries.selectPositionsChangedSince(from).executeAsList().isNotEmpty() ||
            database.transactionQueries.selectTransactionsChangedSince(from).executeAsList().isNotEmpty() ||
            database.goalQueries.selectGoalsChangedSince(from).executeAsList().isNotEmpty() ||
            database.goalAssetQueries.selectGoalAssetsChangedSince(from).executeAsList().isNotEmpty() ||
            database.snapshotQueries.selectSnapshotsChangedSince(from).executeAsList().isNotEmpty() ||
            database.activityQueries.selectActivityChangedSince(from).executeAsList().isNotEmpty() ||
            database.planItemQueries.selectPlanItemsChangedSince(from).executeAsList().isNotEmpty() ||
            database.incomeQueries.selectIncomeChangedSince(from).executeAsList().isNotEmpty() ||
            database.expenseQueries.selectExpensesChangedSince(from).executeAsList().isNotEmpty() ||
            database.expenseQueries.selectBudgetsChangedSince(from).executeAsList().isNotEmpty()
    }

    /**
     * Yerelde herhangi bir senkron tablosuna yazildiginda emit eder. SQLDelight
     * bildirim TABLO duzeyinde: sayac degismese de (guncelleme, silme) akis
     * yeniden verir, yani ekleme kadar duzenleme ve silme de yakalanir.
     *
     * POLL YOK: bu tamamen olay-guduml u - yazma olmadan hicbir sey emit etmez.
     *
     * Plan tablolari da dinlenir: dinlenmeseler yalniz plan degisen bir yazma
     * push'u hic tetiklemez, bir sonraki baska yazmaya kadar beklerdi.
     */
    fun localChanges(): Flow<Unit> = combine(
        database.transactionQueries.countTransactions().asFlow().mapToOne(dispatcher),
        database.positionQueries.countPositions().asFlow().mapToOne(dispatcher),
        database.goalQueries.countGoals().asFlow().mapToOne(dispatcher),
        database.goalAssetQueries.countGoalAssets().asFlow().mapToOne(dispatcher),
        database.portfolioQueries.countMembers().asFlow().mapToOne(dispatcher),
        database.snapshotQueries.countSnapshots().asFlow().mapToOne(dispatcher),
        database.activityQueries.countActivity().asFlow().mapToOne(dispatcher),
        database.planItemQueries.countPlanItems().asFlow().mapToOne(dispatcher),
        database.incomeQueries.countIncome().asFlow().mapToOne(dispatcher),
        database.expenseQueries.countExpenses().asFlow().mapToOne(dispatcher),
        database.expenseQueries.countBudgets().asFlow().mapToOne(dispatcher),
    ) { _ -> }

    private inline fun <reified T> MutableList<TableBatch>.batch(table: String, rows: List<T>) {
        if (rows.isNotEmpty()) add(TableBatch(table, json.encodeToString(rows)))
    }
}

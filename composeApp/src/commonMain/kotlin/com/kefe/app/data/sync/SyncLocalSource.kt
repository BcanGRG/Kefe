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
     * Sira: ust once (members, positions) alt sonra. Sunucuda tablolar arasi
     * yabanci anahtar yok, yani sira zorunlu degil; yalniz okunurluk icin.
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
                            allocation = r.allocation.name,
                            status = r.status.name,
                            sortOrder = r.sortOrder,
                            updatedAt = r.updatedAt,
                            deletedAt = r.deletedAt,
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
            }
        }

    /**
     * Yerelde herhangi bir senkron tablosuna yazildiginda emit eder. SQLDelight
     * bildirim TABLO duzeyinde: sayac degismese de (guncelleme, silme) akis
     * yeniden verir, yani ekleme kadar duzenleme ve silme de yakalanir.
     *
     * POLL YOK: bu tamamen olay-guduml u - yazma olmadan hicbir sey emit etmez.
     */
    fun localChanges(): Flow<Unit> = combine(
        database.transactionQueries.countTransactions().asFlow().mapToOne(dispatcher),
        database.positionQueries.countPositions().asFlow().mapToOne(dispatcher),
        database.goalQueries.countGoals().asFlow().mapToOne(dispatcher),
        database.goalAssetQueries.countGoalAssets().asFlow().mapToOne(dispatcher),
        database.portfolioQueries.countMembers().asFlow().mapToOne(dispatcher),
        database.snapshotQueries.countSnapshots().asFlow().mapToOne(dispatcher),
        database.activityQueries.countActivity().asFlow().mapToOne(dispatcher),
    ) { _ -> }

    private inline fun <reified T> MutableList<TableBatch>.batch(table: String, rows: List<T>) {
        if (rows.isNotEmpty()) add(TableBatch(table, json.encodeToString(rows)))
    }
}

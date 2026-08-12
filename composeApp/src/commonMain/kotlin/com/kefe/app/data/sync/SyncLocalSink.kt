package com.kefe.app.data.sync

import com.kefe.app.data.db.LocalPortfolioId
import com.kefe.app.data.db.toDomain
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.backup.toAssetClass
import com.kefe.app.domain.backup.toGoalStatus
import com.kefe.app.domain.backup.toGoalUnit
import com.kefe.app.domain.backup.toGoldSubtype
import com.kefe.app.domain.backup.toKarat
import com.kefe.app.domain.backup.toQuantityUnit
import com.kefe.app.domain.backup.toTradeSide
import com.kefe.app.domain.model.ActivityKind
import com.kefe.app.domain.model.costBasis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Pull'un YEREL yani: sunucudan gelen satirlari yerele uygular.
 *
 * CAKISMA COZUMU - LWW (son yazan kazanir): her satirda `updatedAt` var; gelen
 * satir ancak yereldekinden YENIYSE (ya da yerelde hic yoksa) uygulanir. Iki
 * cihazin ayni kaydi degistirdigi ender durumda damgasi buyuk olan gecerli olur.
 *
 * Mezar tasi da uygulanir: gelen `deletedAt` doluysa yereldeki satir da silinmis
 * olur - silme bir yazmadir, LWW ona da isler.
 *
 * TURETILEN yeniden kurulur: islem/pozisyon uygulandiktan sonra pozisyonun
 * miktar/maliyet/degeri defterden yeniden hesaplanir - sunucudan gelmez.
 *
 * Hepsi TEK transaction: yari uygulanmis bir pull, ekranda tutarsiz bir ara durum
 * gosterirdi (ornegin pozisyon geldi ama defteri gelmedi).
 */
class SyncLocalSink(
    private val database: KefeDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) {

    /** Uygulanan (yerelden yeni) satir sayisi - loglama/dogrulama icin. */
    suspend fun apply(batch: PullBatch): Int = withContext(dispatcher) {
        var applied = 0
        database.transaction {
            applied += applyMembers(batch.members)
            applied += applyPositions(batch.positions)
            applied += applyTransactions(batch.transactions)
            applied += applyGoals(batch.goals)
            applied += applyGoalAssets(batch.goalAssets)
            applied += applySnapshots(batch.snapshots)
            applied += applyActivity(batch.activity)

            // Pozisyon ya da islem degistiyse turetilenleri yeniden kur.
            if (batch.positions.isNotEmpty() || batch.transactions.isNotEmpty()) {
                recomputeAllPositions()
            }
        }
        applied
    }

    // Yereldeki tum satirlarin id -> updatedAt haritasi (mezar taslari dahil):
    // changedSince(0) hepsini getirir. LWW karsilastirmasi bunun uzerinden.
    private fun applyMembers(rows: List<MemberDto>): Int {
        if (rows.isEmpty()) return 0
        val local = database.portfolioQueries.selectMembersChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!isNewer(r.updatedAt, local[r.id])) continue
            database.portfolioQueries.insertOrIgnoreMember(
                id = r.id,
                portfolioId = LocalPortfolioId,
                name = r.name,
                initials = r.initials,
                sortOrder = r.sortOrder,
            )
            database.portfolioQueries.applyMemberPull(
                name = r.name,
                initials = r.initials,
                sortOrder = r.sortOrder,
                updatedAt = r.updatedAt,
                id = r.id,
            )
            n++
        }
        return n
    }

    private fun applyPositions(rows: List<PositionDto>): Int {
        if (rows.isEmpty()) return 0
        val local = database.positionQueries.selectPositionsChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!isNewer(r.updatedAt, local[r.id])) continue
            database.positionQueries.insertOrIgnorePosition(
                id = r.id,
                name = r.name,
                assetClass = r.assetClass.toAssetClass(),
                subtype = r.subtype.toGoldSubtype(),
                karat = r.karat.toKarat(),
                unit = r.unit.toQuantityUnit(),
                unitPrice = r.unitPrice,
                manualPrice = r.manualPrice,
                dailyChangePercent = 0.0,
                updatedAt = r.updatedAt,
            )
            database.positionQueries.applyPositionMetaPull(
                name = r.name,
                assetClass = r.assetClass.toAssetClass(),
                subtype = r.subtype.toGoldSubtype(),
                karat = r.karat.toKarat(),
                unit = r.unit.toQuantityUnit(),
                unitPrice = r.unitPrice,
                manualPrice = r.manualPrice,
                updatedAt = r.updatedAt,
                deletedAt = r.deletedAt,
                id = r.id,
            )
            n++
        }
        return n
    }

    private fun applyTransactions(rows: List<TransactionDto>): Int {
        if (rows.isEmpty()) return 0
        val local = database.transactionQueries.selectTransactionsChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!isNewer(r.updatedAt, local[r.id])) continue
            // IKI ADIM: createdAt yalniz ilk goruste yazilir, sonraki
            // guncellemeler ona dokunmaz - olusturulma sirasi degismemeli.
            // Sunucu damgasi yoksa (kolon eklenmeden once yazilmis satir)
            // updatedAt'e duseriz; o deger de butun cihazlarda ayni.
            database.transactionQueries.insertOrIgnoreTransactionPull(
                id = r.id,
                positionId = r.positionId,
                dateYear = r.dateYear,
                dateMonth = r.dateMonth,
                dateDay = r.dateDay,
                side = r.side.toTradeSide(),
                quantity = r.quantity,
                unitPrice = r.unitPrice,
                fee = r.fee,
                note = r.note,
                storage = r.storage,
                addedByMemberId = r.addedByMemberId,
                updatedAt = r.updatedAt,
                deletedAt = r.deletedAt,
                createdAt = r.createdAt.takeIf { it > 0L } ?: r.updatedAt,
                goalId = r.goalId,
                goalDelta = r.goalDelta,
            )
            database.transactionQueries.applyTransactionMetaPull(
                id = r.id,
                positionId = r.positionId,
                dateYear = r.dateYear,
                dateMonth = r.dateMonth,
                dateDay = r.dateDay,
                side = r.side.toTradeSide(),
                quantity = r.quantity,
                unitPrice = r.unitPrice,
                fee = r.fee,
                note = r.note,
                storage = r.storage,
                addedByMemberId = r.addedByMemberId,
                updatedAt = r.updatedAt,
                deletedAt = r.deletedAt,
                goalId = r.goalId,
                goalDelta = r.goalDelta,
            )
            n++
        }
        return n
    }

    private fun applyGoals(rows: List<GoalDto>): Int {
        if (rows.isEmpty()) return 0
        val local = database.goalQueries.selectGoalsChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!isNewer(r.updatedAt, local[r.id])) continue
            database.goalQueries.insertOrIgnoreGoalPull(
                id = r.id,
                name = r.name,
                iconKey = r.iconKey,
                amount = r.amount,
                unit = r.unit.toGoalUnit(),
                targetYear = r.targetYear,
                targetMonth = r.targetMonth,
                targetDay = r.targetDay,
                monthlyContribution = r.monthlyContribution,
                isMain = r.isMain,
                status = r.status.toGoalStatus(),
                sortOrder = r.sortOrder,
                updatedAt = r.updatedAt,
            )
            database.goalQueries.applyGoalMetaPull(
                name = r.name,
                iconKey = r.iconKey,
                amount = r.amount,
                unit = r.unit.toGoalUnit(),
                targetYear = r.targetYear,
                targetMonth = r.targetMonth,
                targetDay = r.targetDay,
                monthlyContribution = r.monthlyContribution,
                isMain = r.isMain,
                status = r.status.toGoalStatus(),
                sortOrder = r.sortOrder,
                updatedAt = r.updatedAt,
                deletedAt = r.deletedAt,
                id = r.id,
            )
            n++
        }
        return n
    }

    private fun applyGoalAssets(rows: List<GoalAssetDto>): Int {
        if (rows.isEmpty()) return 0
        val local = database.goalAssetQueries.selectGoalAssetsChangedSince(0).executeAsList()
            .associate { it.positionId to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!isNewer(r.updatedAt, local[r.positionId])) continue
            database.goalAssetQueries.applyGoalAssetPull(
                positionId = r.positionId,
                goalId = r.goalId,
                quantity = r.quantity,
                updatedAt = r.updatedAt,
                deletedAt = r.deletedAt,
            )
            n++
        }
        return n
    }

    private fun applySnapshots(rows: List<SnapshotDto>): Int {
        if (rows.isEmpty()) return 0
        val local = database.snapshotQueries.selectSnapshotsChangedSince(0).executeAsList()
            .associate { snapshotKey(it.dateYear, it.dateMonth, it.dateDay) to it.updatedAt }
        var n = 0
        for (r in rows) {
            val key = snapshotKey(r.dateYear, r.dateMonth, r.dateDay)
            if (!isNewer(r.updatedAt, local[key])) continue
            database.snapshotQueries.upsertSnapshot(
                dateYear = r.dateYear,
                dateMonth = r.dateMonth,
                dateDay = r.dateDay,
                totalValue = r.totalValue,
                principal = r.principal,
                updatedAt = r.updatedAt,
            )
            n++
        }
        return n
    }

    private fun applyActivity(rows: List<ActivityDto>): Int {
        if (rows.isEmpty()) return 0
        val local = database.activityQueries.selectActivityChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!isNewer(r.updatedAt, local[r.id])) continue
            database.activityQueries.applyActivityPull(
                id = r.id,
                memberId = r.memberId,
                kind = r.kind.toActivityKind(),
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
            n++
        }
        return n
    }

    /** Pozisyonun miktar/maliyet/degerini DEFTERDEN yeniden kurar (bkz. repo). */
    private fun recomputeAllPositions() {
        val ids = database.positionQueries.selectPositionsChangedSince(0).executeAsList().map { it.id }
        for (id in ids) {
            val existing = database.positionQueries.selectPositionById(id).executeAsOneOrNull() ?: continue
            val basis = database.transactionQueries.selectTransactionsByPosition(id).executeAsList()
                .map { it.toDomain() }
                .costBasis()
            database.positionQueries.updatePositionComputed(
                basis.quantity,
                basis.totalCost,
                basis.quantity * existing.unitPrice,
                id,
            )
        }
    }

    // Yerelde yoksa (null) her zaman uygula; varsa yalniz gelen daha yeniyse.
    private fun isNewer(incoming: Long, local: Long?): Boolean = local == null || incoming > local

    private fun snapshotKey(y: Long, m: Long, d: Long): Long = y * 10000 + m * 100 + d

    // EnumColumnAdapter ada gore yazar; taninmayan bir deger (yeni surumden gelen)
    // uygulamayi dusurmemeli - GoalUpdate'e duser (en zararsiz akis turu).
    private fun String.toActivityKind(): ActivityKind =
        ActivityKind.entries.firstOrNull { it.name == this } ?: ActivityKind.GoalUpdate
}

/** Pull'da tek turda uygulanacak tum tablolarin cozulmus satirlari. */
data class PullBatch(
    val members: List<MemberDto> = emptyList(),
    val positions: List<PositionDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    val goals: List<GoalDto> = emptyList(),
    val goalAssets: List<GoalAssetDto> = emptyList(),
    val snapshots: List<SnapshotDto> = emptyList(),
    val activity: List<ActivityDto> = emptyList(),
)

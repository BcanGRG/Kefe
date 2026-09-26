package com.kefe.app.data.sync

import com.kefe.app.data.db.LocalPortfolioId
import com.kefe.app.data.db.resetMembersToDefaults
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

    /**
     * Uygulanan (yerelden yeni) satir sayisi - loglama/dogrulama icin.
     *
     * [mode]: bagli cihazin olagan pull'u [ApplyMode.Lww]. Digerleri yalniz
     * baglanti adiminda (bkz. [commitLink]) kullanilir.
     *
     * NEYDI (devralma). Hesaba baglanmadan once bu telefonda "Profiller"
     * ekraninda ad yazilmissa o adlar `updatedAt = simdi` ile damgalaniyordu -
     * sunucudaki gercek adlardan (haftalar once yazilmis) YENI. Giris yapilinca
     * LWW yerel adi korudu, push da onu sunucuya itti: "Burak Can / Merve" iki
     * telefonda da bu cihazda yazilmis bir seyle degisiyordu. Hesap ORTAK bir
     * kayittir; ilk baglanan cihaz onu devralir, ezmez.
     */
    suspend fun apply(batch: PullBatch, mode: ApplyMode = ApplyMode.Lww): Int = withContext(dispatcher) {
        database.transactionWithResult { applyAll(batch, mode, now = 0L) }
    }

    /**
     * Hesap baglantisini TEK ISLEMDE kurar: (gerekirse) esitlenen tablolari
     * bosaltir, hesabin satirlarini [mode] ile uygular, profil adlarini yazar,
     * bu cihazda girilmis kayitlari yeni profile aktarir ve baglanti
     * tercihlerini ([settings]; null = sil) yazar.
     *
     * NEDEN TEK ISLEM. Baglanti anahtari yazildigi an esitleme baslar (bkz.
     * CloudMode). Hesap yarim uygulanmis ya da "bu telefon kimin" secimi
     * yazilmamisken baslarsa, eksik ya da yanlis profile yazilmis satirlar
     * hesaba gider. Islem yarida patlarsa HICBIRI yazilmaz: baglanti yok,
     * push yok - kullanici "Tekrar dene"yle ayni noktaya doner.
     */
    suspend fun commitLink(
        batch: PullBatch,
        mode: ApplyMode,
        renames: List<MemberRename>,
        remap: AuthorRemap?,
        settings: Map<String, String?>,
        now: Long,
    ): Int = withContext(dispatcher) {
        database.transactionWithResult {
            if (mode == ApplyMode.Replace) wipeSyncedTables()
            val applied = applyAll(batch, mode, now)
            // Adlar hesabin satirlarindan SONRA: devralma, bu ekranda duzeltilen
            // adi hesabinkiyle ezmesin.
            renames.forEach { r ->
                database.portfolioQueries.renameMember(
                    name = r.name,
                    initials = r.initials,
                    updatedAt = now,
                    id = r.memberId,
                )
            }
            remap?.let { remapAuthor(it, batch, now) }
            // Canli islemi olan pozisyon canli kalir (bkz. Position.sq,
            // revivePositionsWithLiveTransactions); dirilen pozisyonun miktari
            // defterden yeniden kurulur - applyAll onu silinmis sayip atlamisti.
            database.positionQueries.revivePositionsWithLiveTransactions(updatedAt = now)
            recomputeAllPositions()
            settings.forEach { (key, value) ->
                if (value == null) {
                    database.settingQueries.deleteSetting(key)
                } else {
                    database.settingQueries.upsertSetting(settingKey = key, settingValue = value)
                }
            }
            applied
        }
    }

    /**
     * Baglanti onizlemesinin YEREL yani: canli islem ve hedeflerin kimlikleri
     * ve "kim ekledi" bilgisi, bir de mezar taslari dahil tum kayit kimlikleri.
     * HICBIR SEY YAZMAZ.
     */
    suspend fun readLocalRecords(): LocalRecords = withContext(dispatcher) {
        val tx = database.transactionQueries.selectTransactionsChangedSince(0).executeAsList()
        val goals = database.goalQueries.selectGoalsChangedSince(0).executeAsList()
        LocalRecords(
            liveTransactions = tx.filter { it.deletedAt == null }.associate { it.id to it.addedByMemberId },
            liveGoals = goals.filter { it.deletedAt == null }.mapTo(mutableSetOf()) { it.id },
            allRecordIds = (tx.map { it.id } + goals.map { it.id }).toSet(),
            namedMembers = database.portfolioQueries.selectMembers().executeAsList().any { it.updatedAt > 0L },
        )
    }

    // Transaction icinden cagrilir; kendi transaction'ini acmaz.
    //
    // "Birleştir"de ([ApplyMode.Merge]) IKI TARAFTA DA OLAN kayit hesaptan gelir,
    // damgasi eski olsa bile (accountWins). NEYDI: yalniz profiller ve gunluk
    // fotograflar hesaptan geliyordu, gerisi LWW'ydi. Hesapsizken geri yuklenen
    // cihazda yedegin her satiri "simdi" damgali: LWW yedegin eski halini hesabin
    // yeni halinin ustune yaziyor, hesapta silinmis kayitlari (mezar tasi daha
    // eski) diriltiyor, sonra hepsini iki telefona birden itiyordu. Geri yukleme
    // disinda cakismada ortak rastgele kimlik yok (bkz. classifyLink); kural
    // orada bir sey degistirmez. Pozisyonlar LWW kalir: kimlikleri her
    // portfoyde ayni, "ortak kayit" demek degil (bkz. commitLink'teki dirilis).
    private fun applyAll(batch: PullBatch, mode: ApplyMode, now: Long): Int {
        val accountWins = mode == ApplyMode.Merge
        // Yalniz cihazin kayitlarinin hesaba KATILDIGI baglanti modlarinda.
        val linking = mode == ApplyMode.Adopt || mode == ApplyMode.Merge
        var applied = 0
        applied += applyMembers(batch.members, adopt = mode != ApplyMode.Lww)
        applied += applyPositions(batch.positions)
        applied += applyTransactions(batch.transactions, accountWins)
        applied += applyGoals(batch.goals, accountWins)
        if (mode == ApplyMode.Merge) keepServerMainGoal(batch.goals, now)
        applied += applyGoalAssets(batch.goalAssets, accountWins, accountGoals = batch.goals.takeIf { linking })
        applied += applySnapshots(batch.snapshots, serverWins = mode == ApplyMode.Merge || mode == ApplyMode.Replace)
        applied += applyActivity(batch.activity, accountWins)

        // Pozisyon ya da islem degistiyse turetilenleri yeniden kur.
        if (batch.positions.isNotEmpty() || batch.transactions.isNotEmpty()) {
            recomputeAllPositions()
        }
        return applied
    }

    /**
     * "Hesaptakileri kullan": ESITLENEN tablolar bosaltilir, profiller adsiz
     * haline doner. Plan, gelir, gider ve butce tablolari KALIR - onlar bu
     * turda esitlenmiyor, silinirlerse hicbir yerden geri gelmez. Fiyat
     * onbellegi de kalir: hesaba ait degil, cihazin.
     *
     * Sira: yapraklar once (atama, islem), sonra baktiklari (pozisyon, hedef).
     */
    private fun wipeSyncedTables() {
        database.goalAssetQueries.deleteAllGoalAssets()
        database.transactionQueries.deleteAllTransactions()
        database.positionQueries.deleteAllPositions()
        database.goalQueries.deleteAllGoals()
        database.activityQueries.deleteAllActivity()
        database.snapshotQueries.deleteAllSnapshots()
        database.resetMembersToDefaults()
    }

    /**
     * Bu telefonun profili baglanirken DEGISTI (orn. cihazda "Merve" ilk
     * profildi, hesapta ilk profil "Burak Can"): bu cihazda girilmis ve hesapta
     * OLMAYAN kayitlar yeni profile aktarilir, damga [now].
     *
     * NEYDI. Aktarim yoktu; secim degisince eski kayitlar eski profilde kaliyor,
     * hesabin adlari gelince o profil karsi tarafin adini aliyordu - telefonun
     * sahibinin girdigi her sey esinin adina gorunuyordu. Hesapta zaten olan
     * kayitlara dokunulmaz: onlar hesabin gecmisi.
     */
    private fun remapAuthor(remap: AuthorRemap, batch: PullBatch, now: Long) {
        val serverTx = batch.transactions.mapTo(mutableSetOf()) { it.id }
        database.transactionQueries.selectTransactionsChangedSince(0).executeAsList()
            .filter { it.addedByMemberId == remap.from && it.id !in serverTx }
            .forEach {
                database.transactionQueries.remapTransactionAuthor(
                    memberId = remap.to,
                    updatedAt = now,
                    id = it.id,
                )
            }
        val serverActivity = batch.activity.mapTo(mutableSetOf()) { it.id }
        database.activityQueries.selectActivityChangedSince(0).executeAsList()
            .filter { it.memberId == remap.from && it.id !in serverActivity }
            .forEach {
                database.activityQueries.remapActivityMember(
                    memberId = remap.to,
                    updatedAt = now,
                    id = it.id,
                )
            }
    }

    /**
     * "Birleştir"de ana hedef TEK kalir: hesabinki. Iki cihazin ikisinde de bir
     * ana hedef varsa birlesmeden sonra iki "ana" hedef olurdu; Ozet hangisini
     * gosterecegini bilemezdi. Hesapta ana hedef yoksa cihazinki kalir.
     */
    private fun keepServerMainGoal(goals: List<GoalDto>, now: Long) {
        val main = goals.firstOrNull { it.isMain && it.deletedAt == null }?.id ?: return
        database.goalQueries.clearMainGoalExcept(updatedAt = now, keepId = main)
        database.goalQueries.markMainGoal(updatedAt = now, id = main)
    }

    // Yereldeki tum satirlarin id -> updatedAt haritasi (mezar taslari dahil):
    // changedSince(0) hepsini getirir. LWW karsilastirmasi bunun uzerinden.
    private fun applyMembers(rows: List<MemberDto>, adopt: Boolean): Int {
        if (rows.isEmpty()) return 0
        val local = database.portfolioQueries.selectMembersChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            // Devralmada bile hic adlandirilmamis (damgasi 0) sunucu satiri
            // yereldekini ezmez: orada devralinacak bir ad yok.
            val take = (adopt && r.updatedAt > 0 && r.updatedAt != local[r.id]) || isNewer(r.updatedAt, local[r.id])
            if (!take) continue
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

    // [accountWins]: bkz. applyAll - "Birleştir"de ortak kayit hesaptan gelir.
    private fun applyTransactions(rows: List<TransactionDto>, accountWins: Boolean = false): Int {
        if (rows.isEmpty()) return 0
        val local = database.transactionQueries.selectTransactionsChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!accountWins && !isNewer(r.updatedAt, local[r.id])) continue
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

    private fun applyGoals(rows: List<GoalDto>, accountWins: Boolean = false): Int {
        if (rows.isEmpty()) return 0
        val local = database.goalQueries.selectGoalsChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!accountWins && !isNewer(r.updatedAt, local[r.id])) continue
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

    /**
     * [accountGoals]: yalniz baglanti adiminda (Adopt/Merge) hesabin hedefleri.
     * Atama satirinin anahtari pozisyon kimligi ve o her portfoyde ayni
     * (pos_<varlik>): iki ayri gecmisin satirlari ayni anahtara duser. Cihazin
     * KENDI hedefine (hesapta olmayan, cihazda canli) yaptigi canli atama,
     * hesabin OLU satiriyla (mezar tasi ya da hesapta canli olmayan hedefe
     * atama) ezilmez. NEYDI: daha yeni damgali bir hesap mezar tasi, cihazdaki
     * hedefin atamasini sessizce siliyordu - hedefin ilerlemesi baglanir
     * baglanmaz dusuyordu. Hedef hesapta da varsa (ayni gecmis, orn. geri
     * yukleme) olagan kural gecerli.
     */
    private fun applyGoalAssets(
        rows: List<GoalAssetDto>,
        accountWins: Boolean = false,
        accountGoals: List<GoalDto>? = null,
    ): Int {
        if (rows.isEmpty()) return 0
        val localRows = database.goalAssetQueries.selectGoalAssetsChangedSince(0).executeAsList()
            .associateBy { it.positionId }
        val keepDevice: (GoalAssetDto) -> Boolean = if (accountGoals == null) {
            { false }
        } else {
            val accountGoalIds = accountGoals.mapTo(mutableSetOf()) { it.id }
            val accountLiveGoals = accountGoals.filter { it.deletedAt == null }.mapTo(mutableSetOf()) { it.id }
            val deviceLiveGoals = database.goalQueries.selectGoals().executeAsList().mapTo(mutableSetOf()) { it.id }
            val keep: (GoalAssetDto) -> Boolean = { r ->
                val mine = localRows[r.positionId]
                val accountRowDead = r.deletedAt != null || r.goalId !in accountLiveGoals
                mine != null &&
                    mine.deletedAt == null &&
                    mine.goalId !in accountGoalIds &&
                    mine.goalId in deviceLiveGoals &&
                    accountRowDead
            }
            keep
        }
        var n = 0
        for (r in rows) {
            if (keepDevice(r)) continue
            if (!accountWins && !isNewer(r.updatedAt, localRows[r.positionId]?.updatedAt)) continue
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

    /**
     * [serverWins]: birlesmede ve bos cihazin indirmesinde ayni gunun fotografi
     * HESAPTAN gelir, damgasi eski olsa bile. NEDEN: yeni cihaz acilista
     * "bugun"un fotografini (deger 0, damga simdi) ceker; LWW onu korur ve
     * baglantidan sonraki push hesabin bugunku fotografinin ustune yazardi.
     * Hesapta olmayan gunler cihazdan tamamlanir.
     */
    private fun applySnapshots(rows: List<SnapshotDto>, serverWins: Boolean = false): Int {
        if (rows.isEmpty()) return 0
        val local = database.snapshotQueries.selectSnapshotsChangedSince(0).executeAsList()
            .associate { snapshotKey(it.dateYear, it.dateMonth, it.dateDay) to it.updatedAt }
        var n = 0
        for (r in rows) {
            val key = snapshotKey(r.dateYear, r.dateMonth, r.dateDay)
            if (!serverWins && !isNewer(r.updatedAt, local[key])) continue
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

    private fun applyActivity(rows: List<ActivityDto>, accountWins: Boolean = false): Int {
        if (rows.isEmpty()) return 0
        val local = database.activityQueries.selectActivityChangedSince(0).executeAsList()
            .associate { it.id to it.updatedAt }
        var n = 0
        for (r in rows) {
            if (!accountWins && !isNewer(r.updatedAt, local[r.id])) continue
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

/**
 * Hesabin satirlarinin yerele NASIL uygulanacagi.
 *
 * Olagan esitleme hep [Lww]. Digerleri yalniz hesaba ilk baglanirken, kullanici
 * ne olacagini gordukten sonra (bkz. classifyLink, AccountLinker.commit).
 */
enum class ApplyMode {
    /** Her satirda son yazan kazanir. Bagli cihazin pull'u; ayni hesaba donus. */
    Lww,

    /**
     * Adlandirilmis hesap profilleri DEVRALINIR (yerel damga yeni olsa bile);
     * gerisi LWW. Cihazda kayit var, hesap bos: kayitlar hesaba gidecek.
     */
    Adopt,

    /**
     * "Birleştir" ve bos cihazin indirmesi: devralma + iki tarafta da olan
     * kayit ve ayni gunun fotografi hesaptan + tek ana hedef (hesabinki). Iki
     * tarafin kayitlari birlikte kalir.
     */
    Merge,

    /**
     * "Hesaptakileri kullan": esitlenen tablolar ONCE bosaltilir, sonra
     * hesabinki devralinir. Plan tablolari kalir.
     */
    Replace,
}

/** Baglantiyla birlikte yazilacak profil adi (bu ekranda yazildi/duzeltildi). */
data class MemberRename(val memberId: String, val name: String, val initials: String)

/** Bu cihazda girilmis kayitlarin [from] profilinden [to] profiline aktarimi. */
data class AuthorRemap(val from: String, val to: String)

/**
 * Onizlemenin gordugu yerel kayitlar. "Kayit" = islem ve hedef: kullanicinin
 * elle girdigi, sayilabilen seyler (pozisyon ve profil kimlikleri sabit ve
 * turetilmis, sayilmaz).
 */
data class LocalRecords(
    /** Canli islemler: kimlik -> ekleyen profil. */
    val liveTransactions: Map<String, String> = emptyMap(),
    val liveGoals: Set<String> = emptySet(),
    /** Mezar taslari dahil tum islem ve hedef kimlikleri - ortaklik icin. */
    val allRecordIds: Set<String> = emptySet(),
    /** Cihazda adlandirilmis (damgali) profil var mi. */
    val namedMembers: Boolean = false,
)

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

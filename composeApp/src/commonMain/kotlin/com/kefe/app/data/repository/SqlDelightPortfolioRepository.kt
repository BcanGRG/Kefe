package com.kefe.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.kefe.app.data.db.DefaultCurrency
import com.kefe.app.data.db.DefaultPortfolioName
import com.kefe.app.data.db.LocalPortfolioId
import com.kefe.app.data.db.toDomain
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
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
import com.kefe.app.domain.model.priceKey
import com.kefe.app.domain.model.valuedAt
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PriceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Kalici portfoy deposu (SQLDelight).
 *
 * ISLEM DEFTERI TEK GERCEK KAYNAKTIR - bellek ici surumdeki kural aynen gecerli:
 * pozisyonun miktari ve maliyeti elle guncellenmez, her degisiklikten sonra
 * [costBasis] ile defterden yeniden hesaplanir.
 *
 * TEK FARK, miktar sifirlandiginda pozisyonun ne oldugu: bellekte satir listeden
 * DUSURULUYORDU, burada duruyor ama `quantity > 0` filtresinin disinda kaliyor.
 * SQL'de silmek yabanci anahtar zinciriyle o varligin tum gecmisini de goturur;
 * tumunu satan kullanici defterini kaybetmemeli, ayrica ayni varligi tekrar
 * aldiginda meta bilgisi (ad, ayar, birim) yerinde durmali. Ekranlarin gordugu
 * liste ayni.
 *
 * Yazmalar tek transaction: islem satiri ile pozisyonun yeniden hesabi arasinda
 * akislar araya girmemeli, yoksa ekran bir an "miktar 0" gorur.
 */
class SqlDelightPortfolioRepository(
    private val database: KefeDatabase,
    private val clock: KefeClock,
    // Pozisyonlar okunurken guncel fiyatla degerlenir; bkz. [observePositions].
    // Ters bagimlilik yok - fiyat deposu portfoyu bilmiyor.
    private val priceRepository: PriceRepository,
    // Dispatchers.IO ortak kodda garanti degil; SQLite cagrilari kisa oldugu icin
    // Default havuzu yeterli. Ana is parcacigi her durumda bosta kalir.
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : PortfolioRepository {

    private val portfolioQueries = database.portfolioQueries
    private val positionQueries = database.positionQueries
    private val transactionQueries = database.transactionQueries
    private val goalQueries = database.goalQueries
    private val goalAssetQueries = database.goalAssetQueries
    private val activityQueries = database.activityQueries
    private val snapshotQueries = database.snapshotQueries
    private val settingQueries = database.settingQueries
    private val priceQueries = database.priceQueries

    // --- Okumalar -----------------------------------------------------------

    /**
     * Portfoy satiri yoksa da AKIS SUSMAZ: ekranlar bu akisi combine ile
     * digerlerine bagliyor, bir kez bile deger uretmezse butun ekran yuklenmede
     * kalirdi. Satir yokken yerine varsayilan kimlik konur.
     */
    override fun observePortfolio(): Flow<Portfolio> = combine(
        portfolioQueries.selectPortfolio().asFlow().mapToOneOrNull(dispatcher),
        portfolioQueries.selectMembers().asFlow().mapToList(dispatcher),
    ) { row, members ->
        val memberIds = members.map { it.id }
        row?.toDomain(memberIds) ?: Portfolio(
            id = LocalPortfolioId,
            name = DefaultPortfolioName,
            currency = DefaultCurrency,
            memberIds = memberIds,
        )
    }

    override fun observeMembers(): Flow<List<Member>> =
        portfolioQueries.selectMembers().asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Pozisyonlar - GUNCEL FIYATLA degerlenmis.
     *
     * Tablodaki `unitPrice` ve `value` pozisyon ilk olustugunda yazilip bir daha
     * hic guncellenmiyordu; fiyat yenilemesi icin yazilmis `updatePositionPrice`
     * sorgusu duruyordu ama hicbir yerden cagirilmiyordu. Ceyrek ₺10.018'e
     * alindiysa ekran haftalar sonra da ₺10.018 gosteriyordu.
     *
     * Cozum tabloya yazmak DEGIL, okurken bindirmek: fiyatin tek gercek kaynagi
     * fiyat tablosudur ve ayni sayiyi iki yerde tutmak ikisinin ayrisabilecegi
     * anlamina gelir. Saklanan degerler yalniz fiyat bulunamadiginda - o varlik
     * tabloda yoksa - son bilinen deger olarak kullanilir.
     *
     * Miktar ve maliyet bindirilmez: onlar defterden turer, fiyattan degil.
     */
    override fun observePositions(): Flow<List<Position>> = combine(
        positionQueries.selectActivePositions().asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } },
        priceRepository.observePrices(),
    ) { positions, board ->
        positions.map { position ->
            position.valuedAt(position.priceKey()?.let { board.byKey(it) })
        }
    }

    override fun observeGoalAssets(): Flow<Map<String, String>> =
        goalAssetQueries.selectGoalAssets().asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate { it.positionId to it.goalId } }

    override suspend fun assignPositionToGoal(positionId: String, goalId: String?) {
        withContext(dispatcher) {
            if (goalId == null) {
                goalAssetQueries.clearPositionAssignment(positionId)
            } else {
                goalAssetQueries.assignPositionToGoal(positionId = positionId, goalId = goalId)
            }
        }
    }

    /** Siralama sozu SQL'de tutulur (ORDER BY sortOrder). */
    override fun observeGoals(): Flow<List<Goal>> =
        goalQueries.selectGoals().asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeActivity(): Flow<List<ActivityEvent>> =
        activityQueries.selectActivity().asFlow().mapToList(dispatcher)
            .map { rows ->
                val today = clock.today()
                rows.map { it.toDomain(today) }
            }

    override fun observeSnapshots(): Flow<List<DailySnapshot>> =
        snapshotQueries.selectSnapshots().asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeTransactions(positionId: String): Flow<List<Transaction>> =
        transactionQueries.selectTransactionsByPosition(positionId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        transactionQueries.selectAllTransactions().asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    // --- Islemler -----------------------------------------------------------

    override suspend fun addTransaction(transaction: Transaction) {
        withContext(dispatcher) {
            database.transaction {
                // Kimlik icerikten turetiliyor ("tx_<pozisyon>_<tarih>_<miktar>"),
                // yani ayni gun ayni miktarda ikinci alim AYNI kimligi uretir.
                // Bellekte iki satir sessizce olusuyordu; burada duz INSERT
                // birincil anahtar ihlaliyle atardi. Kullanicinin ikinci islemi
                // kaybolmasin diye kimlik burada tekillestirilir.
                val stored = transaction.copy(id = uniqueTransactionId(transaction.id))

                transactionQueries.insertTransaction(
                    id = stored.id,
                    positionId = stored.positionId,
                    dateYear = stored.date.year.toLong(),
                    dateMonth = stored.date.month.toLong(),
                    dateDay = stored.date.day.toLong(),
                    side = stored.side,
                    quantity = stored.quantity,
                    unitPrice = stored.unitPrice,
                    fee = stored.fee,
                    note = stored.note,
                    storage = stored.storage,
                    addedByMemberId = stored.addedByMemberId,
                    syncState = stored.syncState,
                )
                recomputePosition(stored.positionId)
                appendActivity(stored)
            }
        }
    }

    override suspend fun deleteTransaction(transactionId: String) {
        withContext(dispatcher) {
            database.transaction {
                val removed = transactionQueries.selectTransactionById(transactionId)
                    .executeAsOneOrNull() ?: return@transaction
                transactionQueries.deleteTransactionById(transactionId)
                // Aktivite satiri da gider. Kalirsa Aktivite akisi silinmis bir
                // islemi "ekledi" diye gostermeye devam eder ve Islem Ekle'deki
                // "son eklediginiz" kisayolu artik var olmayan bir kaydi onerir.
                activityQueries.deleteActivityById("act_${removed.id}")
                recomputePosition(removed.positionId)
            }
        }
    }

    override fun observeOnboarded(): Flow<Boolean> =
        settingQueries.selectSetting(OnboardedKey).asFlow().mapToOneOrNull(dispatcher)
            .map { it == "true" }

    override suspend fun markOnboarded() {
        withContext(dispatcher) {
            settingQueries.upsertSetting(settingKey = OnboardedKey, settingValue = "true")
        }
    }

    override suspend fun deleteAllData() {
        withContext(dispatcher) {
            database.transaction {
                // Islemler pozisyonlardan ONCE: yabanci anahtar zinciri aciksa
                // ters sira zaten calisir ama bu sira niyeti okunur kiliyor.
                transactionQueries.deleteAllTransactions()
                positionQueries.deleteAllPositions()
                goalQueries.deleteAllGoals()
                activityQueries.deleteAllActivity()
                snapshotQueries.deleteAllSnapshots()
                priceQueries.deleteAllManualPrices()
                priceQueries.deleteAllCachedPrices()
                priceQueries.deleteAllPriceHistory()

                // Portfoy ve uye BIRAKILIR: onlar kullanici verisi degil kimlik.
                // Silinirse islem eklerken "kim ekledi" bagi kopardi.
                //
                // Ayarlar tumden silinir - tercihler de kullanicinin verisi.
                // Icindeki acilis bayraklari da gittigi icin uygulama sifirdan
                // acilmis gibi baslar: bu, "her seyi sil" dedikten sonra
                // beklenen davranis. Portfoy satirlari INSERT OR IGNORE ile
                // kuruldugu icin acilis kurulumunun tekrar calismasi zararsiz.
                settingQueries.deleteAllSettings()
            }
        }
    }

    override suspend fun recordSnapshot(snapshot: DailySnapshot) {
        withContext(dispatcher) {
            snapshotQueries.upsertSnapshot(
                dateYear = snapshot.date.year.toLong(),
                dateMonth = snapshot.date.month.toLong(),
                dateDay = snapshot.date.day.toLong(),
                totalValue = snapshot.totalValue,
                principal = snapshot.principal,
            )
        }
    }

    /**
     * Pozisyonu defterden yeniden kurar.
     *
     * Meta bilgi (ad, sinif, birim, guncel birim fiyat) pozisyonda kalir; yalniz
     * miktar, maliyet ve deger defterden gelir. Pozisyon tanimli degilse islem
     * oksuz kalir - cagiran taraf once upsertPosition ile varligi tanitmali.
     */
    private fun recomputePosition(positionId: String) {
        val existing = positionQueries.selectPositionById(positionId).executeAsOneOrNull() ?: return
        // Ekran sorgusu DEGIL: costBasis() kararli siraladigi icin defter
        // kronolojik gelmeli, yoksa ayni gun yapilan satis alistan once islenip
        // hesaptan dusuyor.
        val basis = transactionQueries.selectTransactionsForCompute(positionId)
            .executeAsList()
            .map { it.toDomain() }
            .costBasis()

        positionQueries.updatePositionComputed(
            basis.quantity,
            basis.totalCost,
            basis.quantity * existing.unitPrice,
            positionId,
        )
    }

    /**
     * Cakisan kimlige bos bir sira bulur. Kayit sayisi bir avuc oldugu icin
     * dongu pratikte bir tur doner.
     */
    private fun uniqueTransactionId(candidate: String): String {
        if (transactionQueries.selectTransactionById(candidate).executeAsOneOrNull() == null) {
            return candidate
        }
        var suffix = 2
        while (
            transactionQueries.selectTransactionById("${candidate}_$suffix")
                .executeAsOneOrNull() != null
        ) {
            suffix++
        }
        return "${candidate}_$suffix"
    }

    /** Yeni islem Aktivite akisina da dusmeli - tasarimda "kim ne ekledi" oradan okunur. */
    private fun appendActivity(transaction: Transaction) {
        val name = positionQueries.selectPositionById(transaction.positionId)
            .executeAsOneOrNull()?.name ?: "Varlık"
        val quantity = formatQuantity(transaction.quantity)
        // Olayin tarihi ISLEMIN tarihi degil KAYDIN gunudur: akis "kim ne zaman
        // girdi" sorusunu yanitliyor, gecmise donuk bir alim da bugun eklenmistir.
        val today = clock.today()

        activityQueries.insertActivity(
            id = "act_${transaction.id}",
            memberId = transaction.addedByMemberId,
            kind = when (transaction.side) {
                TradeSide.Buy -> ActivityKind.AddTransaction
                TradeSide.Sell -> ActivityKind.SellTransaction
            },
            // METIN BICIMI KORUNMALI: Islem Ekle ekranindaki "son eklediğin"
            // kisayolu bu cumleyi ayristiriyor.
            description = when (transaction.side) {
                TradeSide.Buy -> "$quantity $name ekledi"
                TradeSide.Sell -> "$quantity $name sattı"
            },
            amount = transaction.total,
            isManualPrice = false,
            occurredYear = today.year.toLong(),
            occurredMonth = today.month.toLong(),
            occurredDay = today.day.toLong(),
            // Gercek saat kaynagi yok; okurken bos etiket uretilir.
            timeLabel = null,
        )
    }

    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')

    // --- Pozisyonlar --------------------------------------------------------

    /**
     * YALNIZ META yazar. Iki adim tek transaction icinde: INSERT OR REPLACE satiri
     * once silecegi icin ON DELETE CASCADE tetiklenir ve pozisyonun tum defteri
     * ucardi.
     */
    override suspend fun upsertPosition(position: Position) {
        withContext(dispatcher) {
            database.transaction {
                positionQueries.insertOrIgnorePosition(
                    id = position.id,
                    name = position.name,
                    assetClass = position.assetClass,
                    subtype = position.subtype,
                    karat = position.karat,
                    unit = position.unit,
                    unitPrice = position.unitPrice,
                    manualPrice = position.manualPrice,
                    dailyChangePercent = position.dailyChangePercent,
                )
                positionQueries.updatePositionMeta(
                    name = position.name,
                    assetClass = position.assetClass,
                    subtype = position.subtype,
                    karat = position.karat,
                    unit = position.unit,
                    unitPrice = position.unitPrice,
                    manualPrice = position.manualPrice,
                    dailyChangePercent = position.dailyChangePercent,
                    id = position.id,
                )
                // Defter zaten doluysa miktar/maliyet oradan gelsin.
                recomputePosition(position.id)
            }
        }
    }

    override suspend fun deletePosition(positionId: String) {
        withContext(dispatcher) {
            database.transaction {
                // CASCADE zaten silerdi; yabanci anahtar zorlamasi kapali bir
                // surucuye dusulurse diye defter acikca temizlenir.
                transactionQueries.deleteTransactionsByPosition(positionId)
                positionQueries.deletePositionById(positionId)
            }
        }
    }

    // --- Hedefler -----------------------------------------------------------

    override suspend fun upsertGoal(goal: Goal) {
        withContext(dispatcher) {
            goalQueries.upsertGoal(
                id = goal.id,
                name = goal.name,
                iconKey = goal.iconKey,
                amount = goal.amount,
                unit = goal.unit,
                targetYear = goal.targetDate.year.toLong(),
                targetMonth = goal.targetDate.month.toLong(),
                targetDay = goal.targetDate.day.toLong(),
                monthlyContribution = goal.monthlyContribution,
                isMain = goal.isMain,
                allocation = goal.allocation,
                status = goal.status,
                sortOrder = goal.order.toLong(),
                estimatedYear = goal.estimatedArrival?.year?.toLong(),
                estimatedMonth = goal.estimatedArrival?.month?.toLong(),
                estimatedDay = goal.estimatedArrival?.day?.toLong(),
            )
        }
    }

    override suspend fun deleteGoal(goalId: String) {
        withContext(dispatcher) {
            goalQueries.deleteGoalById(goalId)
        }
    }

    /**
     * Verilen kimlikler indekslerine gore yeniden numaralanir; listede olmayan
     * hedefler kendi sirasini korur. Tek transaction: yarim yazilmis bir sira
     * ekranda hedefleri ziplatirdi.
     */
    override suspend fun reorderGoals(orderedIds: List<String>) {
        withContext(dispatcher) {
            database.transaction {
                orderedIds.forEachIndexed { index, goalId ->
                    goalQueries.updateGoalOrder(sortOrder = index.toLong(), id = goalId)
                }
            }
        }
    }
}

/** Acilis akisinin gecildigini isaretleyen ayar anahtari. */
private const val OnboardedKey = "onboarded"

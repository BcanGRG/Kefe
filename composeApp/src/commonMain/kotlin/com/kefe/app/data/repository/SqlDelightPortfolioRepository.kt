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
import com.kefe.app.domain.backup.BackupFile
import com.kefe.app.domain.backup.BackupGoal
import com.kefe.app.domain.backup.BackupGoalAsset
import com.kefe.app.domain.backup.BackupMember
import com.kefe.app.domain.backup.BackupPosition
import com.kefe.app.domain.backup.BackupSnapshot
import com.kefe.app.domain.backup.BackupTransaction
import com.kefe.app.domain.backup.toAssetClass
import com.kefe.app.domain.backup.toGoalAllocation
import com.kefe.app.domain.backup.toGoalStatus
import com.kefe.app.domain.backup.toGoalUnit
import com.kefe.app.domain.backup.toGoldSubtype
import com.kefe.app.domain.backup.toKarat
import com.kefe.app.domain.backup.toQuantityUnit
import com.kefe.app.domain.backup.toTradeSide
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.ActivityKind
import com.kefe.app.domain.model.DailySnapshot
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Portfolio
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.SyncState
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.costBasis
import com.kefe.app.domain.model.priceKey
import com.kefe.app.domain.model.valuedAt
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PriceRepository
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

    override suspend fun renameMember(memberId: String, name: String, initials: String) {
        withContext(dispatcher) {
            portfolioQueries.renameMember(
                id = memberId,
                name = name,
                initials = initials,
                updatedAt = clock.nowEpochMillis(),
            )
        }
    }

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

    override fun observeGoalAssets(): Flow<Map<String, GoalAssignment>> =
        goalAssetQueries.selectGoalAssets().asFlow().mapToList(dispatcher)
            .map { rows ->
                rows.associate { it.positionId to GoalAssignment(it.goalId, it.quantity) }
            }

    override suspend fun assignPositionToGoal(
        positionId: String,
        goalId: String?,
        quantity: Double,
    ) {
        withContext(dispatcher) {
            if (goalId == null) {
                goalAssetQueries.clearPositionAssignment(positionId = positionId, deletedAt = clock.nowEpochMillis())
            } else {
                goalAssetQueries.assignPositionToGoal(
                    positionId = positionId,
                    goalId = goalId,
                    quantity = quantity,
                    updatedAt = clock.nowEpochMillis(),
                )
            }
        }
    }

    /**
     * Bir varligin SU ANKI atamasi - kayit anindaki "miktari ne kadar
     * artiracagim" hesabi icin. Akisi beklemek yerine tek satir okunur: kayit
     * ve atama ayni islemde bitmeli.
     */
    override suspend fun goalAssignmentOf(positionId: String): GoalAssignment? =
        withContext(dispatcher) {
            goalAssetQueries.selectGoalAssetByPosition(positionId).executeAsOneOrNull()
                ?.let { GoalAssignment(it.goalId, it.quantity) }
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
                // Kimlik cagiran taraftan UUID olarak gelir; burada tekillestirme
                // YOK. Once icerikten turetiliyordu ve carpisanlari burada "_2"
                // ekleyerek ayiriyorduk - iki cihazda o cozum bozuluyor, cunku
                // her cihaz kendi numarasini bagimsiz veriyor.
                val stored = transaction

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
                    updatedAt = clock.nowEpochMillis(),
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
                val now = clock.nowEpochMillis()
                transactionQueries.deleteTransactionById(deletedAt = now, id = transactionId)
                // Aktivite satiri da gider. Kalirsa Aktivite akisi silinmis bir
                // islemi "ekledi" diye gostermeye devam eder ve Islem Ekle'deki
                // "son eklediginiz" kisayolu artik var olmayan bir kaydi onerir.
                // Soft-delete: mezar tasi ese de gider.
                activityQueries.deleteActivityById(deletedAt = now, id = "act_${removed.id}")
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

    override suspend fun clearOnboarded() {
        withContext(dispatcher) {
            settingQueries.deleteSetting(OnboardedKey)
        }
    }

    // --- Yedekleme ----------------------------------------------------------

    /**
     * Kullanicinin GIRDIGI her sey.
     *
     * Fiyat onbellegi ve gunluk fiyat gecmisi disaridadir: kaynaktan yeniden
     * gelirler ve yedege konsalardi geri yukleyen kullanici eski fiyatlarla
     * acilirdi. Aktivite akisi da disarida - islemlerden yeniden uretilebilir
     * bir gunluk, verinin kendisi degil.
     */
    override suspend fun exportBackup(takenOn: String): BackupFile =
        withContext(dispatcher) {
            BackupFile(
                takenOn = takenOn,
                portfolioName = portfolioQueries.selectPortfolio().executeAsOneOrNull()?.name
                    ?: DefaultPortfolioName,
                members = portfolioQueries.selectMembers().executeAsList().map {
                    BackupMember(
                        id = it.id,
                        name = it.name,
                        initials = it.initials,
                    )
                },
                positions = positionQueries.selectAllPositions().executeAsList().map {
                    BackupPosition(
                        id = it.id,
                        name = it.name,
                        assetClass = it.assetClass.name,
                        subtype = it.subtype?.name,
                        karat = it.karat?.name,
                        unit = it.unit.name,
                        unitPrice = it.unitPrice,
                        manualPrice = it.manualPrice,
                    )
                },
                transactions = transactionQueries.selectAllTransactions().executeAsList().map {
                    BackupTransaction(
                        id = it.id,
                        positionId = it.positionId,
                        year = it.dateYear.toInt(),
                        month = it.dateMonth.toInt(),
                        day = it.dateDay.toInt(),
                        side = it.side.name,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice,
                        fee = it.fee,
                        note = it.note,
                        storage = it.storage,
                        addedByMemberId = it.addedByMemberId,
                    )
                },
                goals = goalQueries.selectGoals().executeAsList().map {
                    BackupGoal(
                        id = it.id,
                        name = it.name,
                        iconKey = it.iconKey,
                        amount = it.amount,
                        unit = it.unit.name,
                        year = it.targetYear.toInt(),
                        month = it.targetMonth.toInt(),
                        day = it.targetDay.toInt(),
                        monthlyContribution = it.monthlyContribution,
                        isMain = it.isMain,
                        allocation = it.allocation.name,
                        status = it.status.name,
                        order = it.sortOrder.toInt(),
                    )
                },
                goalAssets = goalAssetQueries.selectGoalAssets().executeAsList().map {
                    BackupGoalAsset(
                        positionId = it.positionId,
                        goalId = it.goalId,
                        quantity = it.quantity,
                    )
                },
                snapshots = snapshotQueries.selectSnapshots().executeAsList().map {
                    BackupSnapshot(
                        year = it.dateYear.toInt(),
                        month = it.dateMonth.toInt(),
                        day = it.dateDay.toInt(),
                        totalValue = it.totalValue,
                        principal = it.principal,
                    )
                },
                settings = settingQueries.selectAllSettings().executeAsList()
                    .associate { it.settingKey to it.settingValue },
            )
        }

    /**
     * Yedegi geri yukler - TEK TRANSACTION.
     *
     * Yarim yuklenmis bir portfoy hic yuklenmemis olandan kotudur: yarida hata
     * cikarsa transaction geri alinir ve kullanici eski verisiyle kalir.
     *
     * Pozisyonlarin miktar/maliyet/deger alanlari yedekte YOK; defter yazildiktan
     * sonra her pozisyon icin yeniden hesaplanir. Boylece yedegin icinde
     * defteriyle celisen bir toplam tasinamaz.
     */
    override suspend fun restoreBackup(file: BackupFile) {
        withContext(dispatcher) {
            database.transaction {
                // Cihaza ait tercihler silme-yeniden yazma boyunca KORUNUR.
                // deleteAllSettings hepsini siliyor; yedekten de atlandiklari
                // icin, korunmasalar Ayse'nin "bu telefon kimin" secimi Volkan'in
                // yedegini yukleyince tamamen kaybolurdu - ne yedekten gelir ne
                // de eskisi kalir.
                val preserved = DeviceOnlySettings.associateWith { key ->
                    settingQueries.selectSetting(key).executeAsOneOrNull()
                }

                transactionQueries.deleteAllTransactions()
                positionQueries.deleteAllPositions()
                goalQueries.deleteAllGoals()
                activityQueries.deleteAllActivity()
                snapshotQueries.deleteAllSnapshots()
                settingQueries.deleteAllSettings()

                preserved.forEach { (key, value) ->
                    if (value != null) {
                        settingQueries.upsertSetting(settingKey = key, settingValue = value)
                    }
                }

                portfolioQueries.updatePortfolio(
                    name = file.portfolioName,
                    currency = DefaultCurrency,
                    id = LocalPortfolioId,
                )

                file.members.forEachIndexed { index, member ->
                    portfolioQueries.insertOrIgnoreMember(
                        id = member.id,
                        portfolioId = LocalPortfolioId,
                        name = member.name,
                        initials = member.initials,
                        sortOrder = index.toLong(),
                    )
                }

                file.positions.forEach { position ->
                    positionQueries.insertOrIgnorePosition(
                        id = position.id,
                        name = position.name,
                        assetClass = position.assetClass.toAssetClass(),
                        subtype = position.subtype.toGoldSubtype(),
                        karat = position.karat.toKarat(),
                        unit = position.unit.toQuantityUnit(),
                        unitPrice = position.unitPrice,
                        manualPrice = position.manualPrice,
                        dailyChangePercent = 0.0,
                        updatedAt = clock.nowEpochMillis(),
                    )
                }

                file.transactions.forEach { tx ->
                    transactionQueries.insertTransaction(
                        id = tx.id,
                        positionId = tx.positionId,
                        dateYear = tx.year.toLong(),
                        dateMonth = tx.month.toLong(),
                        dateDay = tx.day.toLong(),
                        side = tx.side.toTradeSide(),
                        quantity = tx.quantity,
                        unitPrice = tx.unitPrice,
                        fee = tx.fee,
                        note = tx.note,
                        storage = tx.storage,
                        addedByMemberId = tx.addedByMemberId,
                        syncState = SyncState.Synced,
                        updatedAt = clock.nowEpochMillis(),
                    )
                }

                // Miktar ve maliyet DEFTERDEN yeniden hesaplanir.
                file.positions.forEach { recomputePosition(it.id) }

                file.goals.forEach { goal ->
                    goalQueries.upsertGoal(
                        id = goal.id,
                        name = goal.name,
                        iconKey = goal.iconKey,
                        amount = goal.amount,
                        unit = goal.unit.toGoalUnit(),
                        targetYear = goal.year.toLong(),
                        targetMonth = goal.month.toLong(),
                        targetDay = goal.day.toLong(),
                        monthlyContribution = goal.monthlyContribution,
                        isMain = goal.isMain,
                        allocation = goal.allocation.toGoalAllocation(),
                        status = goal.status.toGoalStatus(),
                        sortOrder = goal.order.toLong(),
                        estimatedYear = null,
                        estimatedMonth = null,
                        estimatedDay = null,
                        updatedAt = clock.nowEpochMillis(),
                    )
                }

                // Atamalar hedeflerden ve pozisyonlardan SONRA: yabanci anahtar.
                file.goalAssets.forEach { assignment ->
                    goalAssetQueries.assignPositionToGoal(
                        positionId = assignment.positionId,
                        goalId = assignment.goalId,
                        quantity = assignment.quantity,
                        updatedAt = clock.nowEpochMillis(),
                    )
                }

                file.snapshots.forEach { snapshot ->
                    snapshotQueries.upsertSnapshot(
                        dateYear = snapshot.year.toLong(),
                        dateMonth = snapshot.month.toLong(),
                        dateDay = snapshot.day.toLong(),
                        totalValue = snapshot.totalValue,
                        principal = snapshot.principal,
                        updatedAt = clock.nowEpochMillis(),
                    )
                }

                file.settings.forEach { (key, value) ->
                    // CIHAZA AIT tercihler geri yuklenmez. Yedek, dosyayi ALAN
                    // cihazin verisidir: Volkan'in yedegi Ayse'nin telefonuna
                    // yuklendiginde o telefon Volkan'in profili olmamali. Onun
                    // hangi profil oldugu kendi kararidir, yedekle gelmez.
                    if (key !in DeviceOnlySettings) {
                        settingQueries.upsertSetting(settingKey = key, settingValue = value)
                    }
                }
            }
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
                updatedAt = clock.nowEpochMillis(),
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
            updatedAt = clock.nowEpochMillis(),
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
                    updatedAt = clock.nowEpochMillis(),
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
                    updatedAt = clock.nowEpochMillis(),
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
                transactionQueries.deleteTransactionsByPosition(deletedAt = clock.nowEpochMillis(), positionId = positionId)
                positionQueries.deletePositionById(deletedAt = clock.nowEpochMillis(), id = positionId)
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
                updatedAt = clock.nowEpochMillis(),
            )
        }
    }

    override suspend fun deleteGoal(goalId: String) {
        withContext(dispatcher) {
            goalQueries.deleteGoalById(deletedAt = clock.nowEpochMillis(), id = goalId)
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
                val now = clock.nowEpochMillis()
                orderedIds.forEachIndexed { index, goalId ->
                    goalQueries.updateGoalOrder(sortOrder = index.toLong(), updatedAt = now, id = goalId)
                }
            }
        }
    }
}

/** Acilis akisinin gecildigini isaretleyen ayar anahtari. */
private const val OnboardedKey = "onboarded"

/**
 * Geri yuklemede ATLANACAK tercihler.
 *
 * Bunlar veriye degil CIHAZA aittir: yedek dosyasi tercihler tablosunu oldugu
 * gibi tasidigi icin, atlanmasalar Volkan'in yedegini yukleyen Ayse'nin telefonu
 * kendini Volkan'in profili sanardi.
 */
private val DeviceOnlySettings = setOf(
    PreferenceKeys.ActiveMemberId,
    // Push watermark'i cihaza ait: geri yukleme onu sifirlamamali, yoksa restore
    // sonrasi butun defter yeniden itilir (zararsiz ama gereksiz trafik).
    PreferenceKeys.LastPushedAt,
)

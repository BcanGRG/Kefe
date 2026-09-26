package com.kefe.app.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * AYLIK YATIRIM PLANI.
 *
 * Saklanan YALNIZ kullanicinin niyetidir: "Ekim'de 10 gr gram altin, 3.000 TL
 * AFA". Neyin yapildigi, neyin eksik kaldigi, plan disi alimlar ve ayin skoru
 * SAKLANMAZ - her seferinde islem defterinden turetilir. Saklansaydi bir islem
 * duzenlendiginde, silindiginde ya da geriye tarihlenip eklendiginde bayatlardi
 * ve tazelemek icin bir arka plan isi gerekirdi ([isOverdue] ile ayni gerekce).
 *
 * Esleme VARLIK + AY uzerinden yapilir, islemin hedef alanindan (goalId)
 * degil: o alan "bu alim su hedef icindi" demiyor - hedefsiz bir alim bile
 * "tum varlik" atamasini dondururken oraya bir hedef yazabiliyor.
 */

enum class PlanTargetMode {
    /** Hedef varligin kendi biriminde: gram, adet, dolar. */
    Quantity,

    /** Hedef TL: "3.000 TL AFA". */
    Amount,
}

/**
 * Varsayilan hedef birimi. Altin, gumus ve dovizde insanlar miktarla dusunur
 * ("10 gram", "500 dolar"); fonun pay adedi anlamsizdir, hisse ve nakitte de
 * tutar dogal olandir.
 */
fun defaultPlanMode(assetClass: AssetClass): PlanTargetMode = when (assetClass) {
    AssetClass.Gold, AssetClass.Silver, AssetClass.Fx -> PlanTargetMode.Quantity
    AssetClass.Fund, AssetClass.Stock, AssetClass.Cash -> PlanTargetMode.Amount
}

/** Bir ayin tek plan satiri. Bir ayda bir varlik icin EN FAZLA bir satir vardir. */
data class PlanItem(
    val id: String,
    val month: YearMonth,
    val assetKey: String,
    /** Planlandigi andaki ad - henuz alinmamis bir fonun baska bir adi yok. */
    val assetName: String,
    val mode: PlanTargetMode,
    /** [mode] Quantity ise varligin biriminde, Amount ise TL. */
    val target: Double,
    /** Yumusak bag: bu satir icin yapilan alim bu hedefe sayilir. */
    val goalId: String? = null,
    /**
     * Kaydedildigi andaki ALIS birim fiyati. Miktar satirinin TL agirligini
     * sabitler: gecmis bir ayin skoru bugunku fiyatla oynamasin.
     */
    val unitPriceAtPlan: Double? = null,
) {
    val unit: QuantityUnit? get() = parseAssetKey(assetKey)?.unit
    val assetClass: AssetClass? get() = parseAssetKey(assetKey)?.assetClass
}

enum class PlanItemStatus {
    /** Gelecek ay - henuz bir sey beklenmiyor. */
    Upcoming,

    /** Bu ay, henuz hic alinmadi. */
    Waiting,
    Partial,
    Done,

    /** Ay bitti, hic alinmadi. */
    Missed,
}

data class PlanItemProgress(
    val item: PlanItem,
    val boughtQuantity: Double,
    /** Alimlarin TL tutari - komisyon DAHIL (cepten cikan). */
    val boughtTl: Double,
    val soldQuantity: Double,
    val soldTl: Double,
    /** Hedefle ayni birimde gerceklesen: Quantity'de miktar, Amount'ta TL. */
    val actual: Double,
    /** 0..1 - asim sayilmaz, [over] ayrica gosterilir. */
    val ratio: Double,
    val remaining: Double,
    val over: Double,
    /** Satirin TL agirligi; fiyat hic bilinmiyorsa null. */
    val plannedTl: Double?,
    val status: PlanItemStatus,
) {
    val isDone: Boolean get() = status == PlanItemStatus.Done
}

enum class ExtraKind {
    /** Planda olmayan bir varlik alindi. */
    Unplanned,

    /** Plandaki varliktan hedefin USTUNDE alindi. */
    OverPlan,
}

data class ExtraPurchase(
    val assetKey: String,
    val name: String,
    val kind: ExtraKind,
    /** Varligin biriminde; tutar satirinda asim TL oldugu icin null. */
    val quantity: Double?,
    val unit: QuantityUnit?,
    val tl: Double,
)

enum class MonthVerdict {
    NoPlan,
    Future,

    /** Bu ay, esik henuz asilmadi. Seriyi BOZMAZ. */
    InProgress,

    /** Esik (%80) asildi ama tamami degil - "duzenli" sayilir. */
    Partial,
    Full,
    Missed,
}

/** Ayin "duzenli" sayilmasi icin agirlikli skorun esigi. */
const val RegularMonthThreshold: Double = 0.80

data class MonthPlanProgress(
    val month: YearMonth,
    val items: List<PlanItemProgress>,
    val extras: List<ExtraPurchase>,
    /** 0..1, TL agirlikli; satir yoksa null. */
    val score: Double?,
    val plannedTl: Double,
    val extrasTl: Double,
    /** Ayin toplam satis hasilati - net yeni parayi okumak icin. */
    val soldTl: Double,
    val verdict: MonthVerdict,
) {
    val doneCount: Int get() = items.count { it.isDone }

    /** Seriye sayilir mi. */
    val isRegular: Boolean get() = verdict == MonthVerdict.Full || verdict == MonthVerdict.Partial
}

/** Bir varligin bir aydaki alim/satim toplami. */
internal data class AssetFlow(
    val buyQuantity: Double = 0.0,
    val buyTl: Double = 0.0,
    /** Birim fiyat x miktar - komisyonsuz; ortalama fiyat icin. */
    val buyGross: Double = 0.0,
    val sellQuantity: Double = 0.0,
    val sellTl: Double = 0.0,
) {
    val averageBuyPrice: Double? get() = if (buyQuantity > 0.0) buyGross / buyQuantity else null
}

/**
 * Ayin islemlerini varlik anahtarina gore toplar.
 *
 * [positions] SATILIP SIFIRLANANLARI DA icermeli: yalniz eldekiler verilirse
 * gecmis bir ayda alinip sonra tamamen satilan varligin o ayki alimi kaybolur
 * ve plan "hic alinmadi" der.
 */
internal fun assetFlowsIn(
    month: YearMonth,
    transactions: List<Transaction>,
    positions: List<Position>,
): Map<String, AssetFlow> {
    val keyOf = positions.associate { it.id to it.assetKey() }
    val flows = mutableMapOf<String, AssetFlow>()
    transactions.forEach { tx ->
        if (tx.date !in month) return@forEach
        val key = keyOf[tx.positionId] ?: return@forEach
        val f = flows[key] ?: AssetFlow()
        flows[key] = when (tx.side) {
            TradeSide.Buy -> f.copy(
                buyQuantity = f.buyQuantity + tx.quantity,
                buyTl = f.buyTl + tx.total,
                buyGross = f.buyGross + tx.quantity * tx.unitPrice,
            )

            TradeSide.Sell -> f.copy(
                sellQuantity = f.sellQuantity + tx.quantity,
                sellTl = f.sellTl + tx.total,
            )
        }
    }
    return flows
}

private const val Tolerance = 1e-6

private fun Double.isZero(): Boolean = abs(this) <= Tolerance

/**
 * Bir ayin planinin durumu.
 *
 * KURALLAR
 * - Olcu BRUT ALIMDIR. Satis planin yapilma oranini dusurmez: "bu ay 10 gr
 *   alacaktim, aldim mi" sorusunun cevabi ayni ay 1 gr satmakla degismez.
 *   Satislar ayrica gosterilir ve para akisinda net yeni parayi azaltir.
 * - Miktar satiri miktarla, tutar satiri komisyon DAHIL TL ile olculur (cepten
 *   cikan para - "Bu ay eklenen" ile ayni tanim).
 * - Her satir en fazla %100 sayilir; asim "plan disi" bolumunde gorunur.
 * - Skor TL agirliklidir: 10 gr altinin (~67 bin TL) %50'si ile 500 TL fonun
 *   %100'u ayni agirlikta olamaz.
 * - Ay CIHAZ gunune gore belirlenir (bkz. SummaryViewModel: piyasa gunu ay
 *   donumunde bir katkiyi iki aydan da dusuruyordu).
 */
fun monthPlanProgress(
    month: YearMonth,
    items: List<PlanItem>,
    transactions: List<Transaction>,
    /** Silinmemis BUTUN pozisyonlar - satilip sifirlananlar dahil. */
    positions: List<Position>,
    today: KefeDate,
): MonthPlanProgress = monthPlanProgressOf(
    month = month,
    items = items.filter { it.month == month },
    flows = assetFlowsIn(month, transactions, positions),
    positions = positions,
    today = today,
)

internal fun monthPlanProgressOf(
    month: YearMonth,
    items: List<PlanItem>,
    flows: Map<String, AssetFlow>,
    positions: List<Position>,
    today: KefeDate,
): MonthPlanProgress {
    val current = YearMonth.of(today)
    val isFuture = month > current
    val isCurrent = month == current

    val progress = items.map { item ->
        val flow = flows[item.assetKey] ?: AssetFlow()
        val actual = when (item.mode) {
            PlanTargetMode.Quantity -> flow.buyQuantity
            PlanTargetMode.Amount -> flow.buyTl
        }
        val ratio = when {
            item.target <= 0.0 -> 1.0
            item.target - actual <= Tolerance -> 1.0
            else -> (actual / item.target).coerceIn(0.0, 1.0)
        }
        val status = when {
            isFuture -> PlanItemStatus.Upcoming
            ratio >= 1.0 -> PlanItemStatus.Done
            actual.isZero() -> if (isCurrent) PlanItemStatus.Waiting else PlanItemStatus.Missed
            else -> PlanItemStatus.Partial
        }
        PlanItemProgress(
            item = item,
            boughtQuantity = flow.buyQuantity,
            boughtTl = flow.buyTl,
            soldQuantity = flow.sellQuantity,
            soldTl = flow.sellTl,
            actual = actual,
            ratio = ratio,
            remaining = max(0.0, item.target - actual).takeUnless { it <= Tolerance } ?: 0.0,
            over = max(0.0, actual - item.target).takeUnless { it <= Tolerance } ?: 0.0,
            plannedTl = plannedTlOf(item, positions, flow),
            status = status,
        )
    }

    val score = weightedScore(progress)
    val planned = progress.mapNotNull { it.plannedTl }.sum()

    // --- Plan disi -----------------------------------------------------------
    val plannedKeys = items.map { it.assetKey }.toSet()
    val nameOf = positions
        .groupBy { it.assetKey() }
        .mapValues { (_, list) -> list.maxByOrNull { it.value }?.name }
    val extras = buildList {
        flows.forEach { (key, flow) ->
            if (key in plannedKeys || flow.buyQuantity.isZero()) return@forEach
            add(
                ExtraPurchase(
                    assetKey = key,
                    name = nameOf[key] ?: catalogName(key),
                    kind = ExtraKind.Unplanned,
                    quantity = flow.buyQuantity,
                    unit = parseAssetKey(key)?.unit,
                    tl = flow.buyTl,
                ),
            )
        }
        progress.filter { it.over > 0.0 }.forEach { p ->
            val flow = flows[p.item.assetKey] ?: AssetFlow()
            val (quantity, tl) = when (p.item.mode) {
                PlanTargetMode.Quantity -> p.over to p.over * (flow.averageBuyPrice ?: 0.0)
                PlanTargetMode.Amount -> null to p.over
            }
            add(
                ExtraPurchase(
                    assetKey = p.item.assetKey,
                    name = p.item.assetName,
                    kind = ExtraKind.OverPlan,
                    quantity = quantity,
                    unit = p.item.unit,
                    tl = tl,
                ),
            )
        }
    }.sortedByDescending { it.tl }

    val verdict = when {
        items.isEmpty() -> MonthVerdict.NoPlan
        isFuture -> MonthVerdict.Future
        score != null && score >= 1.0 - Tolerance -> MonthVerdict.Full
        score != null && score >= RegularMonthThreshold - Tolerance -> MonthVerdict.Partial
        isCurrent -> MonthVerdict.InProgress
        else -> MonthVerdict.Missed
    }

    return MonthPlanProgress(
        month = month,
        items = progress,
        extras = extras,
        score = score,
        plannedTl = planned,
        extrasTl = extras.sumOf { it.tl },
        soldTl = flows.values.sumOf { it.sellTl },
        verdict = verdict,
    )
}

/**
 * Satirin TL agirligi.
 *
 * Tutar satirinda hedefin kendisi. Miktar satirinda hedef x fiyat; fiyat su
 * siradan aranir:
 *   1. Kayittaki anlik goruntu - gecmis ay bugunku fiyatla oynamasin.
 *   2. Eldeki varligin guncel fiyati.
 *   3. O ay odenen ortalama fiyat.
 * Hicbiri yoksa null: agirlik UYDURULMAZ, [weightedScore] bunu ayrica ele alir.
 */
private fun plannedTlOf(item: PlanItem, positions: List<Position>, flow: AssetFlow): Double? {
    if (item.mode == PlanTargetMode.Amount) return item.target
    val price = item.unitPriceAtPlan?.takeIf { it > 0.0 }
        ?: currentUnitPrice(item.assetKey, positions)
        ?: flow.averageBuyPrice
        ?: return null
    return item.target * price
}

/** Anahtarin eldeki en buyuk pozisyonundaki birim fiyat. */
fun currentUnitPrice(assetKey: String, positions: List<Position>): Double? =
    positions
        .filter { it.quantity > 0.0 && it.unitPrice > 0.0 && it.assetKey() == assetKey }
        .maxByOrNull { it.value }
        ?.unitPrice

/**
 * TL agirlikli skor.
 *
 * Agirligi bilinmeyen satir (fiyati hic bulunamayan miktar satiri) bilinen
 * agirliklarin ORTALAMASINI alir; hicbiri bilinmiyorsa butun satirlar esit
 * sayilir. Sifir agirlik vermek o satiri skordan silerdi - yapilmamis bir
 * satir ayi "tam" gosterebilirdi.
 */
private fun weightedScore(progress: List<PlanItemProgress>): Double? {
    if (progress.isEmpty()) return null
    val known = progress.mapNotNull { it.plannedTl?.takeIf { w -> w > 0.0 } }
    val fallback = if (known.isEmpty()) 1.0 else known.average()
    var sum = 0.0
    var weights = 0.0
    progress.forEach { p ->
        val w = p.plannedTl?.takeIf { it > 0.0 } ?: fallback
        sum += w * p.ratio
        weights += w
    }
    return if (weights <= 0.0) null else min(1.0, sum / weights)
}

/**
 * "Al" dugmesinin islem sayfasina onerecegi miktar; null ise miktar
 * kullaniciya birakilir.
 *
 * Tutar satirinda fon ya da hisse icin pay adedi uydurulmaz - fiyat islem
 * aninda belli olur. Nakitte miktar zaten TL'dir.
 */
fun PlanItemProgress.prefillQuantity(): Double? {
    if (remaining <= 0.0) return null
    return when (item.mode) {
        PlanTargetMode.Quantity -> remaining
        PlanTargetMode.Amount -> remaining.takeIf { item.assetClass == AssetClass.Cash }
    }
}

// --- Hedef onsecimi ------------------------------------------------------------

data class PlanGoalSelection(
    /** Secicide isaretlenecek hedef. */
    val selectedGoalId: String?,
    /** Plan baska bir hedef diyor ama varlik su hedefte - ekran uyarmali. */
    val conflictGoalId: String?,
)

/**
 * Islem sayfasinda hangi hedefin onsecili gelecegi.
 *
 * PLAN MEVCUT ATAMAYI ASLA EZMEZ. Varlik baska bir hedefe atanmisken planin
 * hedefini secmek varligi TASIR ve eski hedef yalniz bu alimin miktarini degil
 * butun atamasini kaybeder (bkz. goalAssignmentChange). Bu, kullanicinin
 * bilerek yapmasi gereken bir karar; plan onu sessizce veremez. Onsecim
 * yalniz varligin hic atamasi yoksa, yeni bir ALIMDA ve hedef hala varsa olur.
 */
fun planGoalSelection(
    currentGoalId: String?,
    planGoalId: String?,
    isBuy: Boolean,
    isEditing: Boolean,
    knownGoalIds: Set<String>,
): PlanGoalSelection {
    val plan = planGoalId?.takeIf { it in knownGoalIds }
    if (!isBuy || isEditing || plan == null) return PlanGoalSelection(currentGoalId, null)
    return when (currentGoalId) {
        null -> PlanGoalSelection(plan, null)
        plan -> PlanGoalSelection(plan, null)
        else -> PlanGoalSelection(currentGoalId, conflictGoalId = currentGoalId)
    }
}

// --- Varlik secenekleri ----------------------------------------------------------

data class PlanAssetOption(
    val assetKey: String,
    val name: String,
    val assetClass: AssetClass,
    val unit: QuantityUnit,
    val held: Boolean,
)

/**
 * Plan satirina secilebilecek varliklar: once eldekiler (degere gore), sonra
 * henuz alinmamis ama katalogda olan formlar. Fon ve hisse katalogda yok -
 * onlar koduyla girilir.
 */
fun planAssetOptions(positions: List<Position>): List<PlanAssetOption> {
    val held = positions
        .filter { it.quantity > 0.0 }
        .mapNotNull { p -> p.assetKey()?.let { it to p } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (key, list) ->
            val info = parseAssetKey(key) ?: return@mapNotNull null
            val top = list.maxBy { it.value }
            PlanAssetOption(key, top.name, info.assetClass, info.unit, held = true) to list.sumOf { it.value }
        }
        .sortedByDescending { it.second }
        .map { it.first }
    val heldKeys = held.map { it.assetKey }.toSet()
    val catalog = PlanCatalogKeys
        .filter { it !in heldKeys }
        .mapNotNull { key ->
            parseAssetKey(key)?.let { PlanAssetOption(key, catalogName(key), it.assetClass, it.unit, held = false) }
        }
    return held + catalog
}

private val PlanCatalogKeys: List<String> = buildList {
    add(goldAssetKey(GoldSubtype.Gram, Karat.K24))
    add(goldAssetKey(GoldSubtype.Gram, Karat.K22))
    add(goldAssetKey(GoldSubtype.Quarter, Karat.K22))
    add(goldAssetKey(GoldSubtype.Half, Karat.K22))
    add(goldAssetKey(GoldSubtype.Full, Karat.K22))
    add(goldAssetKey(GoldSubtype.Ata, Karat.K22))
    add(goldAssetKey(GoldSubtype.Bullion, Karat.K24))
    add(goldAssetKey(GoldSubtype.Jewelry, Karat.K22))
    add(SilverAssetKey)
    Currency.entries.forEach { add(it.priceKey()) }
    add(CashAssetKey)
}

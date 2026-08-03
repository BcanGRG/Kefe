package com.kefe.app.domain.model

import kotlin.math.min

/**
 * Bir varligin bir hedefe atanmis kismi.
 *
 * [quantity] < 0 ise TUM VARLIK sayilir - eski (miktarsiz) atamalarin ve hedef
 * detayindaki "Varlik sec" listesinin anlami budur: "bu varligin tamami bu
 * hedefi karsilar". Sifir ya da uzeri ise o kadar adet/gram sayilir.
 */
data class GoalAssignment(
    val goalId: String,
    val quantity: Double = WholePosition,
) {
    val isWhole: Boolean get() = quantity < 0.0

    companion object {
        /** "Tum varlik" isareti. Negatif secildi: gecerli bir miktar olamaz. */
        const val WholePosition: Double = -1.0
    }
}

/**
 * Bu atamanin BUGUN kac birime karsilik geldigi.
 *
 * POZISYONUN MIKTARIYLA SINIRLIDIR: 10 ceyrek atanmisken 6 tanesi satilirsa
 * hedef 6 sayar. Kirpmayi okuma aninda yapmak, satisin hangi hedeften
 * dusuruleceGine dair ikinci bir hesap defteri tutmaktan kurtarir.
 */
fun GoalAssignment.effectiveQuantity(position: Position): Double =
    if (isWhole) position.quantity else min(quantity, position.quantity).coerceAtLeast(0.0)

/**
 * Atanan kismin bugunku TL degeri.
 *
 * Oransal: varligin degeri zaten miktar x birim fiyat, atanan kisim da o
 * degerin ayni oranidir. Birim fiyati burada yeniden carpmak, elle fiyatlanan
 * varliklarda (deger baska yerden geliyor) iki farkli rakam uretirdi.
 */
fun GoalAssignment.valueIn(position: Position): Double {
    if (position.quantity <= 0.0) return 0.0
    return position.value * (effectiveQuantity(position) / position.quantity)
}

/**
 * Bir hedefi hangi varliklarin karsiladigi = YALNIZ o hedefe atanan varliklar.
 *
 * Kati atama: atama yoksa 0 doner, hedef %0 kalir - kullanici varlik atayana
 * kadar dolmaz. (Once "atama yoksa tum birikim sayilir" fallback'i vardi; ama
 * o zaman hedefsiz eklenen bir varlik atama yapilmamis her hedefin
 * "karsilayanlar" listesinde beliriyordu ve karmasa yaratiyordu. Kullanici bu
 * yuzden kati atamayi secti.)
 *
 * Atama artik MIKTAR tasir: varligin tamami degil, atanan kadari sayilir.
 */
fun goalWealth(
    goal: Goal,
    positions: List<Position>,
    /** positionId -> atama. */
    assignments: Map<String, GoalAssignment>,
): Double = positions.sumOf { position ->
    val assignment = assignments[position.id]
    if (assignment?.goalId != goal.id) 0.0 else assignment.valueIn(position)
}

/**
 * Bir hedefi karsilayan varligin O HEDEFE DUSEN kismi.
 *
 * Ekranlarin `Position`i dogrudan gostermesi, atama miktar tasimaya baslayinca
 * YALAN soyler oldu: hedefe 15 ceyrek atanmisken liste "16 adet" ve varligin
 * tam degerini yaziyordu. Neyin sayildigini tasiyan tek tip burasi.
 */
data class GoalAsset(
    val position: Position,
    val assignment: GoalAssignment,
) {
    /** Hedefe sayilan miktar - pozisyonunkiyle sinirli. */
    val quantity: Double get() = assignment.effectiveQuantity(position)

    /** Hedefe sayilan TL degeri. */
    val value: Double get() = assignment.valueIn(position)

    /**
     * Hedefe sayilan kismin MALIYETI - [value] ile ayni oranda.
     *
     * Hedefe ozel getiri ancak bununla hesaplanabilir: 16 ceyregin 15'i bu
     * hedefteyse maliyet de 15/16'sidir. Tam maliyeti kullanmak, hedefin
     * getirisini oldugundan dusuk gosterirdi.
     *
     * Ortalama maliyet varsayimi: hangi ceyregin hedefe sayildigi bilinmiyor
     * ve bilinemez - atama miktar tutuyor, belli bir islemi degil.
     */
    val cost: Double
        get() = if (position.quantity <= 0.0) {
            0.0
        } else {
            position.cost * (quantity / position.quantity)
        }

    /** Hedefe dusen kismin kar/zarari. */
    val profit: Double get() = value - cost

    /**
     * Varligin tamami mi sayiliyor. Kismi ise ekran bunu acikca yazmali;
     * "tamami" ise miktar tekrar edilmemeli.
     */
    val coversWholePosition: Boolean
        get() = assignment.isWhole || quantity >= position.quantity
}

/** Hedefe atanmis varliklar - detay ekranindaki liste. */
fun assetsOf(
    goal: Goal,
    positions: List<Position>,
    assignments: Map<String, GoalAssignment>,
): List<GoalAsset> = positions.mapNotNull { position ->
    val assignment = assignments[position.id]?.takeIf { it.goalId == goal.id }
    assignment?.let { GoalAsset(position, it) }
}

/**
 * Islem kaydedilirken atamanin ALACAGI yeni miktar; `null` ise atamaya
 * DOKUNULMAZ.
 *
 * Hedef secicisi YALNIZ BU ISLEMIN nereye sayilacagini soyler.
 *
 * [selectedGoalId] doluyken:
 *   - Ayni hedef  -> miktar bu islem kadar artar (satista azalir).
 *   - "Tum varlik" atamasi ayni hedefte KORUNUR: kullanici bir kez "tamami bu
 *     hedefe" demisse sonraki alimlar da oraya sayilmali.
 *   - Baska hedef (ya da ilk atama) -> miktar bu islemin miktari olur; varlik
 *     o hedefe TASINIR.
 *
 * "HEDEFSIZ" (null) VE "TUM VARLIK" ATAMASI. Burasi ilk surumde eksikti ve
 * duzeltme sahada tutmadi: eski atamalarin hepsi -1 (tum varlik) oldugu icin
 * "Hedefsiz" hicbir sey ifade etmiyordu - hedef "hepsi" dedigi surece yeni
 * alinan da sayiliyordu. Kullanici 15 ceyregi Ev'deyken 1 tane hedefsiz
 * ekliyor, hedef 16 gosteriyordu.
 *
 * Cozum: hedefsiz bir ALIM, "tum varlik" atamasini o ana kadarki miktara
 * SABITLER ([quantityBefore]). "Tamami bu hedefe" sozu bugune kadar alinanlar
 * icindir; kullanici yeni alimin disarida kalmasini acikca istediginde o soz
 * dondurulur. Miktari zaten belli olan atamalara dokunulmaz - onlarda yeni alim
 * nasilsa sayilmiyor.
 */
fun nextAssignedQuantity(
    current: GoalAssignment?,
    selectedGoalId: String?,
    transactionQuantity: Double,
    isSell: Boolean,
    /** Pozisyonun BU ISLEMDEN ONCEKI miktari. */
    quantityBefore: Double,
): Double? = when {
    // Hedefsiz: yalniz "tum varlik" atamasini dondurmak icin mudahale edilir.
    selectedGoalId == null ->
        if (current != null && current.isWhole && !isSell) {
            quantityBefore.coerceAtLeast(0.0)
        } else {
            null
        }

    current == null || current.goalId != selectedGoalId ->
        transactionQuantity.coerceAtLeast(0.0)

    current.isWhole -> GoalAssignment.WholePosition

    else -> {
        val delta = if (isSell) -transactionQuantity else transactionQuantity
        (current.quantity + delta).coerceAtLeast(0.0)
    }
}

/**
 * Bir varligin baska hedefe atanip atanmadigi - secici bunu gostermeli.
 *
 * Atama tekildir: baska hedefe atanmis bir varligi secmek onu TASIR. Kullanici
 * bunu okumadan yapmamali, yoksa bir hedefin ilerlemesi sessizce duser.
 */
fun otherGoalOf(
    positionId: String,
    goal: Goal,
    assignments: Map<String, GoalAssignment>,
): String? = assignments[positionId]?.goalId?.takeIf { it != goal.id }

// --- Hedef bazli rakamlar ----------------------------------------------------

/**
 * Bu hedefin BUGUNKU degisimi - atanan kisimlarla.
 *
 * Portfoy geneliyle ayni mantik ([weightedPeriodTotal]): yuzdeler ortalanmaz,
 * her kalem gun basindaki degerine geri cozulur ve TL farklar toplanir. Aksi
 * halde hedefteki kucuk bir fonun %10'u, buyuk altinin %1'iyle ayni agirligi
 * tasirdi.
 *
 * Yuzde SIFIR olabilir ve bu dogrudur: hafta sonu hicbir piyasa oynamaz
 * (bkz. [Price.todayChangePercent]).
 */
fun List<GoalAsset>.todayChange(): PeriodTotal? =
    weightedPeriodTotal(map { it.value to it.position.dailyChangePercent })

/**
 * Bu hedefin TOPLAM getirisi - atanan kisimlarin kar/zarari.
 *
 * Payda MALIYETTIR, guncel deger degil: "koydugum paraya gore ne kazandim"
 * sorusu bu. Ozet ekranindaki toplam getiriyle ayni tanim, boylece iki ekran
 * birbiriyle celismez.
 *
 * Maliyeti sifir olan hedef (hepsi elle fiyatli, defteri olmayan varlik) null
 * doner - sifira bolmek yerine ekran "—" yazar.
 */
fun List<GoalAsset>.totalReturn(): PeriodTotal? {
    val cost = sumOf { it.cost }
    if (cost <= 0.0) return null
    val profit = sumOf { it.profit }
    return PeriodTotal(amount = profit, percent = profit / cost * 100.0)
}

/**
 * Hedefe atanan kisimlarin varlik sinifi dagilimi.
 *
 * [List<Position>.allocation] ile ayni cikti, farkli girdi: orada pozisyonun
 * TAMAMI sayilir, burada yalniz hedefe dusen kismi. Ikisini tek fonksiyona
 * sigdirmak, "deger" kelimesinin iki ayri sey demesine yol acardi.
 */
fun List<GoalAsset>.allocation(): List<AllocationSlice> {
    val total = sumOf { it.value }
    if (total <= 0.0) return emptyList()

    val sums = LinkedHashMap<AssetClass, Double>()
    for (asset in this) {
        val key = asset.position.assetClass
        sums[key] = (sums[key] ?: 0.0) + asset.value
    }
    return sums.entries
        .sortedByDescending { it.value }
        .map { AllocationSlice(it.key, it.value, it.value / total * 100.0) }
}

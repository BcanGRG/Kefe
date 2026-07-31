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

/** Hedefe atanmis varliklar - detay ekranindaki liste. */
fun assetsOf(
    goal: Goal,
    positions: List<Position>,
    assignments: Map<String, GoalAssignment>,
): List<Position> = positions.filter { assignments[it.id]?.goalId == goal.id }

/**
 * Islem kaydedilirken atamanin ALACAGI yeni miktar.
 *
 * Hedef secicisi YALNIZ BU ISLEMIN nereye sayilacagini soyler:
 *   - Ayni hedef  -> miktar bu islem kadar artar (satista azalir).
 *   - "Tum varlik" atamasi ayni hedefte KORUNUR: kullanici bir kez "tamami bu
 *     hedefe" demisse sonraki alimlar da oraya sayilmali.
 *   - Baska hedef (ya da ilk atama) -> miktar bu islemin miktari olur; varlik
 *     o hedefe TASINIR.
 *
 * "Hedefsiz" buraya hic gelmez - o durumda atamaya DOKUNULMAZ. Once secici tum
 * varligin atamasini siliyordu: Ev'de 10 ceyrek varken 1 tane hedefsiz eklemek
 * 11'i birden Ev'den cikariyordu.
 */
fun nextAssignedQuantity(
    current: GoalAssignment?,
    selectedGoalId: String,
    transactionQuantity: Double,
    isSell: Boolean,
): Double = when {
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

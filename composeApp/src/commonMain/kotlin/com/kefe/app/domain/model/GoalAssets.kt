package com.kefe.app.domain.model

/**
 * Bir hedefi hangi varliklarin karsiladigi.
 *
 * Ilerlemeyi olcen kural TEK cumleyle: hedefe varlik atanmissa yalniz onlar
 * sayilir, atanmamissa tum birikim sayilir.
 *
 * Ikinci yarisi onemli: atama isteyen bir kullanicinin yaninda atamayla
 * ugrasmak istemeyen biri var (ayni portfoyu paylasiyorlar). Atama zorunlu
 * olsaydi hicbir sey atamamis kisi butun hedeflerini bir anda %0'a duserdi.
 */
fun goalWealth(
    goal: Goal,
    positions: List<Position>,
    /** positionId -> goalId. */
    assignments: Map<String, String>,
): Double {
    val assigned = positions.filter { assignments[it.id] == goal.id }
    if (assigned.isEmpty() && assignments.none { it.value == goal.id }) {
        // Bu hedefe hic atama yok: tum birikim sayilir (eski davranis).
        return positions.sumOf { it.value }
    }
    return assigned.sumOf { it.value }
}

/** Hedefe atanmis varliklar - detay ekranindaki liste. */
fun assetsOf(
    goal: Goal,
    positions: List<Position>,
    assignments: Map<String, String>,
): List<Position> = positions.filter { assignments[it.id] == goal.id }

/**
 * Bir varligin baska hedefe atanip atanmadigi - secici bunu gostermeli.
 *
 * Atama tekildir: baska hedefe atanmis bir varligi secmek onu TASIR. Kullanici
 * bunu okumadan yapmamali, yoksa bir hedefin ilerlemesi sessizce duser.
 */
fun otherGoalOf(
    positionId: String,
    goal: Goal,
    assignments: Map<String, String>,
): String? = assignments[positionId]?.takeIf { it != goal.id }

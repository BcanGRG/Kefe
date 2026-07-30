package com.kefe.app.domain.model

/**
 * Bir hedefi hangi varliklarin karsiladigi = YALNIZ o hedefe atanan varliklar.
 *
 * Kati atama: atama yoksa 0 doner, hedef %0 kalir - kullanici varlik atayana
 * kadar dolmaz. (Once "atama yoksa tum birikim sayilir" fallback'i vardi; ama
 * o zaman hedefsiz eklenen bir varlik atama yapilmamis her hedefin
 * "karsilayanlar" listesinde beliriyordu ve karmasa yaratiyordu. Kullanici bu
 * yuzden kati atamayi secti.)
 */
fun goalWealth(
    goal: Goal,
    positions: List<Position>,
    /** positionId -> goalId. */
    assignments: Map<String, String>,
): Double = positions.filter { assignments[it.id] == goal.id }.sumOf { it.value }

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

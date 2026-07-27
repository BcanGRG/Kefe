package com.kefe.app.domain.model

data class Goal(
    val id: String,
    val name: String,
    val iconKey: String,
    val amount: Double,
    val unit: GoalUnit,
    val targetDate: KefeDate,
    val monthlyContribution: Double,
    val isMain: Boolean = false,
    val allocation: GoalAllocation = GoalAllocation.AllWealth,
    val status: GoalStatus = GoalStatus.Active,
    val order: Int = 0,
    val estimatedArrival: KefeDate? = null,
)

enum class GoalUnit {
    Try,
    GoldGram,
    Usd;

    fun label(): String = when (this) {
        Try -> "TL sabit"
        GoldGram -> "Gram altın"
        Usd -> "USD"
    }
}

enum class GoalAllocation {
    AllWealth,
    FixedShare,
}

enum class GoalStatus {
    Active,
    Completed,
    Overdue,
}

/** Ilerleme %200'de kirpilir - asilan hedeflerde cubuk tasmasin. */
fun Goal.progress(currentWealth: Double): Float =
    if (amount <= 0.0) 0f else (currentWealth / amount).coerceIn(0.0, 2.0).toFloat()

/** Hedef yolundaki yuzde duraklari (%25, %50, %75, %100). */
data class GoalMilestone(
    val percent: Int,
    val amount: Double,
    val label: String,
    val reached: Boolean,
)

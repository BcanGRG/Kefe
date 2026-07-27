package com.kefe.app.domain.model

data class ActivityEvent(
    val id: String,
    val memberId: String,
    val kind: ActivityKind,
    val description: String,
    val amount: Double? = null,
    val timeLabel: String,
    val dayGroup: String,
    val isManualPrice: Boolean = false,
)

enum class ActivityKind {
    AddTransaction,
    SellTransaction,
    ManualPrice,
    GoalUpdate,
    ExcludeFromGoals,
}

package com.kefe.app.domain.model

/** Gunluk servet fotografi - zaman serisi grafiklerinin kaynagi. */
data class DailySnapshot(
    val date: KefeDate,
    val totalValue: Double,
    val principal: Double,
)

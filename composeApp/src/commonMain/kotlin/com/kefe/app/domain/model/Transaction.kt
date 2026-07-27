package com.kefe.app.domain.model

data class Transaction(
    val id: String,
    val positionId: String,
    val date: KefeDate,
    val side: TradeSide,
    val quantity: Double,
    val unitPrice: Double,
    val fee: Double = 0.0,
    val note: String? = null,
    val storage: String? = null,
    val addedByMemberId: String,
    val syncState: SyncState = SyncState.Synced,
) {
    val total: Double get() = quantity * unitPrice + fee
}

enum class TradeSide {
    Buy,
    Sell;

    fun label(): String = when (this) {
        Buy -> "ALIŞ"
        Sell -> "SATIŞ"
    }
}

enum class SyncState {
    Synced,
    Pending,
}

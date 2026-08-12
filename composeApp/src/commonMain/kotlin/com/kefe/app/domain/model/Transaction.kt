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
    /**
     * Kaydin OLUSTURULMA damgasi - ayni gune dusen islemler arasindaki sirayi
     * bu belirler (bkz. 8.sqm).
     *
     * Once bu is SQLite'in rowid'ine birakilmisti; duzenleme, esitleme ve
     * yedekten geri yukleme satiri yerinden oynatinca sira bozuluyor ve
     * costBasis ayni gun yapilan satisi atlayabiliyordu. Deger kaydin kendi
     * alani oldugu icin artik yedege giriyor ve esitlemede korunuyor.
     */
    val createdAt: Long = 0L,
    /**
     * Bu kaydin hedef atamasina yaptigi degisiklik - silinince geri alinabilsin
     * diye (bkz. [GoalAssignmentChange], 9.sqm).
     *
     * [goalId] null ya da [goalDelta] sifirsa kayit atamayi oynatmamistir.
     */
    val goalId: String? = null,
    val goalDelta: Double = 0.0,
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

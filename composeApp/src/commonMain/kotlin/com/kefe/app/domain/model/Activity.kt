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

    /**
     * SILME OLAYLARI.
     *
     * Akis yalniz eklemeleri gosteriyordu ve silme sessizdi: bir islem
     * silindiginde "ekledi" satiri da kayboluyordu, yani gecmise bakan biri o
     * kaydin hic var olmadigini saniyordu. Iki kisilik bir defterde bu, "sen mi
     * sildin ben mi" sorusunun cevapsiz kalmasi demek.
     *
     * Ekleme satiri yine kaldirilir (Islem Ekle'deki "son eklediğin" kisayolu
     * onu ayristiriyor ve silinmis bir miktari geri onerirdi); yerine bu gecer.
     */
    DeleteTransaction,
    DeletePosition,
    DeleteGoal,
}

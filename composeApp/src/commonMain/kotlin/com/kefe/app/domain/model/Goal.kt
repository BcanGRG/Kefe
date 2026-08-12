package com.kefe.app.domain.model

data class Goal(
    val id: String,
    val name: String,
    val iconKey: String,
    /**
     * Hedef tutari - HER ZAMAN TL.
     *
     * Gram altin ya da dolar secilse bile burada TL durur: giris aninda o
     * gunun kuruyla cevrilir ve bir daha degismez.
     */
    val amount: Double,
    /**
     * Tutarin GIRILDIGI birim - bir GIRIS KOLAYLIGIDIR, canli bir capa degil.
     *
     * Hicbir hesap bu alani okumaz: ilerleme, projeksiyon, kilometre taslari ve
     * senaryolarin hepsi dogrudan [amount] ile calisir. Yani "gram altin" secili
     * bir hedefle "TL" secili bir hedef, ayni tutarda birebir ayni davranir.
     * Alan yalniz editoru ayni birimde geri acmak icin saklanir.
     *
     * Ekran bir zamanlar bunun aksini vaat ediyordu ("hedef de piyasayla birlikte
     * guncellenir"); metin duzeltildi. Hedefin gercekten altina/dolara
     * capalanmasi ayri bir ozelliktir - tutarin birim cinsinden saklanmasini ve
     * paydanin okuma aninda guncel kurla cevrilmesini ister.
     */
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

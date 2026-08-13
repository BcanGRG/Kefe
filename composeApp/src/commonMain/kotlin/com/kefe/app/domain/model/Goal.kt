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
    val status: GoalStatus = GoalStatus.Active,
    val order: Int = 0,
)

/**
 * Hedef tarihi gecmis mi - TURETILIR, saklanmaz.
 *
 * Once [GoalStatus] icinde `Overdue` diye bir deger vardi ama onu yazacak kod
 * yolu YOKTU: tek sorgusu (`updateGoalStatus`) hic cagrilmiyor, yalniz ornek
 * veri elle isaretliyordu. Tarihi gecmis gercek bir hedef ekranda hic "gecikti"
 * gorunmuyordu.
 *
 * Saklanmasi zaten yanlis olurdu: gun donunce bayatlar ve tazelemek icin bir
 * arka plan isi gerekirdi. Tamamlanmis hedef gecikmis SAYILMAZ.
 *
 * OLCU AYDIR, GUN DEGIL. Hedef tarihi kullaniciya yalniz ay-yil olarak
 * gosteriliyor ve secici de o incelikte; gun alanindaki deger (varsayilan: ayin
 * 1'i) kullanicinin sectigi bir sey degil. Gun bazinda kiyaslayinca icinde
 * bulunulan aya kurulan bir hedef DOGAR DOGMAZ gecikmis sayiliyordu: 12
 * Agustos'ta "Agustos 2026" secmek 1 Agustos yaziyor ve o da gecmiste kaliyor.
 * Gecikme metni de ay farkindan uretiliyor (bkz. arrivalLabel) - burasi da
 * ayni birimden konusmali.
 */
fun Goal.isOverdue(today: KefeDate): Boolean =
    status != GoalStatus.Completed && targetDate.monthOrdinal() < today.monthOrdinal()

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

/**
 * `Overdue` KALDIRILDI: uretilmiyordu, artik [isOverdue] ile turetiliyor.
 * `GoalAllocation` (AllWealth/FixedShare) da kaldirildi - kalici, esitlemede,
 * yedekte ve editorde tasiniyordu ama hicbir hesap okumuyordu; iki secenek
 * arasinda sayisal fark sifirdi. Editordeki toggle daha once kaldirilmisti.
 */
enum class GoalStatus {
    Active,
    Completed,
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

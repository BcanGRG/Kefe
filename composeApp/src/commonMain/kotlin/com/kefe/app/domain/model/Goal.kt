package com.kefe.app.domain.model

data class Goal(
    val id: String,
    val name: String,
    val iconKey: String,
    /**
     * Hedef tutari TL. Butun hesaplar (ilerleme, projeksiyon, gereken aylik) bunu
     * okur.
     *
     * Kura bagli hedefte ([anchorAmount] dolu) depo bunu OKUMA ANINDA guncel kurla
     * yeniden hesaplar: €3.000 x bugunku euro (bkz. withLiveAmount). Saklanan
     * deger o yuzden "son bilinen TL karsiligi"dir; kur gelmezse o kullanilir.
     */
    val amount: Double,
    /**
     * Hedefin birimi. TL disinda HEDEF O BIRIMDE YASAR: [anchorAmount] tutari o
     * birimde tutar, TL karsiligi her okumada guncel kurla bulunur - euro
     * yukselirse hedefin TL'si de yukselir. Yurtdisi tatili gibi dovizle
     * odenecek bir hedef boylece gercek maliyetinin altinda kalmaz.
     *
     * NEYDI: birim yalniz bir giris kolayligiydi; "€3.000" o gunun kuruyla TL'ye
     * cevrilip donuyordu. [anchorAmount] null olan eski dolar/gram hedefleri hala
     * oyle (TL sabit); duzenleyip kaydedince kura baglanirlar.
     */
    val unit: GoalUnit,
    val targetDate: KefeDate,
    val monthlyContribution: Double,
    val isMain: Boolean = false,
    val status: GoalStatus = GoalStatus.Active,
    val order: Int = 0,
    /** Tutar [unit] cinsinden (3000.0 = €3.000); null = TL sabit. */
    val anchorAmount: Double? = null,
    /**
     * Hedefin parasi harcandi (epoch ms) - "Harcadım" ile kapanan hedef. Durum
     * [GoalStatus.Completed] olarak kalir: eski bir surum bilinmeyen bir durum
     * degerinde cokerdi (bkz. 13.sqm).
     */
    val spentAt: Long? = null,
)

/** Hedef guncel kurla mi yasiyor. */
val Goal.isAnchored: Boolean get() = anchorAmount != null && unit != GoalUnit.Try

/**
 * [unit] kolonuna yazilacak deger: eski bir surumun TANIDIGI deger. Euro'yu
 * bilmeyen surum "Eur" metninde cokerdi; euro hedefi o kolonda TL gorunur,
 * gercek birim anchorUnit'te durur (bkz. 13.sqm).
 */
fun GoalUnit.storageUnit(): GoalUnit = if (this == GoalUnit.Eur) GoalUnit.Try else this

/** Birimin fiyat tablosundaki anahtari; TL'de null. */
fun GoalUnit.priceKey(): String? = when (this) {
    GoalUnit.Try -> null
    GoalUnit.GoldGram -> "gold_gram"
    GoalUnit.Usd -> "usd_try"
    GoalUnit.Eur -> "eur_try"
}

/**
 * Kura bagli hedefin TL tutari GUNCEL kurla. Kur SATIS (odenecek) tarafidir:
 * hedef "bu euroyu almak icin ne odemem gerekir" sorusunu yanitlar. Kur yoksa
 * son bilinen TL karsiligi ([Goal.amount]) kalir.
 */
fun Goal.withLiveAmount(rateOf: (String) -> Double?): Goal {
    if (!isAnchored) return this
    val rate = unit.priceKey()?.let(rateOf)?.takeIf { it > 0.0 } ?: return this
    return copy(amount = anchorAmount!! * rate)
}

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

/**
 * Hedef tarihine yetismek icin kalan aylarda ayda gereken TL.
 *
 * Ay sayimi hedef detayindaki "N ay" ile AYNIDIR (ay farki, en az 1): ayni
 * ekranda iki farkli ay sayisi yazmasin. Tamamlanmis ya da tarihi gecmis
 * hedefte null - "gereken" bir rakam anlamsiz. Hedefe zaten ulasildiysa 0.
 */
fun Goal.requiredMonthly(currentWealth: Double, today: KefeDate): Double? {
    if (status == GoalStatus.Completed || isOverdue(today)) return null
    if (currentWealth >= amount) return 0.0
    val months = (targetDate.monthOrdinal() - today.monthOrdinal()).coerceAtLeast(1)
    return (amount - currentWealth) / months
}

enum class GoalUnit {
    Try,
    GoldGram,
    Usd,
    Eur;

    fun label(): String = when (this) {
        Try -> "TL"
        GoldGram -> "Gram altın"
        Usd -> "Dolar"
        Eur -> "Euro"
    }

    companion object {
        fun fromName(name: String?): GoalUnit? = entries.firstOrNull { it.name == name }
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

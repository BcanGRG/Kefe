package com.kefe.app.domain.model

/**
 * Hedef projeksiyonu.
 *
 * GERCEKLESEN SERI YOK. Once gunluk net deger fotograflarindan geliyordu, ama
 * o fotograflar PORTFOY GENELIDIR; hedefin karsiligi ise yalnizca ona atanan
 * varliklardir. Iki egri farkli tabandaydi: "bugun"de birlesmeleri gerekirken
 * gerceklesen portfoyun toplamini, tahmin hedefin birikimini gosteriyordu -
 * asagidaki sozlesmenin kendisi ihlal ediliyordu.
 *
 * Hedefin GECMISI elimizde yok: fotograf tablosu hedef bazinda tutulmuyor.
 * Portfoy egrisini hedefin gecmisi diye cizmektense hic cizmemek dogru - ayni
 * karar varlik detayindaki eksen etiketlerinde de verildi. Gercek bir hedef
 * gecmisi defterden ve fiyat gecmisinden yeniden kurulabilir; ayri bir is.
 *
 * [forecast] bir MODELDIR ve modeli tek cumleyle soylenebilir: "bu tempoyla
 * devam edersen". Ilk noktasi BUGUNKU birikimdir.
 *
 * Piyasa getirisi VARSAYILMAZ. Altin ya da dolar ne yapacak bilinmiyor; bir
 * buyume orani uydurmak, tahmini kullanicinin katkisindan cok o varsayima
 * bagli hale getirirdi. Bu yuzden tahmin dogrusal: yalnizca eklenecek para.
 *
 * Belirsizlik bandi da YOK. Bant, varyansi modelledigimizi soyler; modellemiyoruz.
 */
data class GoalProjection(
    val forecast: List<Double>,
    /** Tahmine gore hedefe varis. Katki sifirsa ve hedef uzaktaysa null. */
    val arrival: KefeDate?,
)

/** Tahminin uzayabilecegi en uzun sure - sonsuz donguye karsi ust sinir. */
private const val MaxForecastMonths = 40 * 12

/**
 * Hedefe kac ay kalir.
 *
 * TEK KURAL, tek yerde: bugunku birikime her ay [monthlyContribution] eklenir,
 * piyasa getirisi varsayilmaz. Hem tahmin egrisi hem senaryo kaydiricisi bunu
 * kullanir; ayri hesaplar olsaydi ekranin iki yeri farkli tarih soylerdi -
 * nitekim soyluyordu.
 *
 * null: aylik katki yoksa (birikim kendiliginden buyumez) ya da varis
 * [MaxForecastMonths] disinda kaliyorsa.
 */
fun monthsToReach(currentWealth: Double, target: Double, monthlyContribution: Double): Int? {
    if (currentWealth >= target) return 0
    if (monthlyContribution <= 0.0) return null

    var value = currentWealth
    var months = 0
    while (value < target && months < MaxForecastMonths) {
        value += monthlyContribution
        months++
    }
    return months.takeIf { value >= target }
}

fun goalProjection(
    goal: Goal,
    currentWealth: Double,
    today: KefeDate,
): GoalProjection {
    // Hedef zaten karsilandiysa tahmin edilecek bir sey yok.
    if (currentWealth >= goal.amount) {
        return GoalProjection(forecast = emptyList(), arrival = today)
    }

    val monthly = goal.monthlyContribution
    if (monthly <= 0.0) {
        // Katki yoksa birikim kendiliginden buyumez - duz bir cizgi cizmek
        // "hicbir sey yapmasan da varirsin" demek olurdu.
        return GoalProjection(forecast = emptyList(), arrival = null)
    }

    val forecast = mutableListOf(currentWealth)
    var value = currentWealth
    var months = 0
    while (value < goal.amount && months < MaxForecastMonths) {
        value += monthly
        months++
        forecast += value
    }

    return GoalProjection(
        forecast = forecast,
        arrival = if (value >= goal.amount) today.plusMonths(months) else null,
    )
}

/**
 * Kilometre taslari - hedefin yuzdeleri.
 *
 * Yuzdeler tasarimin karari (25/50/75/100), TUTARLAR hedeften, ULASILDI MI
 * bugunku birikimden, TARIH ise projeksiyondan gelir. Once uc de sabit yaziliydi
 * ("Mart 2025'te geçildi") ve hangi hedefe bakarsan bak ayni tarihi gosteriyordu.
 */
fun goalMilestones(
    goal: Goal,
    currentWealth: Double,
    forecast: List<Double>,
    today: KefeDate,
): List<GoalMilestone> = listOf(25, 50, 75, 100).map { percent ->
    val amount = goal.amount * percent / 100.0
    val reached = currentWealth >= amount
    GoalMilestone(
        percent = percent,
        amount = amount,
        label = when {
            reached -> "geçildi"
            else -> forecast.indexOfFirst { it >= amount }
                .takeIf { it >= 0 }
                ?.let { index -> "≈ " + today.plusMonths(index).formatMonthYear() }
                ?: "—"
        },
        reached = reached,
    )
}

package com.kefe.app.domain.model

/** Bir varligin BELLI BIR GUNDEKI fiyati. Gunluk fiyat gecmisinin tek satiri. */
data class PricePoint(
    val date: KefeDate,
    val price: Double,
)

/**
 * Bir varligin gunluk, haftalik ve aylik yuzde degisimi.
 *
 * GUNLUK BURAYA SONRADAN EKLENDI. Onceki karar "gunluk degisim kaynagin kendi
 * Change alanindan gelir, gecmisten turetmek calisan bir seyi bozardi" idi.
 * Kaynak OLCULDU ve varsayim yanlis cikti: serbest piyasa ucu yalnizca gram
 * altin, has altin ve gumus icin Change dolduruyor; ceyrek, yarim, tam, ata ve
 * butun ayar kalemleri icin duz SIFIR gonderiyor - fiyatlari gun icinde
 * oynadigi halde.
 *
 * Sonuc kullanicinin ekraninda soyleydi: portfoyunun %95'i altin, "bugunku
 * getiri" ise neredeyse bos. Rakam yok degildi, KAYNAKTA yoktu.
 *
 * Bu yuzden gunluk da gecmisten hesaplanabiliyor - ama YEDEK olarak: kaynagin
 * verdigi rakam varsa o kullanilir (gun ici ve daha kesindir), yoksa buraya
 * dusulur.
 *
 * Alanlar null olabilir ve olmalidir: veri yoksa ekran "—" yazar. Sifir
 * yazmak "hic degismedi" demek olurdu, oysa dogrusu "bilmiyoruz".
 */
data class PeriodChanges(
    val day: Double? = null,
    val week: Double? = null,
    val month: Double? = null,
) {
    companion object {
        val Unknown = PeriodChanges()
    }
}

/** Gunluk degisimde geriye bakilan gun. */
private const val DayDaysBack = 1

/**
 * Bir onceki KAYITLI gun. Tolerans var cunku gecmis ancak uygulama acildiginda
 * yaziliyor: hafta sonu bakilmayan bir portfoyde dunun satiri hic olusmaz.
 * Uc gun, "en son ne zaman baktiysak ondan bu yana" demenin makul siniri;
 * daha genisi bir haftalik hareketi "bugun" diye yazmak olurdu.
 */
private const val DayTolerance = 3

/** Haftalik degisimde geriye bakilan gun ve kabul edilen sapma. */
private const val WeekDaysBack = 7

/** Hafta sonu + uygulamanin acilmadigi bir gun: 7 gunluk pencere 10 gune kadar esner. */
private const val WeekTolerance = 3

/** Aylik degisimde geriye bakilan gun. */
private const val MonthDaysBack = 30

/** Fon gunde bir fiyatlanir, tatiller ust uste binebilir: 30 gun 37'ye kadar esner. */
private const val MonthTolerance = 7

/**
 * [daysBack] gun onceki fiyata gore yuzde degisim.
 *
 * Tam o gunun fiyati yoksa en yakin ESKI noktaya bakilir, ama [tolerance]
 * gununden fazla geriye gidilmez: 40 gun onceki fiyati "haftalik degisim" diye
 * yazmak uydurmaktir. Uygun nokta yoksa null doner ve ekran "—" gosterir.
 *
 * [history] eskiden yeniye SIRALI beklenir (`selectPriceHistory` boyle veriyor).
 */
fun periodChangePercent(
    history: List<PricePoint>,
    latest: Double,
    today: KefeDate,
    daysBack: Int,
    tolerance: Int,
): Double? {
    if (latest <= 0.0 || history.isEmpty()) return null

    val todayEpoch = today.toEpochDay()
    val newest = todayEpoch - daysBack
    val oldest = newest - tolerance

    // Pencereye dusen EN YENI nokta: 7 gun geriye bakiyorsak 8 gun onceki
    // fiyat 10 gun onceki fiyattan daha dogru bir karsilastirmadir.
    val reference = history
        .filter { it.date.toEpochDay() in oldest..newest }
        .maxByOrNull { it.date.toEpochDay() }
        ?: return null

    if (reference.price <= 0.0) return null
    return (latest - reference.price) / reference.price * 100.0
}

/**
 * Bugunden ONCEKI en yeni kayitli fiyat - "en son kapanista neredeydi".
 *
 * Fiyatin bugun GERCEKTEN oynayip oynamadigini anlamak icin gerekiyor. Kaynagin
 * kendi damgasi bu soruyu yanitlamiyor: serbest piyasa ucu hafta sonu da
 * Update_Date'i her dakika ilerletiyor ama fiyatlari cumadan donmus halde
 * biraktigi icin damga "bugun kotasyon var" gibi gorunuyor (9 Agustos 2026
 * Pazar gunu olculdu: damga 10:04 -> 10:05 ilerledi, butun altin fiyatlari ve
 * Change alanlari zerre degismedi).
 *
 * Fiyatin kendisi bu soruyu yanitliyor: deger onceki gunun kaydiyla AYNI ise
 * piyasa o gun oynamamistir.
 */
fun previousDayPrice(history: List<PricePoint>, today: KefeDate): Double? {
    val newest = today.toEpochDay() - DayDaysBack
    val oldest = newest - DayTolerance
    return history
        .filter { it.date.toEpochDay() in oldest..newest }
        .maxByOrNull { it.date.toEpochDay() }
        ?.price
        ?.takeIf { it > 0.0 }
}

/** Gun, hafta ve ay degisimini tek cagriyla verir. */
fun periodChangesOf(
    history: List<PricePoint>,
    latest: Double,
    today: KefeDate,
): PeriodChanges = PeriodChanges(
    day = periodChangePercent(history, latest, today, DayDaysBack, DayTolerance),
    week = periodChangePercent(history, latest, today, WeekDaysBack, WeekTolerance),
    month = periodChangePercent(history, latest, today, MonthDaysBack, MonthTolerance),
)

/**
 * Bir degisimin TL tutari ve yuzdesi BIRLIKTE.
 *
 * Ikisi ayni cozumden gelir: ayri hesaplansalar yuvarlamada ayrisir ve ekranda
 * "+₺100 · +0,00%" gibi kendi kendiyle celisen satirlar cikardi.
 *
 * [percent]'in paydasi DONEM BASINDAKI degerdir - kar/zararin maliyete orani
 * degil. "Bu hafta ne kadar oynadi" ile "bugune kadar ne kazandim" ayri
 * sorulardir.
 */
data class PeriodTotal(
    val amount: Double,
    val percent: Double,
)

/**
 * Bir grup pozisyonun DEGER AGIRLIKLI donem degisimi.
 *
 * Yuzdeler ORTALANMAZ: ₺900.000'lik altinin %1'i ile ₺1.000'lik fonun %10'u
 * ayni agirlikta degildir. [PortfolioSummary.todayChange] ile ayni mantik -
 * her pozisyon donem basindaki degerine geri cozulur, TL farklar toplanir.
 *
 * Yuzdesi bilinmeyen pozisyonlar hesaba HIC girmez (paya da paydaya da): bir
 * fonun bilinmeyen aylik degisimini sifir saymak grubun degisimini sulandirirdi.
 * Hicbirinde veri yoksa null.
 */
fun weightedPeriodTotal(values: List<Pair<Double, Double?>>): PeriodTotal? {
    var previousTotal = 0.0
    var currentTotal = 0.0
    var known = false

    for ((value, percent) in values) {
        if (percent == null) continue
        val previous = value / (1.0 + percent / 100.0)
        if (previous <= 0.0) continue
        previousTotal += previous
        currentTotal += value
        known = true
    }

    if (!known || previousTotal <= 0.0) return null
    val amount = currentTotal - previousTotal
    return PeriodTotal(amount = amount, percent = amount / previousTotal * 100.0)
}

/** Tek pozisyonun donem degisimi - yuzde biliniyorsa TL karsiligiyla. */
fun periodTotalOf(value: Double, percent: Double?): PeriodTotal? {
    if (percent == null) return null
    val previous = value / (1.0 + percent / 100.0)
    if (previous <= 0.0) return null
    return PeriodTotal(amount = value - previous, percent = percent)
}

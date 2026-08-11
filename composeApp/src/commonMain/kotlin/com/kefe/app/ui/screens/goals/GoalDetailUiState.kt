package com.kefe.app.ui.screens.goals

import com.kefe.app.domain.model.AllocationSlice
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAsset
import com.kefe.app.domain.model.GoalMilestone
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthlyContribution
import com.kefe.app.domain.model.PeriodTotal
import com.kefe.app.domain.model.Position

enum class GoalDetailStage { Loading, Ready, Missing }

/**
 * Secicideki tek varlik satiri.
 *
 * [otherGoalName] doluysa varlik BASKA bir hedefe atanmis: secmek onu tasir ve
 * o hedefin ilerlemesi duser. Yazilmazsa kullanici bunu ancak digerine bakinca
 * fark eder.
 */
data class AssignableAsset(
    val position: Position,
    val assignedToThis: Boolean,
    val otherGoalName: String?,
    /** Bu hedefe sayilan miktar. Atanmamissa 0. */
    val assignedQuantity: Double = 0.0,
    /**
     * "Tümü" secili mi - yani atama miktar tasimiyor (-1).
     *
     * Fark onemli: "tümü" ileride ALINACAKLARI da kapsar, sabit bir miktar
     * kapsamaz. Kullanici bir kismini atadiktan sonra "hepsi ve bundan
     * sonrakiler de" diyebilmeli.
     */
    val coversWholePosition: Boolean = false,
) {
    /** Kaydiricinin ust siniri; miktarsiz varlikta secici cizilmez. */
    val maxQuantity: Double get() = position.quantity
    val isDivisible: Boolean get() = position.quantity > 0.0
}

/** Aylik katki gecmisi tablosunun tek satiri. */
data class ContributionRow(
    /** "Tem 2026" */
    val monthLabel: String,
    /** 0.0 ise o ay katki yapilmamistir - tabloda "katkı yok" yazar. */
    val contribution: Double,
    /** O ayin son fotografi. Fotograf yoksa null - "0" yazmak yanlis olurdu. */
    val monthEnd: Double?,
    /** Ay sonu - ay basi - katki. Iki ucundan biri bilinmiyorsa null. */
    val gain: Double?,
)

data class GoalDetailUiState(
    val stage: GoalDetailStage = GoalDetailStage.Loading,
    val goal: Goal? = null,
    /** Gercek bugun - tum ay hesaplari buradan gelir. */
    val today: KefeDate = KefeDate(2026, 7, 1),

    /** Ilerleme TOPLAM birikime gore olculur. */
    val currentWealth: Double = 0.0,
    val progress: Float = 0f,

    /**
     * Bu hedefe ATANAN kisimlarin bugunku degisimi ve toplam getirisi.
     *
     * Ozet ekranindaki iki satirin hedefe ozel hali - ayni tanimlar, farkli
     * kapsam. Hedefe hicbir varlik atanmamissa null: sifir yazmak "hedef bugun
     * oynamadi" demek olurdu, oysa dogrusu "olcecek bir sey yok".
     */
    val todayChange: PeriodTotal? = null,
    val totalReturn: PeriodTotal? = null,
    /** Hedefi karsilayan varliklarin sinif dagilimi - donut ve legend. */
    val allocation: List<AllocationSlice> = emptyList(),

    /** Bugunden hedef tarihine kalan ay. */
    val monthsToTarget: Int = 0,
    /** Tahmin hedef tarihini kac ay asiyor (negatifse erken). */
    val delayMonths: Int = 0,

    /** Gerceklesen: gunluk net deger fotograflari. */
    /** Tahmin: bugunku birikim + her ay eklenecek katki. Belirsizlik bandi yok. */
    val projectionForecast: List<Double> = emptyList(),
    /** Tahmine gore hedefe varis. Aylik katki 0 ise null - tahmin edilemez. */
    val projectedArrival: KefeDate? = null,

    val milestones: List<GoalMilestone> = emptyList(),
    val months: List<MonthlyContribution> = emptyList(),
    /** Katkisiz ayin bulunma ekli adi ("Kasım'da") - grafigin altindaki notr not. */
    val emptyMonthLocative: String? = null,
    val rows: List<ContributionRow> = emptyList(),
    /** Tasarimdaki kisa liste: en yeni uc ay + varsa katkisiz ay. */
    val collapsedRows: List<ContributionRow> = emptyList(),
    val showAllRows: Boolean = false,

    /**
     * Bu hedefe atanmis varliklar - ATANAN KISIMLARIYLA.
     *
     * [GoalAsset] tasiyor cunku atama artik miktar tutabiliyor: 16 ceyregin
     * 15'i bu hedefteyse liste "16 adet" ve varligin tam degerini yazmamali.
     */
    val assignedAssets: List<GoalAsset> = emptyList(),
    /** "Bu hedefi karsilayanlar" listesinin gosterdigi kalemler. */
    val composingAssets: List<GoalAsset> = emptyList(),
    /** Seciciye dokulen tum varliklar. */
    val assignableAssets: List<AssignableAsset> = emptyList(),
    val assetPickerOpen: Boolean = false,

    /** Senaryo kaydiricisi: aylik katki, BIN TL cinsinden (30..120). */
    val scenarioContribution: Float = ScenarioMinThousands,
    val baseContribution: Double = 0.0,
    /** Senaryonun verdigi varis ayi etiketi: "Ekim 2028". Hesaplanamazsa bos. */
    val scenarioArrival: String = "",
    /** Senaryoya gore hedefe kalan ay. Katki 0 ise null - varis yok. */
    val scenarioMonths: Int? = null,
    /** Senaryonun SIMDIKI PLANA gore farki (ay). Kiyaslanamazsa null. */
    val scenarioDiffMonths: Int? = null,
) {
    /** Hedefe ulasildi - fazlasi birikimde kalir. */
    val exceeded: Boolean get() = progress >= 1f

    /** Tarihi gecti ama hedef duruyor. */
    val overdue: Boolean get() = !exceeded && goal?.status == GoalStatus.Overdue

    /** Projeksiyon/senaryo/kilometre taslari yalniz yolundaki hedefte gosterilir. */
    val showAnalysis: Boolean get() = !exceeded && !overdue
}

const val ScenarioMinThousands: Float = 30f
const val ScenarioMaxThousands: Float = 120f

/** 30.000 - 120.000 arasi 5.000'lik adimlar -> 18 aralik. */
const val ScenarioSteps: Int = 18

sealed interface GoalDetailIntent {
    data class SetScenarioContribution(val thousands: Float) : GoalDetailIntent
    data object ToggleAllRows : GoalDetailIntent

    /** "Hedefi kapat" - asilan hedefi tamamlandi olarak isaretler. */
    data object CloseGoal : GoalDetailIntent

    data object OpenAssetPicker : GoalDetailIntent
    data object CloseAssetPicker : GoalDetailIntent

    /** Atanmamissa TUMUNU atar, atanmissa kaldirir. */
    data class ToggleAsset(val positionId: String) : GoalDetailIntent

    /**
     * Varligin bu hedefe sayilan miktarini belirler.
     *
     * Sonradan fikir degistirmenin tek yolu buydu ve yoktu: "gecen hafta
     * hedefsiz aldigim 1 ceyregi de Ev'e alayim" diyen kullanicinin elinde
     * yalnizca hepsi-ya-hicbiri anahtari vardi.
     */
    data class SetAssetQuantity(val positionId: String, val quantity: Double) : GoalDetailIntent

    /**
     * "Tümü": miktar sabitlemeyi birakir (-1).
     *
     * Sabit bir miktardan farki, ILERIDE ALINACAKLARI da kapsamasidir.
     */
    data class AssignWholeAsset(val positionId: String) : GoalDetailIntent
}

/** Ay sirasi - iki tarih arasindaki ay farkini hesaplamak icin. */
internal fun KefeDate.monthIndex(): Int = year * 12 + (month - 1)

/** Ay adindan bulunma eki: "Kasım'da", "Mart'ta", "Eylül'de". */
internal fun trMonthLocative(month: Int): String = when (month) {
    1 -> "Ocak'ta"
    2 -> "Şubat'ta"
    3 -> "Mart'ta"
    4 -> "Nisan'da"
    5 -> "Mayıs'ta"
    6 -> "Haziran'da"
    7 -> "Temmuz'da"
    8 -> "Ağustos'ta"
    9 -> "Eylül'de"
    10 -> "Ekim'de"
    11 -> "Kasım'da"
    else -> "Aralık'ta"
}

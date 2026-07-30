package com.kefe.app.ui.screens.goals

import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAllocation
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate

/** Ekranin veri durumu. Tasarimda liste ve bos durum ayri cerceveler. */
enum class GoalsStage { Loading, Empty, Ready }

/** Bos durumdaki hazir oneri cipi. */
data class GoalSuggestion(val name: String, val iconKey: String)

/** Tasarimdaki alti oneri, sirasiyla. */
val GoalSuggestions: List<GoalSuggestion> = listOf(
    GoalSuggestion("Ev", "ev"),
    GoalSuggestion("Araba", "araba"),
    GoalSuggestion("Eğitim", "egitim"),
    GoalSuggestion("Tatil", "ucak"),
    GoalSuggestion("Acil durum", "kalkan"),
    GoalSuggestion("Diğer", "yildiz"),
)

/** Sayfadaki ikon izgarasi: 6 sutun x 2 satir. */
val GoalIconKeys: List<String> = listOf(
    "ev", "araba", "ucak", "egitim", "kalkan", "kalp",
    "hediye", "cocuk", "laptop", "palmiye", "yuzuk", "yildiz",
)

/**
 * Olustur/duzenle sayfasinin tum alanlari.
 *
 * [amountText] SECILI BIRIMDEKI sayidir (TL'de 7.800.000, gram altinda 434).
 * Birim degisince tutar TL uzerinden cevrilir; boylece kullanicinin girdigi
 * deger birim degistirdiginde anlamini kaybetmez.
 */
data class GoalEditorState(
    val goalId: String? = null,
    val name: String = "",
    val iconKey: String = "ev",
    val amountText: String = "",
    val unit: GoalUnit = GoalUnit.Try,
    val targetDate: KefeDate = KefeDate(2028, 12, 1),
    val contributionText: String = "",
    val allocation: GoalAllocation = GoalAllocation.AllWealth,
    val isMain: Boolean = false,
    val advancedExpanded: Boolean = true,

    /**
     * Kaydet'e basildiginda bos bulunan zorunlu alanlar. Ekran o alani kirmizi
     * cizip ilkine kaydirir; kullanici yazmaya baslayinca temizlenir.
     */
    val nameError: Boolean = false,
    val amountError: Boolean = false,

    /** Tarih secici acik mi. Alan ay-yil gosterdigi icin secici de o inceliktedir. */
    val datePickerOpen: Boolean = false,
    val goldGramPrice: Double = 0.0,
    val usdPrice: Double = 0.0,
    val openGoalCount: Int = 0,
) {
    val isNew: Boolean get() = goalId == null
}

/** Alanlarda binlik noktali metin tutulur; sayiya cevirirken ayraclar atilir. */
// Ham metin ("3000000" / "2,5") sayiya. Binlik ayrac (nokta) atilir, ondalik
// virgul noktaya cevrilir - boylece "2,5 gram" gibi degerler dogru okunur.
fun String.parseAmount(): Double =
    filter { it.isDigit() || it == ',' }.replace(',', '.').toDoubleOrNull() ?: 0.0

/** Secili birimin TL karsiligi. Fiyat yoksa 1.0 - bolme hatasi olmasin. */
fun GoalEditorState.rateOf(target: GoalUnit): Double = when (target) {
    GoalUnit.Try -> 1.0
    GoalUnit.GoldGram -> goldGramPrice.takeIf { it > 0.0 } ?: 1.0
    GoalUnit.Usd -> usdPrice.takeIf { it > 0.0 } ?: 1.0
}

/** Girilen tutarin TL karsiligi - ilerleme hep TL uzerinden olculur. */
fun GoalEditorState.amountInTry(): Double = amountText.parseAmount() * rateOf(unit)

data class GoalsUiState(
    val stage: GoalsStage = GoalsStage.Loading,
    /** Acik hedefler - depo sirasiyla (surukleme bu sirayi degistirir). */
    val goals: List<Goal> = emptyList(),
    val completed: List<Goal> = emptyList(),
    /** Hedef ilerlemesi TOPLAM birikime gore olculur. */
    val totalWealth: Double = 0.0,
    /**
     * Hedef basina karsilik. Varlik atanmis hedef yalniz kendi varliklarini
     * sayar; atanmamis hedef tum birikimi sayar (eski davranis).
     */
    val wealthByGoal: Map<String, Double> = emptyMap(),
    val completedExpanded: Boolean = false,
    val sortMode: Boolean = false,
    val editor: GoalEditorState? = null,
)

sealed interface GoalsIntent {
    data object ToggleCompleted : GoalsIntent
    data object ToggleSortMode : GoalsIntent

    /** Surukleme bittiginde yeni sira - tasarimdaki tutamak bunu uretir. */
    data class Reorder(val orderedIds: List<String>) : GoalsIntent

    data class CreateGoal(val suggestion: GoalSuggestion? = null) : GoalsIntent
    data class EditGoal(val goalId: String) : GoalsIntent
    data object DismissEditor : GoalsIntent

    data class EditorName(val value: String) : GoalsIntent
    data class EditorIcon(val key: String) : GoalsIntent
    data class EditorAmount(val value: String) : GoalsIntent
    data class EditorUnit(val unit: GoalUnit) : GoalsIntent
    data class EditorContribution(val value: String) : GoalsIntent
    data class EditorAllocation(val allocation: GoalAllocation) : GoalsIntent
    data class EditorMain(val value: Boolean) : GoalsIntent

    data object ToggleEditorDatePicker : GoalsIntent

    /** Hedef tarihini [months] ay kaydirir. Negatif geri alir. */
    data class EditorShiftTargetDate(val months: Int) : GoalsIntent
    data object ToggleEditorAdvanced : GoalsIntent
    data object SaveEditor : GoalsIntent
    data object DeleteEditorGoal : GoalsIntent
}

// --- Turkce ek yardimcilari -------------------------------------------------

/**
 * Yil sonrasi bulunma eki: 2026'da, 2027'de, 2025'te.
 * Son rakamin okunusundaki son unlu ve sert/yumusak sessiz belirler.
 */
internal fun trYearLocative(year: Int): String = when (year % 10) {
    3, 4, 5 -> "'te"
    1, 2, 7, 8 -> "'de"
    else -> "'da"
}

/** "3" yerine "Üç" - tasarimdaki alt basligin metni sayiyla yazilmiyor. */
internal fun trCount(value: Int): String = when (value) {
    1 -> "Bir"
    2 -> "İki"
    3 -> "Üç"
    4 -> "Dört"
    5 -> "Beş"
    6 -> "Altı"
    7 -> "Yedi"
    8 -> "Sekiz"
    9 -> "Dokuz"
    else -> value.toString()
}

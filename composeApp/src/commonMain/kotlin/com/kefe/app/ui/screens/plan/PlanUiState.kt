package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.YearMonth

// Bu dosya Compose'a BAGLI DEGIL: durum, niyet ve etki saf Kotlin; turetim ve
// metinler commonTest'te ekransiz sinanir (bkz. PlanTextTest, PlanViewModelTest).

enum class PlanStage { Loading, Ready }

/** Gosterilen ayin bugune gore yeri - basligi, notlari ve "Al"i belirler. */
enum class MonthRelation { Past, Current, Future }

data class PlanUiState(
    val stage: PlanStage = PlanStage.Loading,
    val content: PlanContent = PlanContent(),
)

/** Veriden TURETILEN her sey; toplayici YALNIZ bunu degistirir. */
data class PlanContent(
    val header: PlanHeader = PlanHeader(),
)

/**
 * Ay basligi: "< Ekim 2026 >" ve alt satir.
 *
 * Gecis sinirlari ([canGoBack], [canGoForward]) veriden gelir: geriye ilk
 * plan/islem/defter ayina kadar, ileriye EN FAZLA bir ay (onayli karar).
 */
data class PlanHeader(
    val month: YearMonth? = null,
    /** "Ekim 2026". */
    val title: String = "",
    /** "Ekim · 9 gün kaldı" | "Geçmiş ay · alımlar işlem tarihine göre sayılır." */
    val subtitle: String = "",
    val relation: MonthRelation = MonthRelation.Current,
    val canGoBack: Boolean = false,
    /** En fazla 1 ay ileri. */
    val canGoForward: Boolean = false,
    /** Baska bir aydayken "Bu ay" cipi - tek dokunusla geri donus. */
    val showThisMonthChip: Boolean = false,
)

sealed interface PlanIntent {
    data object PreviousMonth : PlanIntent
    data object NextMonth : PlanIntent

    /** "Bu ay" cipi: secim bosaltilir, sayfa gun donunce kendiliginden ilerler. */
    data object ThisMonth : PlanIntent
}

/** Kabukta karsilanir (bkz. App.kt): ekleme sayfasi ve serit kabugun. */
sealed interface PlanEffect {
    /** "Al": 4/5'te mevcut ekleme sayfasi; varlik eldeyse onunla, degilse varlik secimiyle. */
    data class OpenAddTransaction(val positionId: String?) : PlanEffect

    /** Kabugun saveError seridi. */
    data class Message(val text: String) : PlanEffect
}

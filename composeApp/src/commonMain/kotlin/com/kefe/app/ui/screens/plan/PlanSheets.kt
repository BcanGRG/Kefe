package com.kefe.app.ui.screens.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Plan sheet'lerinin TEK yeri - kabuk cizer, her ekranin ustunde.
 *
 * NEDEN kabukta: Hedefler'de sheet hem ekranda hem kabukta cizildigi icin iki kez
 * aciliyor. Plan sheet'leri yalniz burada.
 *
 * Her tur KOSULSUZ bestelenir (gorunurlugu [sheet]'e bagli): KefeBottomSheet'in geri
 * isleyicisi bestelemeye girip cikarsa sira bozulur (bkz. KefeBackHandler).
 */
@Composable
fun PlanSheets(
    sheet: PlanSheet?,
    onIntent: (PlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kapanista durum null olur; baslik ve alanlar bir anda bosalmasin diye son gorunen
    // sheet tutulur. ACIKKEN her zaman canli deger okunur. NEDEN: LaunchedEffect ile
    // tutmak (MarketScreen kalibi) bestelemeden SONRA calisir; yeni acilan sheet bir kare
    // onceki sheet'i (orn. eski bir kalem editorunu) gosterir, yazarken alan geriden gelir.
    var lastShown by remember { mutableStateOf<PlanSheet?>(null) }
    val shown = sheet ?: lastShown
    SideEffect { if (sheet != null) lastShown = sheet }

    PlanItemSheet(
        visible = sheet is PlanSheet.Item,
        editor = (shown as? PlanSheet.Item)?.editor,
        onIntent = onIntent,
        modifier = modifier,
    )
    PlanCopySheet(
        visible = sheet is PlanSheet.Copy,
        draft = (shown as? PlanSheet.Copy)?.draft,
        onIntent = onIntent,
        modifier = modifier,
    )
    PlanIncomeSheet(
        visible = sheet is PlanSheet.Income,
        editor = (shown as? PlanSheet.Income)?.editor,
        onIntent = onIntent,
        modifier = modifier,
    )
    PlanExpenseSheet(
        visible = sheet is PlanSheet.Expense,
        editor = (shown as? PlanSheet.Expense)?.editor,
        onIntent = onIntent,
        modifier = modifier,
    )
    PlanBudgetSheet(
        visible = sheet is PlanSheet.Budget,
        editor = (shown as? PlanSheet.Budget)?.editor,
        onIntent = onIntent,
        modifier = modifier,
    )
    PlanPurchaseSheet(
        visible = sheet is PlanSheet.Purchase,
        sheet = (shown as? PlanSheet.Purchase)?.sheet,
        onIntent = onIntent,
        modifier = modifier,
    )
}

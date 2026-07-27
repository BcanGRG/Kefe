package com.kefe.app.ui.screens.summary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kefe.app.ui.layout.KefeMarketRow
import com.kefe.app.ui.layout.LocalWindowSize
import com.kefe.app.ui.layout.WindowSize

/**
 * Ozet ekraninin pencere genisligine gore secilen yuzu.
 *
 * Secim CIHAZ TURUNE degil genislige bakar ([LocalWindowSize]) - katlanabilir
 * acildiginda telefon da tablet yerlesimine gecer, masaustu penceresi
 * daraltildiginda tersi olur.
 *
 * Uc yerlesim ayni [SummaryUiState] ile beslenir; farklilik yalniz sunumdadir.
 * Navigasyon (alt serit / ray / yan navigasyon) uygulama kabugunun isidir.
 */
@Composable
fun SummaryScreenAdaptive(
    state: SummaryUiState,
    onIntent: (SummaryIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMarket: () -> Unit = {},
    onAddAsset: () -> Unit = {},
    modifier: Modifier = Modifier,
    marketRows: List<KefeMarketRow> = emptyList(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onOpenMarketRow: ((KefeMarketRow) -> Unit)? = null,
) {
    when (LocalWindowSize.current) {
        WindowSize.Compact -> SummaryScreen(
            state = state,
            onIntent = onIntent,
            onOpenGoal = onOpenGoal,
            onOpenGoals = onOpenGoals,
            onOpenActivity = onOpenActivity,
            onOpenMarket = onOpenMarket,
            onAddAsset = onAddAsset,
            modifier = modifier,
        )

        WindowSize.Medium -> SummaryScreenTablet(
            state = state,
            onIntent = onIntent,
            onOpenGoal = onOpenGoal,
            onOpenGoals = onOpenGoals,
            onOpenActivity = onOpenActivity,
            onOpenMarket = onOpenMarket,
            onAddAsset = onAddAsset,
            modifier = modifier,
        )

        WindowSize.Expanded -> SummaryScreenDesktop(
            state = state,
            onIntent = onIntent,
            onOpenGoal = onOpenGoal,
            onOpenGoals = onOpenGoals,
            onOpenActivity = onOpenActivity,
            onAddAsset = onAddAsset,
            modifier = modifier,
            marketRows = marketRows,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onOpenMarketRow = onOpenMarketRow,
        )
    }
}

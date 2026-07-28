package com.kefe.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kefe.app.ui.theme.KefeTheme

/**
 * Asagi cekip yenileme.
 *
 * Ust bardaki yenile dugmesinin YERINE degil yanina: dugme tek dokunusla
 * calisiyor ama kesfedilmesi gereken bir ikon; asagi cekmek telefonda fiyat
 * tazelemenin refleks hareketi.
 *
 * Gostergenin rengi temadan gelir - Material varsayilani mor bir vurgu
 * getiriyor ve uygulamanin hicbir yerinde o renk yok.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KefePullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = KefeTheme.colors
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = colors.surfaceElevated,
                color = colors.accent,
            )
        },
        content = { content() },
    )
}

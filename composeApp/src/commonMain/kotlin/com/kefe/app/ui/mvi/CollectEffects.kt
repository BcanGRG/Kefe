package com.kefe.app.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

/**
 * Etki kanalini dinler.
 *
 * [onEffect] rememberUpdatedState ile tutulur: lambda her bestelemede yeniden
 * yaratildigi icin anahtar olarak verilseydi toplayici surekli yeniden baslar,
 * arada gelen etkiler kacardi.
 */
@Composable
fun <E> CollectEffects(effects: Flow<E>, onEffect: (E) -> Unit) {
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects) {
        effects.collect { handler(it) }
    }
}

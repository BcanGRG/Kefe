package com.kefe.app.ui.screens.quick

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.data.sync.SyncCoordinator
import com.kefe.app.di.appModule
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.mvi.CollectEffects
import com.kefe.app.ui.screens.account.ThemeMode
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinConfiguration

/**
 * Hizli giris penceresinin koku: Android'de ana ekranin ustunde, uygulama
 * acilmadan cizilir (QuickExpenseActivity). [initialCategory] widget'taki
 * kalemin adi ("Groceries", "c:Kredi Kartı Limit"); null = + dugmesi.
 *
 * Tema uygulamanin Ayarlar secimine uyar; kayit hesaba bagliysa esitlemeyle gider.
 */
@Composable
fun QuickExpenseApp(initialCategory: String?, onClose: () -> Unit) {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(appModule) }),
    ) {
        val preferences = koinInject<PreferencesRepository>()
        val prefs by remember(preferences) { preferences.observeAll() }.collectAsState(initial = null)

        // Kordinator surec omurlu: start() ikinci kez cagrilirsa yok sayilir. Kayit
        // aninda push'lanir; pencere kapandiktan sonra surec bir sure daha yasar.
        val sync = koinInject<SyncCoordinator>()
        LaunchedEffect(Unit) { sync.start() }

        val loaded = prefs
        if (loaded != null) {
            val dark = when (ThemeMode.entries.firstOrNull { it.name == loaded[PreferenceKeys.ThemeMode] } ?: ThemeMode.System) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            val showCents = loaded[PreferenceKeys.ShowCents]?.toBooleanStrictOrNull() ?: false
            KefeTheme(darkTheme = dark, showCents = showCents) {
                val vm = koinViewModel<QuickExpenseViewModel>(key = "quick:${initialCategory.orEmpty()}") {
                    parametersOf(initialCategory.orEmpty())
                }
                val state by vm.state.collectAsState()
                CollectEffects(vm.effects) { effect ->
                    when (effect) {
                        QuickExpenseEffect.Close -> onClose()
                    }
                }
                QuickExpenseScreen(state = state, onIntent = vm::onIntent)
            }
        }
    }
}

@Composable
fun QuickExpenseScreen(state: QuickExpenseUiState, onIntent: (QuickExpenseIntent) -> Unit) {
    val c = KefeTheme.colors
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(EnterMillis))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    .clickable(interactionSource = scrimInteraction, indication = null) {
                        onIntent(QuickExpenseIntent.Close)
                    },
            )
        }
        AnimatedVisibility(
            visibleState = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(EnterMillis)) { it } + fadeIn(tween(EnterMillis)),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SheetMaxWidth)
                    .fillMaxWidth()
                    .clip(KefeShapes.sheet)
                    .background(c.surfaceElevated)
                    // Panele dokunus scrim'e gecip pencereyi kapatmasin.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(top = Space.x8, bottom = Space.x20),
                verticalArrangement = Arrangement.spacedBy(Space.x12),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(c.outline),
                )
                val saved = state.saved
                when {
                    state.loading -> Spacer(Modifier.height(LoadingHeight))
                    saved != null -> SavedContent(saved, onIntent)
                    else -> FormContent(state, onIntent)
                }
            }
        }
    }
}

@Composable
private fun FormContent(state: QuickExpenseUiState, onIntent: (QuickExpenseIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    // Not yazilirken sistem klavyesi acik: tus takimi gizlenir, iki klavye ust uste binmez.
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(verticalArrangement = Arrangement.spacedBy(Space.x12)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.x16, end = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Harcama ekle", style = t.h2.copy(fontSize = 20.sp), color = c.onSurface, modifier = Modifier.weight(1f))
            DaySwitch(state.day) { onIntent(QuickExpenseIntent.SelectDay(it)) }
            Box(
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .clip(CircleShape)
                    .clickable { onIntent(QuickExpenseIntent.Close) }
                    .semantics { contentDescription = "Kapat" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(KefeIcons.Close, contentDescription = null, tint = c.onSurfaceMuted, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = Space.x16)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focus.clearFocus() },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.shownAmount,
                    style = t.display.copy(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold).tabular(),
                    color = if (state.amountText.isEmpty()) c.onSurfaceMuted else c.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Caret(visible = !imeOpen)
            }
            val error = state.error
            val line = state.line
            when {
                error != null -> Text(error, style = t.caption, color = c.negative)
                line != null -> Text(line.text, style = t.caption, color = toneColor(line.tone))
            }
        }

        CategoryRow(state, onIntent)

        NoteField(
            value = state.note,
            onValueChange = { onIntent(QuickExpenseIntent.Note(it)) },
            onDone = { focus.clearFocus() },
        )

        if (state.suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.x16),
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    Icon(
                        KefeIcons.Clock,
                        contentDescription = "Sık girilenler",
                        tint = c.onSurfaceMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                items(state.suggestions, key = { it.note.lowercase() }) { s ->
                    Row(
                        modifier = Modifier
                            .height(Sizes.chipSmall)
                            .clip(KefeShapes.pill)
                            .border(1.dp, c.outline, KefeShapes.pill)
                            .clickable(role = Role.Button) {
                                focus.clearFocus()
                                onIntent(QuickExpenseIntent.PickSuggestion(s))
                            }
                            .padding(horizontal = Space.x12),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(s.note, style = t.caption, color = c.onSurface, maxLines = 1)
                        Text(s.amountText, style = t.caption.tabular(), color = c.onSurfaceMuted, maxLines = 1)
                    }
                }
            }
        }

        if (!imeOpen) {
            Keypad(
                onKey = { key ->
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onIntent(QuickExpenseIntent.Key(key))
                },
            )
        }

        SaveButton(
            text = state.saveText,
            enabled = state.canSave,
            onClick = {
                focus.clearFocus()
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onIntent(QuickExpenseIntent.Save)
            },
        )
    }
}

@Composable
private fun DaySwitch(day: QuickDay, onSelect: (QuickDay) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceSunken)
            .padding(3.dp),
    ) {
        listOf(QuickDay.Today to "Bugün", QuickDay.Yesterday to "Dün").forEach { (value, label) ->
            val on = value == day
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) c.accentMuted else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .semantics { selected = on }
                    .padding(horizontal = Space.x12),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (on) c.accent else c.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun Caret(visible: Boolean) {
    val c = KefeTheme.colors
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(CaretMillis), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    Box(
        Modifier
            .padding(start = 3.dp)
            .width(2.dp)
            .height(36.dp)
            .alpha(if (visible) blink else 0f)
            .background(c.accent),
    )
}

@Composable
private fun CategoryRow(state: QuickExpenseUiState, onIntent: (QuickExpenseIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val start = state.categories.indexOfFirst { it.category == state.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = Space.x16),
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        items(state.categories, key = { it.category.name }) { item ->
            val on = item.category == state.selected
            Row(
                modifier = Modifier
                    .height(Sizes.chipMedium)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.accentMuted else c.surfaceSunken)
                    .border(1.dp, if (on) c.accent else c.surfaceSunken, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Tab) { onIntent(QuickExpenseIntent.SelectCategory(item.category)) }
                    .semantics { selected = on }
                    .padding(start = Space.x8, end = Space.x12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InitialBadge(item.initial, quickCategoryColor(item.colorIndex), 20.dp)
                Text(
                    item.label,
                    style = if (on) t.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold) else t.caption.copy(fontSize = 14.sp),
                    color = c.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun InitialBadge(initial: String, color: Color, size: Dp) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(c.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = KefeTheme.type.micro.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun NoteField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        modifier = Modifier
            .padding(horizontal = Space.x16)
            .fillMaxWidth()
            .height(Sizes.fieldDefault)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceSunken)
            .padding(horizontal = Space.x14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
    ) {
        Text("Not", style = t.body.copy(fontSize = 14.sp), color = c.onSurfaceMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = t.body.copy(color = c.onSurface),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Not" },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("İsteğe bağlı", style = t.body, color = c.onSurfaceMuted.copy(alpha = 0.7f))
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(onKey: (QuickKey) -> Unit) {
    val c = KefeTheme.colors
    val rows = listOf(
        listOf(QuickKey.Digit('1'), QuickKey.Digit('2'), QuickKey.Digit('3')),
        listOf(QuickKey.Digit('4'), QuickKey.Digit('5'), QuickKey.Digit('6')),
        listOf(QuickKey.Digit('7'), QuickKey.Digit('8'), QuickKey.Digit('9')),
        listOf(QuickKey.Comma, QuickKey.Digit('0'), QuickKey.Back),
    )
    Column(
        modifier = Modifier.padding(horizontal = Space.x16),
        verticalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
                row.forEach { key ->
                    val label = when (key) {
                        is QuickKey.Digit -> key.digit.toString()
                        QuickKey.Comma -> "Virgül"
                        QuickKey.Back -> "Sil, basılı tutunca hepsini sil"
                        QuickKey.Clear -> "Temizle"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(KeyHeight)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surfaceSunken)
                            .combinedClickable(
                                role = Role.Button,
                                onLongClick = if (key == QuickKey.Back) ({ onKey(QuickKey.Clear) }) else null,
                                onClick = { onKey(key) },
                            )
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            QuickKey.Back -> Icon(KefeIcons.Backspace, contentDescription = null, tint = c.onSurface, modifier = Modifier.size(24.dp))
                            else -> Text(
                                if (key is QuickKey.Digit) key.digit.toString() else ",",
                                style = KefeTheme.type.h2.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium),
                                color = c.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .padding(horizontal = Space.x16)
            .fillMaxWidth()
            .height(Sizes.buttonPrimary)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) c.accent else c.accentMuted)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = KefeTheme.type.bodyStrong.copy(fontSize = 16.sp).tabular(),
            color = if (enabled) c.onAccent else c.onSurfaceMuted,
        )
    }
}

@Composable
private fun SavedContent(saved: QuickSavedUi, onIntent: (QuickExpenseIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x16, vertical = Space.x12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(c.positive.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KefeIcons.Check, contentDescription = null, tint = c.positive, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(Space.x4))
        Text(
            saved.title,
            style = t.h2.tabular(),
            color = c.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(saved.sub, style = t.body.copy(fontSize = 14.sp), color = c.onSurfaceMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        saved.line?.let { Text(it.text, style = t.caption, color = toneColor(it.tone)) }
        Spacer(Modifier.height(Space.x8))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Sizes.fieldDefault)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, c.outline, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { onIntent(QuickExpenseIntent.Undo) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Geri al", style = t.bodyStrong, color = c.onSurface)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Sizes.fieldDefault)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surfaceSunken)
                    .clickable(role = Role.Button) { onIntent(QuickExpenseIntent.Again) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Bir tane daha", style = t.bodyStrong, color = c.accent)
            }
        }
        Text(
            "3 saniye içinde kendiliğinden kapanır",
            style = t.captionSmall,
            color = c.onSurfaceMuted,
            modifier = Modifier.padding(top = Space.x4),
        )
    }
}

@Composable
private fun toneColor(tone: QuickTone): Color {
    val c = KefeTheme.colors
    return when (tone) {
        QuickTone.Muted -> c.onSurfaceMuted
        QuickTone.Warning -> c.warning
        QuickTone.Negative -> c.negative
    }
}

/** Harcamalar sayfasindaki kalem renkleriyle ayni palet; -1 plan disi, soluk. */
@Composable
private fun quickCategoryColor(index: Int): Color {
    val c = KefeTheme.colors
    if (index < 0) return c.onSurfaceMuted
    val palette = listOf(c.gold, c.fx, c.fund, c.stock, c.cash, c.silver)
    return palette[index % palette.size]
}

private const val EnterMillis = 220
private const val CaretMillis = 530
private val SheetMaxWidth = 560.dp
private val KeyHeight = 52.dp
private val LoadingHeight = 520.dp

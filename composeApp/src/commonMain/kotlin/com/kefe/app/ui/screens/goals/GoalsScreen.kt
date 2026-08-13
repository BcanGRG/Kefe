package com.kefe.app.ui.screens.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.formatMonthYear
import com.kefe.app.domain.model.progress
import com.kefe.app.ui.components.KefeAccentCard
import com.kefe.app.ui.components.KefeCard
import com.kefe.app.ui.components.KefeDashedCard
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeProgressBar
import com.kefe.app.ui.components.KefeSkeletonBlock
import kotlin.math.min
import kotlin.math.round
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/** Kartlar arasi bosluk - surukleme adimi da bu degere gore hesaplanir. */
private val GoalGap = Space.x10

/**
 * Hedefler ekrani. Her hedefin ilerlemesi YALNIZ kendine atanan varliklardan
 * olculur (kati atama); atama yoksa %0. Siralama uzun basip surukleyerek yapilir.
 */
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    onIntent: (GoalsIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            GoalsHeader(state, onIntent)
            when (state.stage) {
                GoalsStage.Loading -> GoalsSkeleton()
                GoalsStage.Empty -> GoalsEmpty(onIntent)
                GoalsStage.Ready -> GoalsBody(state, onIntent, onOpenGoal)
            }
        }
        GoalEditSheet(state.editor, onIntent)
    }
}

// --- Baslik ----------------------------------------------------------------

@Composable
private fun GoalsHeader(state: GoalsUiState, onIntent: (GoalsIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val ready = state.stage == GoalsStage.Ready

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x16, end = Space.x16, top = 2.dp, bottom = Space.x12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hedefler", style = t.h1, color = c.onSurface)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(Space.x8))
            if (ready) {
                SortButton(
                    active = state.sortMode,
                    onClick = { onIntent(GoalsIntent.ToggleSortMode) },
                )
            }
        }
        if (ready) {
            Spacer(Modifier.height(Space.x4))
            Text(
                "${trCount(state.goals.size)} hedef · Her biri kendine atanan " +
                    "varlıkları sayar · Kartları sürükleyerek sıralayın",
                style = t.caption,
                color = c.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun SortButton(active: Boolean, onClick: () -> Unit) {
    val c = KefeTheme.colors
    val border = if (active) c.accent else c.outline
    val content = if (active) c.accent else c.onSurfaceMuted

    Row(
        modifier = Modifier
            .height(Sizes.chipMedium)
            .clip(KefeShapes.pill)
            .border(Sizes.hairline, border, KefeShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x12),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(KefeIcons.ListMenu, null, size = IconSize.tiny, tint = content)
        Text("Sırala", style = KefeTheme.type.caption, color = content)
    }
}

// --- Liste -----------------------------------------------------------------

@Composable
private fun GoalsBody(
    state: GoalsUiState,
    onIntent: (GoalsIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
) {
    // Surukleme sirasinda sira yerel tutulur; birakinca Reorder ile bildirilir.
    var order by remember(state.goals) { mutableStateOf(state.goals) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableFloatStateOf(0f) }
    val gapPx = with(LocalDensity.current) { GoalGap.toPx() }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x16),
    ) {
        order.forEachIndexed { index, goal ->
            if (index > 0) Spacer(Modifier.height(GoalGap))
            val dragging = goal.id == dragId
            GoalCard(
                goal = goal,
                totalWealth = state.wealthByGoal[goal.id] ?: state.totalWealth,
                arrival = state.arrivalByGoal[goal.id],
                sortMode = state.sortMode,
                onClick = { onOpenGoal(goal.id) },
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .onSizeChanged { cardHeight = it.height.toFloat() }
                    .pointerInput(goal.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragId = goal.id
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                onIntent(GoalsIntent.Reorder(order.map { it.id }))
                                dragId = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragId = null
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val step = cardHeight + gapPx
                                val from = order.indexOfFirst { it.id == goal.id }
                                if (step > 0f && from >= 0) {
                                    if (dragOffset > step / 2f && from < order.lastIndex) {
                                        order = order.moved(from, from + 1)
                                        dragOffset -= step
                                    } else if (dragOffset < -step / 2f && from > 0) {
                                        order = order.moved(from, from - 1)
                                        dragOffset += step
                                    }
                                }
                            },
                        )
                    },
            )
        }

        if (state.completed.isNotEmpty()) {
            // 10px liste bosluğu + 2px kart ustu payi
            Spacer(Modifier.height(GoalGap + 2.dp))
            CompletedGroup(
                items = state.completed,
                expanded = state.completedExpanded,
                onToggle = { onIntent(GoalsIntent.ToggleCompleted) },
                onOpen = onOpenGoal,
            )
        }

        // 10px liste bosluğu + 6px buton ustu payi
        Spacer(Modifier.height(GoalGap + 6.dp))
        NewGoalButton(onClick = { onIntent(GoalsIntent.CreateGoal()) })
        Spacer(Modifier.height(Space.x24))
    }
}

private fun List<Goal>.moved(from: Int, to: Int): List<Goal> =
    toMutableList().also { it.add(to, it.removeAt(from)) }

// --- Hedef karti -----------------------------------------------------------

@Composable
private fun GoalCard(
    goal: Goal,
    totalWealth: Double,
    /** Tahmini varis - hesaplanamiyorsa (aylik katki yok) null. */
    arrival: KefeDate?,
    sortMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val main = goal.isMain
    val progress = goal.progress(totalWealth)
    val reached = progress >= 1f

    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            GoalIconBox(goal.iconKey, main)
            Spacer(Modifier.width(Space.x12))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = goal.name,
                        style = t.bodyStrong,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (main) {
                        Spacer(Modifier.width(Space.x8))
                        MainGoalBadge()
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${goal.targetDate.formatMonthYear()} hedef tarihi",
                    style = t.caption,
                    color = c.onSurfaceMuted,
                )
            }
            Spacer(Modifier.width(Space.x12))
            DragHandle(sortMode)
        }

        Spacer(Modifier.height(Space.x14))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = "${Money.tl(totalWealth)} / ${Money.tl(goal.amount)}",
                style = t.body.tabular(),
                color = c.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            Text(
                // Ulasilan hedefte %200 gibi bir sayi bilgi vermez, hata gibi
                // okunur; %100'de durur ve renk ulasildigini soyler.
                //
                // ULASILMADAN %100 YAZILMAZ. Yuzde sifir ondaliga
                // yuvarlaniyordu: %99,6 ilerleme "%100" gorunuyor ama renk
                // hala "ulasilmadi" diyordu - ekran kendi kendisiyle
                // celisiyordu.
                //
                // Yuvarlama KALIYOR, ustune 99 tavani konuyor. Asagi kirpmak
                // da denenebilirdi ama siradan degerleri bozuyor: `progress`
                // bir Float ve 0,35 bellekte 0,34999999 olarak duruyor, yani
                // %35'lik ilerleme "%34" gorunurdu.
                text = if (reached) {
                    Money.ratio(100.0)
                } else {
                    Money.ratio(min(round(progress.toDouble() * 100.0), 99.0))
                },
                style = t.h2.tabular(),
                color = when {
                    reached -> c.positive
                    main -> c.accent
                    else -> c.onSurface
                },
                modifier = Modifier.alignByBaseline(),
            )
        }

        Spacer(Modifier.height(Space.x8))
        KefeProgressBar(
            progress = progress,
            color = when {
                reached -> c.positive
                main -> c.accent
                else -> c.onSurfaceMuted
            },
        )

        Spacer(Modifier.height(Space.x10))
        // Hedefe ULASILMISSA varis tahmini gosterilmez - birikim zaten yetiyorken
        // gelecege tarih vermek celiskili olurdu. Hedefler ayni birikimi
        // paylastigi icin kucuk hedefler bastan karsilanmis olabilir.
        if (reached) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KefeIcon(KefeIcons.Check, null, size = 14.dp, tint = c.positive)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Birikim bu hedefi karşılıyor",
                    style = t.caption,
                    color = c.positive,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KefeIcon(KefeIcons.Clock, null, size = 14.dp, tint = c.onSurfaceMuted)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = goal.arrivalLabel(arrival),
                    style = t.caption,
                    color = c.onSurfaceMuted,
                    // Tasarimda tahmin metninin alti kesikli: kesin bir tarih degil.
                    modifier = Modifier.dashedUnderline(c.outline).padding(bottom = 1.dp),
                )
            }
        }
    }

    if (main) {
        KefeAccentCard(modifier, onClick, PaddingValues(Space.x16), content)
    } else {
        KefeCard(modifier, onClick, PaddingValues(Space.x16), content)
    }
}

@Composable
private fun GoalIconBox(iconKey: String, main: Boolean) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .size(Sizes.avatarLarge)
            .clip(KefeShapes.button)
            .background(if (main) c.accentMuted else c.surfaceSunken),
        contentAlignment = Alignment.Center,
    ) {
        KefeIcon(
            icon = KefeIcons.goalIcon(iconKey),
            contentDescription = null,
            size = IconSize.default,
            tint = if (main) c.accent else c.onSurfaceMuted,
        )
    }
}

@Composable
private fun MainGoalBadge() {
    val c = KefeTheme.colors
    Row(
        modifier = Modifier
            .clip(KefeShapes.pill)
            .background(c.accentMuted)
            .padding(horizontal = Space.x8, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(KefeIcons.Balance, null, size = 10.dp, tint = c.accent)
        Text(
            text = "ana hedef".trUpper(),
            style = KefeTheme.type.label(10, 0.07, FontWeight.Bold),
            color = c.accent,
        )
    }
}

@Composable
private fun DragHandle(active: Boolean) {
    val c = KefeTheme.colors
    Box(Modifier.padding(horizontal = 2.dp, vertical = 6.dp)) {
        KefeIcon(
            icon = KefeIcons.DragHandle,
            contentDescription = "Sürükleyerek sırala",
            size = IconSize.small,
            tint = if (active) c.accent else c.onSurfaceMuted,
        )
    }
}

// --- Tamamlanan hedefler ---------------------------------------------------

@Composable
private fun CompletedGroup(
    items: List<Goal>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = Space.x16, vertical = Space.x14),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KefeIcon(
                icon = if (expanded) KefeIcons.ChevronDown else KefeIcons.ChevronRight,
                contentDescription = null,
                size = IconSize.small,
                tint = c.onSurfaceMuted,
            )
            Spacer(Modifier.width(Space.x10))
            Text(
                "Tamamlanan hedefler",
                style = t.body,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(items.size.toString(), style = t.caption.tabular(), color = c.onSurfaceMuted)
        }

        if (expanded) {
            items.forEach { goal ->
                KefeHairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(goal.id) }
                        .padding(horizontal = Space.x16, vertical = Space.x14),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(KefeShapes.boxSmall)
                            .background(c.surfaceSunken),
                        contentAlignment = Alignment.Center,
                    ) {
                        KefeIcon(KefeIcons.Check, null, size = IconSize.small, tint = c.positive)
                    }
                    Spacer(Modifier.width(Space.x12))
                    Column(Modifier.weight(1f)) {
                        Text(goal.name, style = t.body, color = c.onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = goal.targetDate.formatMonthYear() +
                                trYearLocative(goal.targetDate.year) + " tamamlandı",
                            style = t.micro.tabular(),
                            color = c.onSurfaceMuted,
                        )
                    }
                    Spacer(Modifier.width(Space.x12))
                    Text(
                        Money.tl(goal.amount),
                        style = t.body.tabular(),
                        color = c.onSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewGoalButton(onClick: () -> Unit) {
    val c = KefeTheme.colors
    KefeDashedCard(
        modifier = Modifier.fillMaxWidth().height(Sizes.buttonPrimary),
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KefeIcon(KefeIcons.Plus, null, size = IconSize.medium, tint = c.accent)
            Spacer(Modifier.width(Space.x8))
            Text("Yeni hedef", style = KefeTheme.type.bodyStrong, color = c.accent)
        }
    }
}

// --- Bos durum -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalsEmpty(onIntent: (GoalsIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Space.x16, end = Space.x16, top = Space.x12),
    ) {
        Text("Ne için\nbiriktiriyorsunuz?", style = t.h1, color = c.onSurface)
        Spacer(Modifier.height(Space.x12))
        Text(
            "Bir hedef koyun; Kefe birikiminizin ne kadarının o hedefi " +
                "karşıladığını gösterir.",
            style = t.body.copy(lineHeight = 22.sp),
            color = c.onSurfaceMuted,
        )

        Spacer(Modifier.height(Space.x28))
        Text(
            "hazır öneriler".trUpper(),
            style = t.label(11, 0.10, FontWeight.Bold),
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(Space.x12))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
            verticalArrangement = Arrangement.spacedBy(Space.x8),
        ) {
            GoalSuggestions.forEach { suggestion ->
                SuggestionChip(
                    suggestion = suggestion,
                    onClick = { onIntent(GoalsIntent.CreateGoal(suggestion)) },
                )
            }
        }

        Spacer(Modifier.height(Space.x32))
        KefeDashedCard(contentPadding = PaddingValues(Space.x16)) {
            Row(verticalAlignment = Alignment.Top) {
                KefeIcon(
                    icon = KefeIcons.Info,
                    contentDescription = null,
                    size = IconSize.medium,
                    tint = c.onSurfaceMuted,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Spacer(Modifier.width(Space.x12))
                Text(
                    "Hedefi TL yerine gram altın veya dolar cinsinden de " +
                        "sabitleyebilirsiniz — hedef piyasayla birlikte güncellenir.",
                    style = t.caption.copy(lineHeight = 19.sp),
                    color = c.onSurfaceMuted,
                )
            }
        }
        Spacer(Modifier.height(Space.x24))
    }
}

@Composable
private fun SuggestionChip(suggestion: GoalSuggestion, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Row(
        modifier = Modifier
            .height(Sizes.touchTarget)
            .clip(KefeShapes.pill)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, c.outline, KefeShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x16),
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(
            icon = KefeIcons.goalIcon(suggestion.iconKey),
            contentDescription = null,
            size = IconSize.small,
            tint = c.accent,
        )
        Text(suggestion.name, style = KefeTheme.type.body, color = c.onSurface)
    }
}

// --- Yuklenme --------------------------------------------------------------

@Composable
private fun GoalsSkeleton() {
    Column(
        Modifier.padding(horizontal = Space.x16),
        verticalArrangement = Arrangement.spacedBy(GoalGap),
    ) {
        repeat(3) { KefeSkeletonBlock(height = 158.dp, radius = 16.dp) }
    }
}

// --- Yardimci --------------------------------------------------------------

/**
 * "Tahmini varış ≈ Mart 2029 · 3 ay gecikme".
 * Gecikme metni yalniz tahmin hedef tarihini asiyorsa eklenir.
 */
private fun Goal.arrivalLabel(arrival: KefeDate?): String {
    // Varis IKI ayri sebeple bilinemez ve ikisi ayni sey degil:
    //   - aylik katki yok: birikim kendiliginden buyumez, varis TARIHI YOKTUR;
    //   - katki var ama hedef kirk yillik ufkun disinda kaliyor.
    // Tek metin ikisini de "aylık katkı olmadan" diye anlatiyordu; aylik 4.000
    // TL yazan kullaniciya katki girmedigi soyleniyordu.
    arrival ?: return if (monthlyContribution <= 0.0) {
        "Aylık katkı olmadan varış hesaplanamıyor"
    } else {
        "Bu katkıyla varış çok uzak - hesaplanamıyor"
    }
    val delay = arrival.monthIndex() - targetDate.monthIndex()
    val base = "Tahmini varış ≈ ${arrival.formatMonthYear()}"
    return if (delay > 0) "$base · $delay ay gecikme" else base
}

/** Metnin altina 1dp kesikli cizgi - tasarimdaki `border-bottom: dashed`. */
private fun Modifier.dashedUnderline(color: Color): Modifier = drawBehind {
    val y = size.height - Sizes.hairline.toPx() / 2f
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = Sizes.hairline.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
    )
}

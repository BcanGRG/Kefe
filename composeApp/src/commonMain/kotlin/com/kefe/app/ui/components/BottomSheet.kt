package com.kefe.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

private const val SheetEnterMillis = 240
private const val SheetExitMillis = 200

/**
 * Alt sayfa. Material3 ModalBottomSheet yerine kendi scrim ve panelini cizer -
 * masaustunde surukleme davranisi tutarli olsun diye.
 *
 * Cagiran taraf bunu ekranin en ustundeki bir Box icine yerlestirmelidir.
 *
 * GERI TUSU SAYFAYI KAPATIR. Sayfa bir gezinme girdisi degil, ekranin bir
 * durumu; sistem bunu bilmedigi icin geri tusu sayfayi acik birakip ARKADAKI
 * ekrani geri atiyordu. Kullanici sayfayi kapatmak icin basiyor, kendini baska
 * ekranda buluyordu. Isleyici burada oldugu icin [KefeBottomSheet] kullanan her
 * sayfa bunu kendiliginden alir.
 */
@Composable
fun KefeBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    closeIcon: ImageVector,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type
    val scrimInteraction = remember { MutableInteractionSource() }

    KefeBackHandler(enabled = visible) { onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SheetEnterMillis)),
            exit = fadeOut(tween(SheetExitMillis)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(SheetEnterMillis)) { it } +
                fadeIn(tween(SheetEnterMillis)),
            exit = slideOutVertically(tween(SheetExitMillis)) { it } +
                fadeOut(tween(SheetExitMillis)),
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(KefeShapes.sheet)
                    .background(colors.surfaceElevated)
                    // Panele yapilan dokunuslar scrim'e gecip sayfayi kapatmasin
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Spacer(Modifier.height(Space.x10))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.outline)
                )
                Spacer(Modifier.height(Space.x12))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.x20, end = Space.x12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = type.h2, color = colors.onSurface)
                        if (subtitle != null) {
                            Spacer(Modifier.height(Space.x4))
                            Text(
                                text = subtitle,
                                style = type.caption,
                                color = colors.onSurfaceMuted,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(Sizes.touchTarget)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = closeIcon,
                            contentDescription = "Kapat",
                            tint = colors.onSurfaceMuted,
                            modifier = Modifier.size(IconSize.medium),
                        )
                    }
                }

                if (header != null) {
                    Spacer(Modifier.height(Space.x12))
                    header()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = Space.x20,
                            end = Space.x20,
                            top = Space.x16,
                            bottom = Space.x16,
                        ),
                    content = content,
                )

                if (footer != null) {
                    footer()
                }
                Spacer(Modifier.height(Space.x8))
            }
        }
    }
}

/**
 * Cok adimli akislarda ilerleme noktalari. Aktif adim hem genisligi hem
 * rengiyle ayrisir - renk tek sinyal degildir.
 */
@Composable
fun KefeStepDots(
    count: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val colors = KefeTheme.colors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.x4 + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            val active = i == activeIndex
            val width: Dp by animateDpAsState(
                targetValue = if (active) 18.dp else 6.dp,
                animationSpec = tween(durationMillis = 200),
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.accent else colors.outline)
            )
        }
    }
}

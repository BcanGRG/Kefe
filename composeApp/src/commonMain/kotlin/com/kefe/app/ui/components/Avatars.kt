package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes

/** Avatar boyutuna gore bas harf punto secimi. */
private fun initialsFontSize(size: Dp): TextUnit = when {
    size <= Sizes.avatarSmall -> 10.sp
    size <= Sizes.avatarMedium -> 11.sp
    else -> 15.sp
}

/** Bas harfleri iki karaktere indirger ve Turkce kurallarina gore buyutur. */
private fun shortInitials(initials: String): String =
    initials.trim().take(2).trUpper()

/**
 * Bas harf avatari. Zemin rengi index'e gore iki notr tondan biri -
 * varlik sinifi paletinden bagimsizdir.
 */
@Composable
fun KefeAvatar(
    initials: String,
    index: Int,
    size: Dp = Sizes.avatarMedium,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val background = if (index % 2 == 0) colors.avatarA else colors.avatarB
    val fontSize = initialsFontSize(size)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shortInitials(initials),
            style = KefeTheme.type.micro.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.15f,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.02.em,
            ),
            color = colors.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Bindirmeli avatar dizisi. `max` sayisindan fazlasi "+N" olarak ozetlenir.
 */
@Composable
fun KefeAvatarStack(
    members: List<Pair<String, Int>>,
    max: Int = 2,
    size: Dp = Sizes.avatarSmall,
    modifier: Modifier = Modifier,
) {
    if (members.isEmpty()) return

    val colors = KefeTheme.colors
    val shown = members.take(max)
    val overflow = members.size - shown.size
    val fontSize = initialsFontSize(size)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { (initials, index) ->
            KefeAvatar(
                initials = initials,
                index = index,
                size = size,
                modifier = Modifier.border(1.5.dp, colors.surface, CircleShape),
            )
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colors.surfaceSunken)
                    .border(1.5.dp, colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    style = KefeTheme.type.micro.copy(
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.15f,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

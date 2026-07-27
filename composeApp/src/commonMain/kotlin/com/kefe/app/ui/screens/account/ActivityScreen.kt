package com.kefe.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Aktivite akisi: kim ne zaman ne ekledi. Paylasimin guven ayagi burasidir,
 * bu yuzden kayitlar silinmez - yalniz suzulur.
 */
@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onIntent: (ActivityIntent) -> Unit,
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        AccountTopBar(title = "Aktivite", onBack = onBack)

        if (state.filters.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KefeTheme.colors.surface)
                    .padding(start = Space.x16, end = Space.x16, bottom = Space.x12),
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
            ) {
                state.filters.forEachIndexed { index, option ->
                    AccountFilterChip(
                        text = option.label,
                        selected = index == state.selectedFilterIndex,
                        onClick = { onIntent(ActivityIntent.SelectFilter(index)) },
                    )
                }
            }
        }

        if (state.isEmpty) {
            ActivityEmpty(onAddTransaction = onAddTransaction)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = Space.x16,
                    end = Space.x16,
                    bottom = Space.x24,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.x16),
            ) {
                items(state.days.size) { dayIndex ->
                    ActivityDayGroup(state.days[dayIndex])
                }
            }
        }
    }
}

@Composable
private fun ActivityDayGroup(day: ActivityDay) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column {
        Text(
            text = day.label.trUpper(),
            style = t.label(11, 0.08, FontWeight.Bold),
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(Space.x8))
        AccountGroupCard {
            day.rows.forEachIndexed { index, row ->
                if (index > 0) KefeHairline()
                ActivityRowItem(row)
            }
        }
    }
}

@Composable
private fun ActivityRowItem(row: ActivityRow) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x14, vertical = Space.x12),
        horizontalArrangement = Arrangement.spacedBy(Space.x10),
        verticalAlignment = Alignment.Top,
    ) {
        AccountAvatar(
            initials = row.memberInitials,
            index = row.memberIndex,
            size = Sizes.avatarMedium,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 1.dp),
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(row.memberName)
                    }
                    append(" · ")
                    append(row.description)
                },
                style = t.caption,
                color = c.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.timeLabel, style = t.micro.tabular(), color = c.onSurfaceMuted)
                if (row.isManualPrice) ManualPriceTag()
            }
        }

        Text(
            text = row.amountText,
            style = t.caption.copy(fontWeight = FontWeight.SemiBold).tabular(),
            color = when (row.amountTone) {
                ActivityAmountTone.Outgoing -> c.negative
                ActivityAmountTone.Incoming -> c.onSurface
                ActivityAmountTone.Neutral -> c.onSurfaceMuted
            },
        )
    }
}

/**
 * "Elle fiyat" isareti. Ortak `KefeManualBadge` metni buyuk harfe cevirir ve
 * kalin yazar; buradaki rozet tasarimda kucuk harf ve yari kalindir.
 */
@Composable
private fun ManualPriceTag() {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .clip(KefeShapes.pill)
            .background(c.surfaceSunken)
            .border(Sizes.hairline, c.outline, KefeShapes.pill)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            "Elle fiyat",
            style = KefeTheme.type.nano.copy(fontWeight = FontWeight.SemiBold),
            color = c.onSurfaceMuted,
        )
    }
}

/** Suzgec cipi: secilide vurgu zemini, metin rengi degismez - okunur kalir. */
@Composable
private fun AccountFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .height(Sizes.chipMedium)
            .clip(KefeShapes.pill)
            .background(if (selected) c.accentMuted else Color.Transparent)
            .border(
                width = Sizes.hairline,
                color = if (selected) c.accent else c.outline,
                shape = KefeShapes.pill,
            )
            .clickable(indication = null, interactionSource = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Space.x16),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) c.onSurface else c.onSurfaceMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActivityEmpty(onAddTransaction: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x24, end = Space.x24, top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            KefeIcon(KefeIcons.Clock, null, size = 26.dp, tint = c.onSurfaceMuted)
        }
        Spacer(Modifier.height(Space.x16))
        Text("Henüz hareket yok", style = t.h2, color = c.onSurface)
        Spacer(Modifier.height(Space.x8))
        Text(
            "İlk kaydınızı ekleyin; kim ne zaman ne ekledi burada sırayla görünür.",
            style = t.caption.copy(lineHeight = 19.sp),
            color = c.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        Spacer(Modifier.height(Space.x20))
        AccountFilledButton(
            text = "İşlem ekle",
            onClick = onAddTransaction,
            height = Sizes.fieldDefault,
        )
    }
}

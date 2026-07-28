package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Geri alinamaz bir islem icin onay kutusu.
 *
 * Kurallar hep ayni: onay dugmesi VARSAYILAN DEGILDIR - kutuyu kapatmak tek
 * dokunusla, silmek bilerek yapilir - ve [message] neyin kaybedilecegini tek tek
 * yazar. Kullanici neyi kaybettigini bilmeli.
 */
@Composable
fun KefeConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Vazgeç",
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(indication = null, interactionSource = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(Space.x24)
                .widthIn(max = Sizes.formMaxWidth)
                .clip(KefeShapes.card)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, KefeShapes.card)
                // Kutuya dokunmak kapatmasin: disaridaki tiklama yukaridaki
                // katmana ait, burada tuketilir.
                .clickable(indication = null, interactionSource = null, onClick = {})
                .padding(Space.x20),
        ) {
            Text(title, style = t.h2, color = c.onSurface)
            Spacer(Modifier.height(Space.x10))
            Text(message, style = t.caption, color = c.onSurfaceMuted)
            Spacer(Modifier.height(Space.x20))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x10)) {
                DialogButton(
                    text = dismissLabel,
                    textColor = c.onSurface,
                    background = c.surfaceSunken,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                DialogButton(
                    text = confirmLabel,
                    textColor = c.onAccent,
                    background = c.negative,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    textColor: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(Sizes.fieldDefault)
            .clip(KefeShapes.button)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KefeTheme.type.bodyStrong, color = textColor)
    }
}

package com.kefe.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * "Bu telefon kimin?" adimi.
 *
 * Iki ad girilir, sonra bu cihazin hangisi oldugu secilir. Secim onemlidir: bu
 * telefondan eklenen her islem secilen profile yazilir. Ikinci telefonda adlar
 * senkrondan gelir, kullanici yalniz "hangisi sensin" der.
 */
@Composable
fun ProfileSetupScreen(
    state: ProfileSetupUiState,
    onIntent: (ProfileSetupIntent) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = Sizes.formMaxWidth)
                .fillMaxWidth()
                .padding(start = Space.x24, end = Space.x24, top = Space.x40),
        ) {
            Text("Profiller", style = t.h1, color = c.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "İki profil oluşturun. Bu telefondan eklediğiniz her kayıt, " +
                    "seçtiğiniz profile yazılır.",
                style = t.body.copy(lineHeight = 22.sp),
                color = c.onSurfaceMuted,
            )

            Spacer(Modifier.height(Space.x28))
            Text("İSİMLER", style = t.label(11, 0.06), color = c.onSurfaceMuted)
            Spacer(Modifier.height(Space.x10))
            NameField(
                value = state.ownerName,
                onValueChange = { onIntent(ProfileSetupIntent.ChangeOwnerName(it)) },
                placeholder = "Örn. Volkan",
            )
            Spacer(Modifier.height(Space.x10))
            NameField(
                value = state.partnerName,
                onValueChange = { onIntent(ProfileSetupIntent.ChangePartnerName(it)) },
                placeholder = "Örn. Ayşe",
            )

            Spacer(Modifier.height(Space.x28))
            Text("BU TELEFON KİMİN?", style = t.label(11, 0.06), color = c.onSurfaceMuted)
            Spacer(Modifier.height(Space.x10))
            // Isim bosken bile secim yapilabilsin diye yer tutucu ad gosterilir.
            DeviceChoice(
                name = state.ownerName.ifBlank { "1. profil" },
                index = 0,
                selected = state.thisDeviceIsOwner,
                onClick = { onIntent(ProfileSetupIntent.SelectThisDevice(true)) },
            )
            Spacer(Modifier.height(Space.x8))
            DeviceChoice(
                name = state.partnerName.ifBlank { "2. profil" },
                index = 1,
                selected = !state.thisDeviceIsOwner,
                onClick = { onIntent(ProfileSetupIntent.SelectThisDevice(false)) },
            )

            Spacer(Modifier.height(Space.x28))
            AccountFilledButton(
                text = if (state.saving) "Kaydediliyor…" else "Devam",
                onClick = { onIntent(ProfileSetupIntent.Save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave,
            )
            Spacer(Modifier.height(Space.x24))
        }
    }
}

/** Secilebilen profil satiri: avatar + ad, secili ise accent kenarlik. */
@Composable
private fun DeviceChoice(
    name: String,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.buttonPrimary)
            .clickable(onClick = onClick)
            .background(
                if (selected) c.accentMuted else c.surfaceElevated,
                KefeShapes.button,
            )
            .border(
                Sizes.hairline,
                if (selected) c.accent else c.outline,
                KefeShapes.button,
            )
            .padding(horizontal = Space.x14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
    ) {
        AccountAvatar(
            initials = name.trim().take(1),
            index = index,
            size = 28.dp,
            fontSize = 12.sp,
        )
        Text(
            text = name,
            style = t.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = c.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("Bu telefon", style = t.micro, color = c.accent)
        }
    }
}

/** Isim alani - LoginScreen'in e-posta alaninin ikonsuz karsiligi. */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused || value.isNotEmpty()) c.accent else c.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.buttonPrimary)
            .background(c.surfaceElevated, KefeShapes.button)
            .border(Sizes.hairline, borderColor, KefeShapes.button)
            .padding(horizontal = Space.x14),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = t.body.copy(color = c.onSurface),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            cursorBrush = SolidColor(c.accent),
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = t.body, color = c.onSurfaceMuted, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Profiller - Ayarlar'dan acilir.
 *
 * Iki profil satiri: ada dokununca yeniden adlandirma, satira dokununca bu
 * telefonu o profile baglama. Cok kullanicili "Paylasim" ekraninin (davet kodu,
 * QR, izin, uye cikarma) yerini aldi.
 */
@Composable
fun ProfilesScreen(
    state: ProfilesUiState,
    onIntent: (ProfilesIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AccountTopBar(title = "Profiller", onBack = onBack)

            Column(Modifier.fillMaxWidth().padding(horizontal = Space.x24)) {
                Text(
                    "Bu telefondan eklediğiniz kayıtlar, işaretli profile yazılır. " +
                        "Adı düzenlemek için dokunun.",
                    style = t.caption.copy(lineHeight = 18.sp),
                    color = c.onSurfaceMuted,
                )
                Spacer(Modifier.height(Space.x16))

                state.profiles.forEach { profile ->
                    ProfileRowView(
                        profile = profile,
                        onSelect = { onIntent(ProfilesIntent.SetThisDevice(profile.id)) },
                        onRename = { onIntent(ProfilesIntent.OpenRename(profile.id)) },
                    )
                    Spacer(Modifier.height(Space.x8))
                }
            }
        }

        RenameSheet(edit = state.editing, onIntent = onIntent)
    }
}

@Composable
private fun ProfileRowView(
    profile: ProfileRow,
    onSelect: () -> Unit,
    onRename: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.buttonPrimary)
            .background(
                if (profile.isThisDevice) c.accentMuted else c.surfaceElevated,
                KefeShapes.button,
            )
            .border(
                Sizes.hairline,
                if (profile.isThisDevice) c.accent else c.outline,
                KefeShapes.button,
            )
            .padding(horizontal = Space.x14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
    ) {
        // Satirin govdesi: bu telefonu bu profile baglar.
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x12),
        ) {
            AccountAvatar(
                initials = profile.initials,
                index = profile.index,
                size = 32.dp,
                fontSize = 13.sp,
            )
            Column {
                Text(
                    profile.name,
                    style = t.body.copy(
                        fontWeight = if (profile.isThisDevice) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = c.onSurface,
                )
                if (profile.isThisDevice) {
                    Text("Bu telefon", style = t.micro, color = c.accent)
                }
            }
        }
        // Kalem: adi degistir.
        Text(
            text = "Düzenle",
            style = t.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.onSurfaceMuted,
            modifier = Modifier
                .clickable(onClick = onRename)
                .padding(horizontal = Space.x8, vertical = Space.x4),
        )
    }
}

@Composable
private fun RenameSheet(edit: ProfileNameEdit?, onIntent: (ProfilesIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeBottomSheet(
        visible = edit != null,
        onDismiss = { onIntent(ProfilesIntent.DismissRename) },
        title = "Profil adı",
        closeIcon = KefeIcons.Close,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val value = edit?.name.orEmpty()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.buttonPrimary)
                .background(c.surfaceElevated, KefeShapes.button)
                .border(
                    Sizes.hairline,
                    if (focused || value.isNotEmpty()) c.accent else c.outline,
                    KefeShapes.button,
                )
                .padding(horizontal = Space.x14),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onIntent(ProfilesIntent.ChangeName(it)) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = t.body.copy(color = c.onSurface),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                cursorBrush = SolidColor(c.accent),
                interactionSource = interaction,
            )
        }
        Spacer(Modifier.height(Space.x16))
        KefePrimaryButton(
            text = "Kaydet",
            onClick = { onIntent(ProfilesIntent.SaveName) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.x8))
    }
}

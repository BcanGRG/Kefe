package com.kefe.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/** Hosgeldin'deki iki yol. */
enum class WelcomeChoice {
    /** Hesapsiz: tanitim, sonra profil olusturma. */
    OnDevice,

    /** Hesapla: e-posta koduyla giris (ya da hesap acma), sonra profil adimi. */
    WithAccount,
}

/** Bir hosgeldin karti: ne oldugu ve ne anlama geldigi. */
data class WelcomeOption(
    val choice: WelcomeChoice,
    val title: String,
    val body: String,
)

/**
 * Hosgeldin kartlari. SAF: testte denenebilsin (bkz. WelcomeTest).
 *
 * IKI KART ESIT AGIRLIKTA, HICBIRI SECILI GELMEZ. Hangisinin dogru oldugunu
 * yalniz kullanici bilir: tek telefonda kullanacak biri icin hesap gereksiz bir
 * adim, esiyle kullanacak biri icin hesapsiz baslamak sonradan baglanma isi.
 * NEYDI: ilk ekran e-posta formuydu, hesapsiz yol altta kucuk bir baglantiydi
 * ("Hesapsız başla") - hesap istemeyen de once e-posta sorulan bir ekranla
 * karsilaniyordu.
 *
 * Bulut bu surumde yapilandirilmamissa hesap karti HIC cizilmez: acilamayacak
 * bir hesaba giden kart bozuk gorunur.
 */
fun welcomeOptions(cloudConfigured: Boolean): List<WelcomeOption> = buildList {
    add(
        WelcomeOption(
            choice = WelcomeChoice.OnDevice,
            title = "Bu cihazda kullan",
            body = "Hesap gerekmez. Kayıtlarınız yalnız bu cihazda saklanır. " +
                "İsterseniz sonra Ayarlar'dan hesaba bağlarsınız.",
        ),
    )
    if (cloudConfigured) {
        add(
            WelcomeOption(
                choice = WelcomeChoice.WithAccount,
                title = "Hesapla, iki telefonda",
                body = "Eşinizle aynı birikimi iki telefonda görün. E-postanıza gelen " +
                    "kodla girersiniz; bu e-postayla hesap yoksa açılır.",
            ),
        )
    }
}

/**
 * Alttaki not: daha once Kefe kullanmis biri hangi karti secmeli. Yeni telefona
 * gecen kullanici "Bu cihazda kullan"i secip bos bir uygulamayla kalmasin -
 * kayitlari hesaptadir, ayni e-postayla girince gelir. Hesap yoksa not da yok.
 */
fun welcomeFooter(cloudConfigured: Boolean): String? =
    if (cloudConfigured) {
        "Daha önce Kefe kullandınız mı? 'Hesapla, iki telefonda'yı seçip aynı " +
            "e-postayla girin — kayıtlarınız bu cihaza gelir."
    } else {
        null
    }

/**
 * Ilk acilis: "Nasil kullanmak istersiniz?". Yalniz yigin KOKUNDE (bkz. rootFor).
 */
@Composable
fun WelcomeScreen(
    cloudConfigured: Boolean,
    onChoose: (WelcomeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    // Masaustunde kartlar pencere boyunca uzamasin: form genisliginde, ortada.
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
                .padding(start = Space.x24, end = Space.x24, top = Space.x40, bottom = Space.x24),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(Space.x24))
                        .background(c.accentMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    KefeIcon(KefeIcons.Balance, null, size = Space.x40, tint = c.accent)
                }
                Spacer(Modifier.height(Space.x20))
                Text("Kefe", style = t.h1, color = c.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Birikiminiz bir kefede,\nhedefiniz diğerinde.",
                    style = t.body.copy(lineHeight = 22.sp),
                    color = c.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(36.dp))
            Text("Nasıl kullanmak istersiniz?".trUpper(), style = t.label(11, 0.06), color = c.onSurfaceMuted)
            Spacer(Modifier.height(Space.x10))

            welcomeOptions(cloudConfigured).forEachIndexed { index, option ->
                if (index > 0) Spacer(Modifier.height(Space.x10))
                WelcomeCard(
                    icon = when (option.choice) {
                        WelcomeChoice.OnDevice -> KefeIcons.Device
                        WelcomeChoice.WithAccount -> KefeIcons.TwoPhones
                    },
                    title = option.title,
                    body = option.body,
                    onClick = { onChoose(option.choice) },
                )
            }

            welcomeFooter(cloudConfigured)?.let { footer ->
                Spacer(Modifier.height(Space.x16))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
                    KefeIcon(
                        icon = KefeIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(top = 2.dp),
                        size = 15.dp,
                        tint = c.onSurfaceMuted,
                    )
                    Text(footer, style = t.micro.copy(lineHeight = 17.sp), color = c.onSurfaceMuted)
                }
            }
        }
    }
}

/**
 * Hosgeldin karti. Iki kart AYNI bicimde: dolu/vurgulu bir "birincil" yok,
 * cunku ikisinden biri digerinden daha dogru degil.
 */
@Composable
private fun WelcomeCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, if (hovered) c.accent else c.outline, KefeShapes.card)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(Space.x16),
        horizontalArrangement = Arrangement.spacedBy(Space.x14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Space.x40)
                .clip(RoundedCornerShape(Space.x12))
                .background(c.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            KefeIcon(icon, null, size = 22.dp, tint = c.accent)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.height(Space.x4))
            Text(body, style = t.caption.copy(lineHeight = 18.sp), color = c.onSurfaceMuted)
        }
        KefeIcon(KefeIcons.ChevronRight, null, size = 18.dp, tint = c.onSurfaceMuted)
    }
}

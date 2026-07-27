package com.kefe.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 8pt grid, 4pt yarim adim. Handoff'ta kullanilan tam deger seti:
 * 4 · 8 · 10 · 12 · 14 · 16 · 20 · 24 · 28 · 32 · 40
 */
@Immutable
object Space {
    val x4: Dp = 4.dp
    val x8: Dp = 8.dp
    val x10: Dp = 10.dp
    val x12: Dp = 12.dp
    val x14: Dp = 14.dp
    val x16: Dp = 16.dp
    val x20: Dp = 20.dp
    val x24: Dp = 24.dp
    val x28: Dp = 28.dp
    val x32: Dp = 32.dp
    val x40: Dp = 40.dp
}

@Immutable
object Radius {
    val card: Dp = 16.dp
    val button: Dp = 12.dp
    val boxSmall: Dp = 10.dp
    val boxMedium: Dp = 14.dp
    val sheetTop: Dp = 24.dp
    val fab: Dp = 20.dp
    val pill: Dp = 999.dp
}

@Immutable
object KefeShapes {
    val card = RoundedCornerShape(Radius.card)
    val button = RoundedCornerShape(Radius.button)
    val boxSmall = RoundedCornerShape(Radius.boxSmall)
    val boxMedium = RoundedCornerShape(Radius.boxMedium)
    val fab = RoundedCornerShape(Radius.fab)
    val pill = RoundedCornerShape(Radius.pill)
    val sheet = RoundedCornerShape(topStart = Radius.sheetTop, topEnd = Radius.sheetTop)
}

@Immutable
object IconSize {
    /** Varsayilan: 24px kutu, outline, 2px stroke. */
    val default: Dp = 24.dp
    val medium: Dp = 20.dp
    val small: Dp = 18.dp
    val tiny: Dp = 16.dp
}

@Immutable
object Sizes {
    /** Erisilebilirlik: dokunma hedefi min 44x44. */
    val touchTarget: Dp = 44.dp
    val hairline: Dp = 1.dp

    // Telefon iskeleti
    val statusBar: Dp = 44.dp

    /** 8dp ust dolgu + 52dp sekme + 18dp alt guvenli alan. */
    val bottomNav: Dp = 78.dp
    val fabSize: Dp = 56.dp

    // Bilesenler
    val buttonPrimary: Dp = 52.dp
    val fieldDefault: Dp = 48.dp
    val fieldLarge: Dp = 56.dp
    val fieldAmount: Dp = 60.dp
    val segment: Dp = 36.dp
    val chipSmall: Dp = 32.dp
    val chipMedium: Dp = 36.dp
    val chipLarge: Dp = 40.dp
    val avatarSmall: Dp = 24.dp
    val avatarMedium: Dp = 28.dp
    val avatarLarge: Dp = 40.dp
    val progressBar: Dp = 8.dp
    val progressBarThin: Dp = 4.dp

    // Tablet / masaustu
    val railWidth: Dp = 92.dp
    val navWidth: Dp = 240.dp
    val sidePanelWidth: Dp = 320.dp

    /**
     * Ekran icerigi bu genisligi asmaz.
     *
     * Tasarimin masaustu duzeni 1440 = 240 (nav) + 880 (icerik) + 320 (piyasa
     * paneli). Piyasa paneli yalniz Ozet'te var; diger ekranlar sinirsiz kalinca
     * 1200px'e yayiliyor ve satirin etiketi solda, tutari sagda kalip arada
     * ~900px bosluk olusuyor. Ozet'in icerik kolonuyla ayni sayiyi kullanmak
     * sekme degistirince baslik hizasinin kaymamasini da saglar.
     *
     * Telefon (390) ve tablette (840 - 92 ray = 748) sinirin altinda kalindigi
     * icin bu olcunun oralarda etkisi yoktur.
     */
    val contentMaxWidth: Dp = 880.dp

    /** Giris ve onboarding formu: liste degil form oldugu icin daha dar. */
    val formMaxWidth: Dp = 440.dp
}

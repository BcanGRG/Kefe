package com.kefe.app.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Acilis ekrani: zincir sarkarak yerine oturur.
 *
 * NEDEN ANIMATEDVECTORDRAWABLE DEGIL: marka isareti zaten ortak kodda ve
 * [KefeMark] surekli bir `progress` parametresi aliyor - zincirin sarkmasi
 * hesaplanan bir egri, cizilmis bir sekil degil. Ayni animasyonu Android'de XML,
 * iOS'ta baska bir sey olarak ikinci kez yazmak, marka geometrisinin iki ayri
 * yerde yasamasi demekti.
 *
 * SISTEM SPLASH'I ILE IS BOLUMU: sistem splash'i yalnizca surec baslarken gecen
 * 50-200 ms'lik boslugu doldurur ve durgun ikonu gosterir. Marka ani burasidir.
 * Sistem tarafina 2 saniye saydirmaya calismak (windowSplashScreenAnimationDuration)
 * ise sonuc vermez: Android o sureyi kirpar, karsiliginda soguk acilisi uzatir.
 */
@Composable
fun KefeSplash(onFinished: () -> Unit) {
    val c = KefeTheme.colors

    // Sarkma DURGUN DEGERDEN baslar, sifirdan degil.
    //
    // Sifirdan baslatmak zinciri duz bir cizgiye indiriyordu ve o kare bos
    // gorunuyordu: sistemin acilis penceresi dolu zinciri gosterip biraktigi
    // anda isaret ortadan kayboluyor, sonra yeniden sarkiyordu. Iki ayri
    // hareket gibi okunuyordu.
    //
    // Simdi ayni yerden devralinir ve zincire yalniz bir HIZ verilir: yeni
    // asilmis bir zincir gibi sallanip yerine oturur. Sistem penceresiyle arada
    // kopukluk kalmaz.
    val sag = remember { Animatable(KefeMarkStaticProgress) }
    val wordmark = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            sag.animateTo(
                targetValue = KefeMarkStaticProgress,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessVeryLow,
                ),
                initialVelocity = SettleVelocity,
            )
        }
        // Ad, zincir sallanirken belirir - ardisik degil es zamanli; toplam sure
        // iki saniyeyi asmasin.
        wordmark.animateTo(1f, tween(WordmarkMillis, easing = LinearOutSlowInEasing))
        delay(HoldMillis)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(c.surface),
        contentAlignment = Alignment.Center,
    ) {
        KefeLogoVertical(
            markSize = 72.dp,
            progress = sag.value,
            // Ad, zincir yerine oturduktan SONRA belirir. Ikisi ayni anda
            // girseydi goz nereye bakacagini bilemez, hareket de kaybolurdu.
            modifier = Modifier.alpha(wordmark.value),
        )
    }
}

/**
 * Zincire verilen ilk hiz. Sarkma araligi kucuk (0..1) oldugu icin deger de
 * kucuk; buyugu zinciri kopmus gibi savuruyor.
 */
private const val SettleVelocity = 1.6f

/** Adin belirmesi. */
private const val WordmarkMillis = 450

/** Isareti okumak icin birakilan pay - toplam ~2 saniye. */
private const val HoldMillis = 1_200L

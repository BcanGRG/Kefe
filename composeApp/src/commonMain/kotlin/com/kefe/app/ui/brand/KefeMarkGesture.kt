package com.kefe.app.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kefe.app.ui.theme.KefeTheme

/**
 * Imza jesti: hat gergin baslar (progress 0), yuk binince hedef sarkmayi asar ve
 * iki salinimla oturur. Kutlama hareketin kendisidir - parilti, konfeti yok.
 *
 * Yay: stiffness 170, damping 14, mass 1.
 * dampingRatio = damping / (2 * sqrt(stiffness * mass)) = 14 / (2 * sqrt(170)) ~= 0,537.
 * Bu oranin asimi exp(-pi * z / sqrt(1 - z^2)) ~= %13,5; hedeflenen %12 asma tam olarak
 * bunu gerektirdigi icin hazir sabitler (MediumBouncy = 0,5) yerine hesaplanan deger
 * kullanilir. Ikinci salinim %1,8'de kalir, ucuncusu gorulmez.
 */
const val MarkGestureDampingRatio: Float = 0.537f

/** Tam surum: ~620ms'de oturur. */
const val MarkGestureStiffness: Float = 170f

/**
 * Kisa surum: ~280ms. Yay suresi 1/sqrt(stiffness) ile olceklendigi icin
 * 170 * (620/280)^2 ~= 833. Sonmeme orani ayni tutulur, asma korunur.
 */
const val MarkGestureStiffnessShort: Float = 833f

/**
 * Jesti oynatir ve o anki sarkma degerini dondurur.
 *
 * [trigger] degistiginde hat gergine (0) doner ve jest bastan oynar. Yalniz [target]
 * degistiyse gergine donulmez, mevcut degerden yeni hedefe yaylanir - bir tutar
 * guncellemesinde isaretin sifira dusup zipmasi hata gibi okunur.
 */
@Composable
fun rememberMarkGesture(target: Float, trigger: Any?, short: Boolean = false): Float {
    val clamped = target.coerceIn(0f, 1f)
    val anim = remember { Animatable(0f) }
    val last = remember { GestureTriggerHolder() }
    LaunchedEffect(trigger, clamped, short) {
        val replay = last.value === NoTrigger || last.value != trigger
        last.value = trigger
        if (replay) anim.snapTo(0f)
        anim.animateTo(clamped, markGestureSpring(short))
    }
    return anim.value
}

/** Jesti oynayan isaret. Statik hal icin dogrudan KefeMark kullanilir. */
@Composable
fun KefeAnimatedMark(
    progress: Float,
    trigger: Any?,
    modifier: Modifier = Modifier,
    color: Color = KefeTheme.colors.accent,
    short: Boolean = false,
    detail: MarkDetail? = null,
) {
    val sag = rememberMarkGesture(target = progress, trigger = trigger, short = short)
    KefeMark(progress = sag, modifier = modifier, color = color, detail = detail)
}

private fun markGestureSpring(short: Boolean): AnimationSpec<Float> = spring<Float>(
    dampingRatio = MarkGestureDampingRatio,
    stiffness = if (short) MarkGestureStiffnessShort else MarkGestureStiffness,
)

/** Recomposition tetiklemeden onceki trigger'i tutar. */
private class GestureTriggerHolder(var value: Any? = NoTrigger)

/** "Henuz oynamadi" isareti; trigger'in kendisi null olabildigi icin ayri sentinel. */
private val NoTrigger: Any = Any()

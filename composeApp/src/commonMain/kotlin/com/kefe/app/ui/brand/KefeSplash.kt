package com.kefe.app.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Acilis ekrani: zincir sistemin acilis penceresinden devralinir, sarkarak
 * yerine oturur, ad belirir, isaret cozulur.
 *
 * NEDEN ANIMATEDVECTORDRAWABLE DEGIL: marka isareti zaten ortak kodda ve
 * [KefeMark] surekli bir `progress` parametresi aliyor - zincirin sarkmasi
 * hesaplanan bir egri, cizilmis bir sekil degil. Ayni animasyonu Android'de XML,
 * iOS'ta baska bir sey olarak ikinci kez yazmak, marka geometrisinin iki ayri
 * yerde yasamasi demekti.
 *
 * "IKI FARKLI EKRAN" SORUNU. Kullanicinin gordugu sey suydu: sistem penceresi
 * zinciri BUYUK (~156dp), askisiz ve ekranin tam ortasinda gosteriyordu; hemen
 * ardindan Compose ayni zinciri KUCUK (~50dp), askili, kelime isaretiyle
 * birlikte ve daha YUKARIDA ciziyordu. Boy, konum ve icerik ayni anda
 * degisince goz bunu tek bir acilis olarak degil, arka arkaya acilan iki ekran
 * olarak okuyordu.
 *
 * Cozum uc parcali:
 *   1. Sistem cizimi kucultuldu (ic_splash_mark.xml, olcek 0.72 -> 0.40).
 *   2. Isaret burada EKRANIN ORTASINDAN baslar ve kilit konumuna suzulur -
 *      yani sistem penceresinin biraktigi yerden devralir. Kalan boy farkini
 *      [HandoffScale] kapatir.
 *   3. Sistem penceresi kesilerek degil soldurularak birakilir (MainActivity).
 *
 * TEK ZAMAN EKSENI. Once uc ayri [Animatable] vardi ve aralarinda 1,2 saniyelik
 * OLU bir bekleme duruyordu; hareket "once su, sonra bu" diye bolunuyordu.
 * Artik her sey tek bir dogrusal saatten (t) turer, asamalar ORTUSUR ve hicbir
 * yerde durus yoktur.
 */
@Composable
fun KefeSplash(onFinished: () -> Unit) {
    val c = KefeTheme.colors

    // Dogrusal saat: egriler asagida, her asama kendi diliminde.
    val t = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(TotalMillis, easing = LinearEasing))
        onFinished()
    }

    val time = t.value

    // 1) Devir teslim: sistem isaretinin boyundan ve konumundan kendi kilidine.
    //
    // HICBIR SEY [HandoffStart]'tan ONCE KIMILDAMAZ. Sistem penceresi 260ms
    // boyunca soluyor; Compose bu sirada isareti tam olarak onun boyunda ve
    // konumunda cizer, yani iki katman ust uste AYNI seyi gosterir. Once hareket
    // hemen basliyordu ve solmakta olan sistem isaretiyle kucumekte olan Compose
    // isareti yan yana gorunuyordu - tek hareket yerine iki ayri sekil.
    val handoff = FastOutSlowInEasing.transform(phase(time, HandoffStart, HandoffEnd))
    val scale = HandoffScale + (1f - HandoffScale) * handoff
    val markShift = MarkLockOffset * handoff

    // 2) Zincir: sonumlu salinim. Yeni asilmis bir zincir gibi birkac kez gidip
    //    gelir ve durgun degerine oturur. Salinim da devir teslimden SONRA
    //    baslar; sistem isareti durgunken altta zincir sallanirsa goz iki ayri
    //    hareket goruyor.
    val swing = phase(time, HandoffStart, SwingEnd)
    val sag = KefeMarkStaticProgress +
        SwingAmplitude * exp(-SwingDecay * swing) * sin(swing * SwingTurns * PI.toFloat())

    // 3) Ad: zincir HALA sallanirken belirir - ardisik degil ortusuk. Hafifce
    //    yukari suzulur ki beliren degil yerine oturan bir sey gibi okunsun.
    val wordmark = FastOutSlowInEasing.transform(phase(time, WordmarkStart, WordmarkEnd))

    // 4) Cikis: kilit hafifce buyuyup cozulur. Son kare duz zemindir, yani
    //    uygulamanin ilk karesiyle AYNI renk - ucuncu bir kesme olusmaz.
    val exit = FastOutSlowInEasing.transform(phase(time, ExitStart, 1f))
    val exitAlpha = (1f - exit).coerceIn(0f, 1f)
    val exitScale = 1f + ExitGrowth * exit

    Box(
        modifier = Modifier.fillMaxSize().background(c.surface),
        contentAlignment = Alignment.Center,
    ) {
        KefeMark(
            progress = sag,
            color = c.accent,
            modifier = Modifier
                .offset(y = markShift)
                .size(MarkSize)
                .scale(scale * exitScale)
                .alpha(exitAlpha),
        )
        KefeWordmark(
            color = c.onSurface,
            height = MarkSize * WordmarkHeightRatio,
            modifier = Modifier
                .offset(y = WordmarkLockOffset + WordmarkRise * (1f - wordmark))
                .scale(exitScale)
                .alpha(wordmark * exitAlpha),
        )
    }
}

/** [from]..[to] dilimindeki 0..1 ilerleme; disinda kirpilir. */
private fun phase(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Toplam sure - kullanicinin istedigi 2-3 saniye araliginin ortasi. */
private const val TotalMillis = 2_400

private val MarkSize = 72.dp

// Dikey kilidin olculeri (KefeLogoVertical ile ayni oranlar). Kilit yuksekligi
// 72 + 18 + 43,2 = 133,2dp; merkezi 66,6dp. Isaretin merkezi 36dp'de, yani
// kilit merkezinden 30,6dp YUKARIDA; kelime isaretininki 45dp asagida.
private const val WordmarkHeightRatio = 0.60f
private val MarkLockOffset = (-30.6).dp
private val WordmarkLockOffset = 45.dp

/** Ad belirirken bu kadar yukari suzulur. */
private val WordmarkRise = 10.dp

/**
 * Compose isaretinin BASLANGIC olcegi.
 *
 * Sistem cizimi kucultuldukten sonra ekranda ~87dp geliyor, buradaki kilit ise
 * 72dp kutuda ~50dp'lik bir zincir ciziyor: 87/50 ~ 1,75. Bu carpanla baslayip
 * 1'e inmek, iki pencere arasindaki boy farkini gorunur bir sicrama olmaktan
 * cikarip tek bir surekli harekete cevirir.
 */
private const val HandoffScale = 1.75f

/**
 * Hareketin baslama ani. Sistem penceresi 260ms soluyor (MainActivity); 2400ms'lik
 * eksende bu ~0,11 eder. Bu ana kadar isaret KIMILDAMAZ ki solan katmanla altta
 * duran ayni yerde ayni boyda dursun.
 */
private const val HandoffStart = 0.12f
private const val HandoffEnd = 0.44f

/** Zincirin durgun degeri etrafindaki salinim. */
private const val SwingAmplitude = 0.16f
private const val SwingDecay = 4.2f
private const val SwingTurns = 3f
private const val SwingEnd = 0.70f

private const val WordmarkStart = 0.28f
private const val WordmarkEnd = 0.56f

private const val ExitStart = 0.86f
private const val ExitGrowth = 0.05f

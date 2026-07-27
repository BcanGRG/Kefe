package com.kefe.app.ui.brand

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cosh
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh

/**
 * Zincir egrisi - marka isaretinin saf geometrisi. Composable degil, cizim yok.
 *
 * Kefeyi degil AGIRLIGI ciziyoruz: iki ucundan asili tek hat, yuk altinda ortasindan
 * sarkar. Sarkma derinligi = hedef ilerlemesi.
 *
 * Bu bir daire yayi DEGILDIR. Zincir egrisinde egrilik dipte toplanir (1/a), daire
 * yayinda her noktada aynidir (1/R). Daire "gulumseme", zincir "gerilim" okunur;
 * asagidaki cosh/sinh matematigi tam da bu farki korumak icindir.
 *
 * Tum olculer kutu GENISLIGINE orandir, piksel degil. Kutu kare kabul edilir:
 * kare olmayan bir alanda kisa kenar birim alinir ve isaret ortalanir.
 */
enum class MarkDetail {
    /** 32dp ustu: tavan basligi + askilar + modulasyonlu hat; yuk dipte kalinlasma. */
    Full,

    /** 32dp ve alti: tavan basligi kalkar, yuk som daireye gecer, hat inceltilir. */
    Medium,

    /** 24dp ve alti: modulasyon 1:1,4, askilar %75 kalin, yuk dairesi buyur. */
    Compact,

    /** 16dp ve alti: hat okunmuyor - isaret kullanilmaz, yalniz yuk dairesi kalir. */
    Dot,
}

/** Logo, acilis ve statik marka halinde kullanilan sarkma. */
const val KefeMarkStaticProgress: Float = 0.6f

/**
 * Olcuye gore sadelestirme esigi. Buyuk isaret kucultulmez; her esikte AYRI varyant
 * cizilir - 24dp'de kalinlastirilmis hat, 16dp'de yalniz daire.
 */
fun markDetailFor(size: Dp): MarkDetail = when {
    size > 32.dp -> MarkDetail.Full
    size > 24.dp -> MarkDetail.Medium
    size > 16.dp -> MarkDetail.Compact
    else -> MarkDetail.Dot
}

/**
 * Isaretin tamami tek Path olarak: askilar + hat + yuk. Tek renkte cizilecek yerler
 * (uygulama ikonu, maske, klip) icin. Askilarin ayri opaklikta cizilmesi gerektiginde
 * parca fonksiyonlari kullanilir.
 */
fun chainCurvePath(size: Size, progress: Float, detail: MarkDetail): Path {
    val sag = sagFor(progress)
    return Path().apply {
        addPath(chainAnchorPath(size, detail))
        addPath(chainLinePath(size, sag, detail))
        addPath(chainLoadPath(size, sag, detail))
    }
}

/** Asilma noktalari: iki dikey aski + (yalniz Full'de) tavan basliklari. */
internal fun chainAnchorPath(size: Size, detail: MarkDetail): Path {
    val path = Path()
    if (detail == MarkDetail.Dot) return path

    val box = MarkBox(size)
    val hangerWidth = if (detail == MarkDetail.Compact) HANGER_WIDTH_THICK else HANGER_WIDTH
    val half = hangerWidth / 2f

    for (cx in listOf(ANCHOR_LEFT, ANCHOR_RIGHT)) {
        path.addRect(
            Rect(
                left = box.toX(cx - half),
                top = box.toY(HANGER_TOP),
                right = box.toX(cx + half),
                bottom = box.toY(Y_ANCHOR),
            )
        )
        // Tavan basligi isareti swoosh olmaktan kurtaran detay: hat bir yere BAGLI.
        if (detail == MarkDetail.Full) {
            path.addRoundRect(
                RoundRect(
                    rect = Rect(
                        left = box.toX(cx - CAP_WIDTH / 2f),
                        top = box.toY(CAP_TOP),
                        right = box.toX(cx + CAP_WIDTH / 2f),
                        bottom = box.toY(CAP_TOP + CAP_HEIGHT),
                    ),
                    cornerRadius = CornerRadius(box.toLength(CAP_RADIUS)),
                )
            )
        }
    }
    return path
}

/**
 * Modulasyonlu hat: uctan dibe kalinlasan zincir egrisi. Kapali bir dilimdir -
 * ust kenar ileri, alt kenar geri taranir.
 */
internal fun chainLinePath(size: Size, sag: Float, detail: MarkDetail): Path {
    val path = Path()
    if (detail == MarkDetail.Dot) return path

    val box = MarkBox(size)
    val tEnd = when (detail) {
        MarkDetail.Compact -> T_END_COMPACT
        else -> T_END
    }
    val tMid = when (detail) {
        MarkDetail.Full -> T_MID
        MarkDetail.Medium -> T_MID_LOADED
        else -> T_MID_COMPACT
    }

    val h = SPAN / 2f
    val d = sag.coerceAtLeast(SAG_FLOOR)
    val a = solveCatenaryA(h, d)

    // Ust kenar (soldan saga)
    for (i in 0..SAMPLES) {
        val u = -h + 2f * h * i / SAMPLES
        val p = edge(box, u, h, d, a, tEnd, tMid, upper = true)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    // Alt kenar (sagdan sola)
    for (i in SAMPLES downTo 0) {
        val u = -h + 2f * h * i / SAMPLES
        val p = edge(box, u, h, d, a, tEnd, tMid, upper = false)
        path.lineTo(p.x, p.y)
    }
    path.close()
    return path
}

/**
 * Yuk. Full'de yuk hattin kendisindedir (dipte kalinlasma) - bu yuzden bos doner.
 * Kucuk olculerde som daireye gecer; Dot'ta geriye yalniz bu daire kalir.
 */
internal fun chainLoadPath(size: Size, sag: Float, detail: MarkDetail): Path {
    val path = Path()
    val box = MarkBox(size)
    when (detail) {
        MarkDetail.Full -> return path
        MarkDetail.Medium -> path.addOval(
            Rect(
                center = Offset(box.toX(CENTER_X), box.toY(Y_ANCHOR + sag.coerceAtLeast(SAG_FLOOR))),
                radius = box.toLength(LOAD_RADIUS),
            )
        )
        MarkDetail.Compact -> path.addOval(
            Rect(
                center = Offset(box.toX(CENTER_X), box.toY(Y_ANCHOR + sag.coerceAtLeast(SAG_FLOOR))),
                radius = box.toLength(LOAD_RADIUS_COMPACT),
            )
        )
        // Tek basina kalan yuk kutunun ortasina oturur: bu olcude sarkma okunmadigi
        // icin merkezden kacik bir daire hata gibi gorunur.
        MarkDetail.Dot -> path.addOval(
            Rect(
                center = Offset(box.toX(CENTER_X), box.toY(CENTER_Y)),
                radius = box.toLength(LOAD_RADIUS_ALONE),
            )
        )
    }
    return path
}

/**
 * Sarkma derinligi. Dogrusal degil: ilk katkilar hatti belirgin ceker, sona dogru
 * hareket yavaslar. %0'da bile hat duz degildir - %5 sarkma ile gergin durur.
 */
internal fun sagFor(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return SAG_MIN + (SAG_MAX - SAG_MIN) * (1f - (1f - p).pow(SAG_EXPONENT))
}

/**
 * `sag = a * (cosh(halfSpan / a) - 1)` denklemini a icin cozer. Kapali formulu yok;
 * ikiye bolmeyle 40 adimda bulunur (hata < %0,01). Sarkma a'ya gore azalan oldugu icin
 * hesaplanan sarkma hedeften buyukse alt sinir yukselir.
 */
internal fun solveCatenaryA(halfSpan: Float, sag: Float): Float {
    if (halfSpan <= 0f || sag <= 0f) return A_MAX
    var lo = A_MIN
    var hi = A_MAX
    repeat(SOLVE_STEPS) {
        val mid = (lo + hi) / 2f
        if (mid * (cosh(halfSpan / mid) - 1f) > sag) lo = mid else hi = mid
    }
    return (lo + hi) / 2f
}

/**
 * u noktasindaki kenar. Hat kalinligi egriye DIK acilir (normal = (sinh, 1)/cosh);
 * dikey ofset kullanilsaydi uclarda hat incelmis gorunurdu.
 */
private fun edge(
    box: MarkBox,
    u: Float,
    halfSpan: Float,
    sag: Float,
    a: Float,
    tEnd: Float,
    tMid: Float,
    upper: Boolean,
): Offset {
    val ch = cosh(u / a)
    val sh = sinh(u / a)
    val ratio = u / halfSpan
    val halfWidth = (tEnd + (tMid - tEnd) * (1f - ratio * ratio)) / 2f
    val sign = if (upper) -1f else 1f
    val x = (CENTER_X + u) + sign * halfWidth * (sh / ch)
    val y = (Y_ANCHOR + sag - a * (ch - 1f)) + sign * halfWidth * (1f / ch)
    return Offset(box.toX(x), box.toY(y))
}

/** Oran -> piksel donusumu. Kutu kare kabul edilir, kisa kenar birimdir. */
private class MarkBox(size: Size) {
    private val unit: Float = min(size.width, size.height)
    private val originX: Float = (size.width - unit) / 2f
    private val originY: Float = (size.height - unit) / 2f

    fun toX(ratio: Float): Float = originX + ratio * unit
    fun toY(ratio: Float): Float = originY + ratio * unit
    fun toLength(ratio: Float): Float = ratio * unit
}

// --- Parametrik tanim: tumu kutu genisligine oran ---

private const val SPAN = 0.80f
private const val Y_ANCHOR = 0.42f
private const val CENTER_X = 0.5f
private const val CENTER_Y = 0.5f
private const val ANCHOR_LEFT = 0.10f
private const val ANCHOR_RIGHT = 0.90f

private const val SAG_MIN = 0.05f
private const val SAG_MAX = 0.40f
private const val SAG_EXPONENT = 1.6f

/** Sifir sarkmada egri tekillesir; jestin alt salinimi icin taban deger. */
internal const val SAG_FLOOR = 0.004f

private const val T_END = 0.026f
private const val T_MID = 0.066f

/** Som daire yuku tasiyinca hat inceltilir, aksi halde iki yuk ust uste biner. */
private const val T_MID_LOADED = 0.046f

private const val T_END_COMPACT = 0.054f
private const val T_MID_COMPACT = 0.076f

private const val HANGER_WIDTH = 0.016f
private const val HANGER_WIDTH_THICK = 0.028f
private const val HANGER_TOP = 0.17f
private const val CAP_WIDTH = 0.12f
private const val CAP_HEIGHT = 0.024f
private const val CAP_TOP = 0.154f
private const val CAP_RADIUS = 0.012f

private const val LOAD_RADIUS = 0.062f
private const val LOAD_RADIUS_COMPACT = 0.09f
private const val LOAD_RADIUS_ALONE = 0.34f

/** 64 ornek nokta yeter; 128'de fark gorulmez. */
private const val SAMPLES = 64
private const val SOLVE_STEPS = 40
private const val A_MIN = 0.0005f
private const val A_MAX = 200f

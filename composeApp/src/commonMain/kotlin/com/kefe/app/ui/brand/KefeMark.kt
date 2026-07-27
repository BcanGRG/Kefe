package com.kefe.app.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kefe.app.ui.theme.KefeTheme

/**
 * Marka isareti: iki ucundan asili tek hat, yuk altinda sarkar.
 * Sarkma derinligi = [progress] (hedef ilerlemesi).
 *
 * [detail] verilmezse cizim aninda olculen kutu boyutundan secilir; boylece ayni
 * cagri hem 16dp hem 96dp'de dogru varyanti cizer. Sadelestirme kucultme degildir:
 * her esikte AYRI bir cizim uretilir (bkz. markDetailFor).
 */
@Composable
fun KefeMark(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = KefeTheme.colors.accent,
    detail: MarkDetail? = null,
) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier) {
        if (size.minDimension <= 0f) return@Canvas
        // DrawScope zaten Density; esik dp cinsinden tanimli oldugu icin geri cevriliyor.
        val resolved = detail ?: markDetailFor(size.minDimension.toDp())
        drawPath(path = chainCurvePath(size, p, resolved), color = color)
    }
}

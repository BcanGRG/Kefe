package com.kefe.app.ui.screens.goals

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.unitPerTl
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.tabular

/**
 * Hedefin rakamlarini HEDEFIN BIRIMINDE yazar; TL yaninda kucuk bir not kalir.
 *
 * Kullanici €1.800 biriktiriyor ve "ayda kac euro" diye bakiyor: kura bagli
 * hedefte ana rakam €, TL kosede kucuk (kullanici karari). TL hedefte her sey
 * TL ve not yok - ayni rakam iki kez yazilmaz.
 *
 * Hesaplar TL'de kalir (ilerleme, projeksiyon, gereken aylik); cevrim yalniz
 * gosterimde ve hedefin KENDI kuruyla yapilir ([unitPerTl]), boylece ayni
 * ekrandaki euro rakamlari birbiriyle toplanir.
 */
class GoalMoney(val unit: GoalUnit, private val unitPerTl: Double?) {
    /** Kura bagli hedef: ana rakam birimde. */
    val inUnit: Boolean get() = unitPerTl != null

    /** TL tutarin birimdeki karsiligi; TL hedefte null. */
    fun units(tl: Double): Double? = unitPerTl?.let { tl * it }

    /** Ana rakam: "€300" ya da TL hedefte "₺16.559,13". */
    fun main(tl: Double): String = units(tl)?.let(::unitText) ?: Money.tlExact(tl)

    /** Isaretli ana rakam: "+€100" / "−€20" ya da TL hedefte "+₺5.524". */
    fun signed(tl: Double): String = units(tl)?.let(::signedUnits) ?: Money.tlSigned(tl)

    /** Birimdeki isaretli miktar: "+€100" / "−€20". */
    fun signedUnits(units: Double): String = (if (units > 0.0) "+" else "") + unitText(units)

    /** Yanindaki kucuk TL: "₺16.559,13"; TL hedefte null. */
    fun note(tl: Double): String? = if (inUnit) Money.tlExact(tl) else null

    /** Birim miktari: "€1.800", "12,5 gr", "$500". */
    fun unitText(units: Double): String = when (unit) {
        GoalUnit.Eur -> Money.foreign(units, "EUR", decimals = Money.decimals(units, max = 2))
        GoalUnit.Usd -> Money.foreign(units, "USD", decimals = Money.decimals(units, max = 2))
        GoalUnit.GoldGram -> Money.quantity(units, "gr", Money.decimals(units, max = 2))
        GoalUnit.Try -> Money.tlExact(units)
    }
}

/** Bu hedefin gosterimi - butun ekranlar ayni kurla ayni birimi yazsin. */
val Goal.money: GoalMoney get() = GoalMoney(unit, unitPerTl)

/** "₺0 / ₺99.354,78 · güncel kurla" - kura bagli hedefin kucuk TL satiri; TL hedefte null. */
internal fun Goal.tlNote(wealthTl: Double): String? =
    if (money.inUnit) "${Money.tlExact(wealthTl)} / ${Money.tlExact(amount)} · güncel kurla" else null

/**
 * "[prefix]€300  ₺16.559,13": ana rakam hedefin biriminde, TL yaninda kucuk ve
 * soluk. TL hedefte not yoktur, yalniz ana rakam yazilir.
 */
@Composable
internal fun AmountWithNote(
    main: String,
    note: String?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    maxLines: Int = Int.MAX_VALUE,
) {
    val c = KefeTheme.colors
    Text(
        text = buildAnnotatedString {
            append(prefix)
            append(main)
            append(suffix)
            if (note != null) {
                withStyle(SpanStyle(color = c.onSurfaceMuted, fontSize = KefeTheme.type.micro.fontSize, fontWeight = FontWeight.Normal)) {
                    append("  $note")
                }
            }
        },
        style = style.tabular(),
        color = color,
        maxLines = maxLines,
        modifier = modifier,
    )
}

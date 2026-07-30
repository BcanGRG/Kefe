package com.kefe.app.ui.format

import kotlin.math.abs
import kotlin.math.round

/**
 * tr-TR sayi bicimlendirme. Ortak kodda java.text yok, elle uygulanmistir.
 *
 * Handoff kurallari:
 * - Binlik ayraci NOKTA, ondalik VIRGUL: `3.180.400` / `%14,9`
 * - `₺` onde; hero ve buyuk tutarlarda BOSLUKLU (`₺ 3.180.400`),
 *   satir ici kompakt tutarlarda bosluksuz (`₺12.400`)
 * - Ana toplamlarda kurus GOSTERILMEZ
 * - Eksi isareti U+2212 (`−`), ASCII tire degil
 * - Iki ayri yuzde bicimi:
 *     ORAN   -> isaretsiz, `%` ONDE   : `%41`, `%58,1`   (pay, ilerleme)
 *     DEGISIM-> isaretli,  `%` SONDA  : `+0,29%`, `−2,40%`
 * - Para eksenleri kisaltilir: `4,2M`, `850B`
 */
object Money {

    const val MINUS = '−'
    const val LIRA = "₺"

    /** Bakiye gizliyken kullanilan maske. Basamak sayisi buyukluge gore degisir. */
    fun masked(digits: Int = 6, spaced: Boolean = true): String =
        LIRA + (if (spaced) " " else "") + "•".repeat(digits)

    /** `3.180.400` - simgesiz gruplanmis sayi. */
    fun number(value: Double, decimals: Int = 0): String = format(value, decimals, forceSign = false)

    /**
     * `₺3.180.400` / `₺ 3.180.400`
     *
     * Sub-lira tutari tam-TL'ye yuvarlamak yaniltir (₺0,76 -> ₺1, hatta ₺0);
     * caller acikca ondalik istememisse (decimals=0) kucuk bir tutarda kurus
     * gosterilir. Buyuk sayilar (>=1 TL) etkilenmez - kahraman rakamlar tam kalir.
     * Boylece bir fon payi (< ₺1) her ekranda gercek degeriyle gorunur.
     */
    fun tl(value: Double, spaced: Boolean = false, decimals: Int = 0): String {
        val effectiveDecimals =
            if (decimals == 0 && value != 0.0 && abs(value) < 1.0) 2 else decimals
        val body = format(abs(value), effectiveDecimals, forceSign = false)
        val sign = if (value < 0) MINUS.toString() else ""
        return sign + LIRA + (if (spaced) " " else "") + body
    }

    /** `+₺12.400` / `−₺8.240` - isaret her zaman yazilir (renk tek sinyal olamaz). */
    fun tlSigned(value: Double, spaced: Boolean = false, decimals: Int = 0): String {
        val sign = when {
            value > 0 -> "+"
            value < 0 -> MINUS.toString()
            else -> ""
        }
        return sign + LIRA + (if (spaced) " " else "") + format(abs(value), decimals, forceSign = false)
    }

    /** Pay/ilerleme orani: `%41`, `%58,1`. Girdi yuzde cinsinden (41.0, 58.1). */
    fun ratio(percent: Double, decimals: Int = 0): String =
        "%" + format(abs(percent), decimals, forceSign = false)

    /** 0..1 araligindaki kesirden oran: 0.41 -> `%41` */
    fun ratioOf(fraction: Double, decimals: Int = 0): String = ratio(fraction * 100.0, decimals)

    /** Degisim yuzdesi: `+0,29%`, `−2,40%`, `0,00%`. */
    fun delta(percent: Double, decimals: Int = 2): String {
        val sign = when {
            percent > 0 -> "+"
            percent < 0 -> MINUS.toString()
            else -> ""
        }
        return sign + format(abs(percent), decimals, forceSign = false) + "%"
    }

    /**
     * Eksen kisaltmasi: `4,2M` (milyon), `850B` (bin). Simge cagiran tarafta.
     *
     * [thousandDecimals] bin araligindaki ondalik: eksende 0 yeterli, dar bir
     * yere sigdirilan GERCEK bir tutarda 1 gerekir - 19.587 aksi halde "20B"
     * olur ve yuzde yarim kayar.
     */
    fun compact(value: Double, thousandDecimals: Int = 0): String {
        val a = abs(value)
        val sign = if (value < 0) MINUS.toString() else ""
        return sign + when {
            a >= 1_000_000 -> format(a / 1_000_000.0, 1, false) + "M"
            // Tam bin ise ondalik yazilmaz: "100,0B" degil "100B".
            a >= 1_000 -> {
                val thousands = a / 1_000.0
                val decimals = if (thousands % 1.0 == 0.0) 0 else thousandDecimals
                format(thousands, decimals, false) + "B"
            }

            else -> format(a, 0, false)
        }
    }

    /** Miktar + birim: `62,4 gr`, `8 adet`, `12.400 pay`. */
    fun quantity(value: Double, unit: String, decimals: Int = 0): String =
        format(value, decimals, forceSign = false) + " " + unit

    // --- ic ---

    private fun format(value: Double, decimals: Int, forceSign: Boolean): String {
        val negative = value < 0
        val a = abs(value)

        val factor = pow10(decimals)
        val scaled = round(a * factor).toLong()
        val intPart = scaled / factor.toLong()
        val fracPart = scaled % factor.toLong()

        val sb = StringBuilder()
        if (negative) sb.append(MINUS) else if (forceSign) sb.append('+')
        sb.append(group(intPart.toString()))
        if (decimals > 0) {
            sb.append(',')
            sb.append(fracPart.toString().padStart(decimals, '0'))
        }
        return sb.toString()
    }

    private fun pow10(n: Int): Double {
        var r = 1.0
        repeat(n) { r *= 10.0 }
        return r
    }

    /** Sagdan uce nokta yerlestirir: `3180400` -> `3.180.400` */
    private fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        val firstGroup = digits.length % 3
        var i = 0
        if (firstGroup > 0) {
            sb.append(digits, 0, firstGroup)
            i = firstGroup
        }
        while (i < digits.length) {
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }
}

/**
 * Sayiyi bir tutar ALANINA yazilacak HAM metne cevirir: binlik ayrac YOK (onu
 * cizimde [com.kefe.app.ui.components.ThousandsSeparatorTransformation] ekler),
 * ondalik virgullu. Ham deger sayesinde String tabanli alanda imlec yerinde
 * durur ve ortadaki rakam duzenlenebilir. Tam sayida ondalik gosterilmez.
 */
fun rawAmount(value: Double): String = when {
    value <= 0.0 -> ""
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString().replace('.', ',')
}

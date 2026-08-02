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

    /**
     * YABANCI para birimindeki fiyat: "$308,91", "€157,68", "£33,84".
     *
     * Yalniz borsa kotasyonlarinin yaninda kullanilir - hesap TL ile yapilir
     * (bkz. [Price.nativePrice]). Sayi tr-TR bicimlenir: kullanici Turkce
     * okuyor, "308.91" degil "308,91" bekliyor.
     *
     * Bilmedigimiz bir kod gelirse simge yerine KODUN KENDISI yazilir
     * ("CHF 42,10"); uydurma bir simge basmaktansa duz kod dogrudur.
     *
     * `GBp` (peni) BURAYA GELMEZ: cevrim katmani onu sterline dondurur, cunku
     * "£3.383,50" yazmak fiyati yuz kat buyuk gostermek olurdu.
     */
    fun foreign(value: Double, currencyCode: String, decimals: Int = 2): String {
        val body = format(abs(value), decimals, forceSign = false)
        val sign = if (value < 0) MINUS.toString() else ""
        return when (currencyCode) {
            "USD" -> "$sign$$body"
            "EUR" -> "$sign€$body"
            "GBP" -> "$sign£$body"
            else -> "$sign$currencyCode $body"
        }
    }

    /**
     * Tutarin GERCEKTEN tasidigi kurusu yazar - en cok iki hane.
     *
     * `Kuruslari goster` ayarindan BAGIMSIZDIR (bkz. [moneyTl]). Ayar ana
     * toplamlar icin konmustu; varlik detayindaki "Güncel değer" ve varlik
     * listesindeki kar/zarar ise ayarin kapali olmasi yuzunden kirpiliyordu:
     * kurusuna kadar hesap yapan biri icin ₺147.581,36 "₺147.581" gorunuyordu.
     *
     * Hane sayisi degerden turer ama IKI KADEMELIDIR: tam sayi tutar
     * "₺40.359,00" olmaz (gurultu), kusuratli tutar da "₺670.503,6" olmaz.
     * Parada ya kurus vardir ya yoktur; tek hane para yazimi degildir ve
     * ust uste dizilen bir sutunda rakamlar egri gorunur.
     */
    fun tlExact(value: Double, spaced: Boolean = false): String =
        tl(value, spaced = spaced, decimals = centDecimals(value))

    /** [tlExact]'in isaretli kardesi - kar/zarar satirlari. */
    fun tlSignedExact(value: Double, spaced: Boolean = false): String =
        tlSigned(value, spaced = spaced, decimals = centDecimals(value))

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

    /**
     * Degerin GERCEKTEN tasidigi ondalik hane sayisi - en az [min], en cok [max].
     *
     * NEDEN: hane sayisi sabit yazildiginda ya bilgi kayboluyor ya gurultu
     * ekleniyordu. Fon payi TEFAS'ta alti haneye kadar iner (₺108,394521) ama
     * ekran iki haneye, varlik detayinda da 100 TL ustunde SIFIR haneye
     * yuvarliyordu: kullanicinin kurusuna kadar girdigi fiyat "₺108" olarak
     * gorunuyordu. Bunun tersi de dogru: 10.063 TL'lik ceyregi alti haneyle
     * yazmak okumayi zorlastirir.
     *
     * Bu yuzden hane sayisi DEGERDEN turer, sinir varlik sinifindan gelir.
     */
    fun decimals(value: Double, max: Int, min: Int = 0): Int {
        if (!value.isFinite()) return min
        val a = abs(value)
        // Kayan nokta artigi: 108,39 diskte 108.38999999999999 olabiliyor.
        // Bagil tolerans, buyuk sayilarda da dogru calisir.
        val tolerance = Epsilon * maxOf(1.0, a)
        for (d in min..max) {
            val factor = pow10(d)
            if (abs(round(a * factor) / factor - a) <= tolerance) return d
        }
        return max
    }

    // --- ic ---

    /** Bagil tolerans: cift duyarlikli sayida ~15 anlamli hane var. */
    private const val Epsilon = 1e-9

    /** TL bir tutarda kurustan oteye hane yoktur. */
    private const val MaxCents = 2

    /** Ya sifir ya iki hane - arada bir sey yok (bkz. [tlExact]). */
    private fun centDecimals(value: Double): Int =
        if (decimals(value, max = MaxCents) == 0) 0 else MaxCents

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

package com.kefe.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Tutar alanlarinda binlik ayraci GORUNUMDE ekler; altyapidaki deger AYRAÇSIZ
 * kalir ("3000000", "2,5"). Boylece imlec ham metnin uzerinde gezer ve ortadaki
 * bir rakami duzenlemek calisir.
 *
 * NEDEN: once ayraci [GoalsViewModel] metnin KENDISINE yaziyordu ("3.000.000").
 * String tabanli `BasicTextField`, her tusta metni yeniden bicimlenince imleci
 * sona atiyor - kullanici ortadaki rakami degistiremiyordu. Ayrac artik yalniz
 * cizimde; deger degismedigi icin imlec yerinde kalir.
 *
 * Ondalik virgulden SONRASI gruplanmaz. Esleme yuruyerek kurulur (formul degil):
 * ayrac konumlari boyle her uzunlukta dogru cikar.
 */
class ThousandsSeparatorTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val commaIndex = raw.indexOf(',')
        val intPart = if (commaIndex < 0) raw else raw.substring(0, commaIndex)
        val tail = if (commaIndex < 0) "" else raw.substring(commaIndex) // virgul + ondalik

        val len = intPart.length
        val display = StringBuilder()
        // originalToTransformed[i] = i. ham ofsetinin cizimdeki karsiligi (tam kisim).
        val o2t = IntArray(len + 1)
        var pos = 0
        for (i in 0 until len) {
            o2t[i] = pos
            // Sagdan uclu gruplar: ilk rakam degilse ve kalan basamak sayisi 3'un
            // kati ise araya ayrac girer.
            if (i > 0 && (len - i) % 3 == 0) {
                display.append(SEPARATOR)
                pos++
            }
            display.append(intPart[i])
            pos++
        }
        o2t[len] = pos
        val intDisplayLen = pos
        display.append(tail)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= len -> o2t[offset]
                // Virgul ve sonrasi bire bir: tam kismin cizim uzunlugu + kaydirma.
                else -> intDisplayLen + (offset - len)
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset >= intDisplayLen -> len + (offset - intDisplayLen)
                else -> {
                    // o2t artan; offset'i gecmeyen en buyuk ham indeksi bul.
                    var i = 0
                    while (i < len && o2t[i + 1] <= offset) i++
                    i
                }
            }
        }
        return TransformedText(AnnotatedString(display.toString()), mapping)
    }

    private companion object {
        const val SEPARATOR = '.'
    }
}

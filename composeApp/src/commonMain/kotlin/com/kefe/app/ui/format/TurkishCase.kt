package com.kefe.app.ui.format

/**
 * Turkce buyuk/kucuk harf donusumu.
 *
 * Kotlin ortak kodunda `String.uppercase()` locale-bagimsizdir: "i" -> "I".
 * Turkcede dogrusu "i" -> "İ" (noktali). Handoff'ta uppercase uygulanan her
 * yerde (micro etiketler, rozetler) bu donusum kullanilmali - aksi halde
 * "İŞLEM" yerine "ISLEM", "GİRİŞ" yerine "GIRIS" cikar.
 */
object TurkishCase {

    /** Turkce buyuk harf. `i -> İ`, `ı -> I`; digerleri standart. */
    fun upper(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                'i' -> append('İ') // İ
                'ı' -> append('I') // ı -> I
                else -> append(ch.uppercaseChar())
            }
        }
    }

    /** Turkce kucuk harf. `I -> ı`, `İ -> i`; digerleri standart. */
    fun lower(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                'I' -> append('ı') // ı
                'İ' -> append('i') // İ -> i
                else -> append(ch.lowercaseChar())
            }
        }
    }
}

/** Micro etiketler ve rozetler icin: `"toplam birikim".trUpper()` -> `TOPLAM BİRİKİM` */
fun String.trUpper(): String = TurkishCase.upper(this)

fun String.trLower(): String = TurkishCase.lower(this)

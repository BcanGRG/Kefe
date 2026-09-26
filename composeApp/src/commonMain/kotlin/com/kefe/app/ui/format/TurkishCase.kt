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

/**
 * Ozel adin tamlayan (ilgi) eki, kesme isaretiyle: "Merve" -> "Merve'nin",
 * "Burak Can" -> "Burak Can'ın", "Ayşe" -> "Ayşe'nin", "Onur" -> "Onur'un".
 *
 * Ek son UNLUYE uyar (buyuk unlu uyumu + duz/yuvarlak): a/ı -> ın, e/i -> in,
 * o/u -> un, ö/ü -> ün. Ad unluyle bitiyorsa araya "n" girer. Unlusu olmayan
 * (kisaltma gibi) bir ad "in" alir - yanlis olabilir ama okunur kalir.
 *
 * NEDEN. Ayarlar'daki onay metinleri esin telefonundan adiyla soz ediyor
 * ("Merve'nin telefonu eşitlenmeye devam eder"); "Merve telefonu" ya da
 * "Merve'in" Turkce degil.
 */
fun String.trGenitive(): String {
    val name = trim()
    if (name.isEmpty()) return name
    val lower = name.trLower()
    val lastVowel = lower.lastOrNull { it in TurkishVowels }
    val suffix = when (lastVowel) {
        'a', 'ı' -> "ın"
        'o', 'u' -> "un"
        'ö', 'ü' -> "ün"
        else -> "in"
    }
    val buffer = if (lower.last() in TurkishVowels) "n" else ""
    return "$name'$buffer$suffix"
}

private const val TurkishVowels = "aıoueiöü"

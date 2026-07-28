package com.kefe.app.domain.model

/**
 * Portfoye girilebilen yabanci para birimleri.
 *
 * Once secim YOKTU: "Döviz" seciminde her sey sessizce USD kaydediliyordu ve
 * euro hic girilemiyordu. Kod bunu biliyordu - islem ekleme ekraninda bir "SINIR"
 * notu duruyordu - ama kullanici acisindan bu, girdigi euroyu dolar sanan bir
 * uygulamaydi.
 *
 * Liste TCMB'den GERCEKTEN cekilenlerle sinirli: ayristirici koda gore
 * calistigi icin yenisini eklemek iki satir, ama cekilmeyen bir para birimini
 * listeye koymak fiyati olmayan bir secim sunmak olurdu.
 */
enum class Currency(val code: String) {
    Usd("USD"),
    Eur("EUR"),
    Gbp("GBP");

    /** Fiyat tablosundaki anahtar: "usd_try". */
    fun priceKey(): String = code.lowercase() + "_try"

    fun label(): String = when (this) {
        Usd -> "Amerikan Doları"
        Eur -> "Euro"
        Gbp -> "İngiliz Sterlini"
    }

    /** Miktar alaninin birimi: "$ 250" degil "250 $". */
    fun symbol(): String = when (this) {
        Usd -> "$"
        Eur -> "€"
        Gbp -> "£"
    }

    companion object {
        /** Fiyat anahtarindan geri cozer; pozisyon kimligi bu anahtari tasir. */
        fun fromPriceKey(key: String): Currency? = entries.firstOrNull { it.priceKey() == key }
    }
}

package com.kefe.app.domain.model

import com.kefe.app.ui.theme.AssetClassColor

enum class AssetClass {
    Gold,
    Silver,
    Fx,
    Fund,
    Cash,
}

/** Varlik sinifinin tema rengi anahtari. Renk secimi tema katmaninda kalir. */
fun AssetClass.color(): AssetClassColor = when (this) {
    AssetClass.Gold -> AssetClassColor.Gold
    AssetClass.Silver -> AssetClassColor.Silver
    AssetClass.Fx -> AssetClassColor.Fx
    AssetClass.Fund -> AssetClassColor.Fund
    AssetClass.Cash -> AssetClassColor.Cash
}

fun AssetClass.label(): String = when (this) {
    AssetClass.Gold -> "Altın"
    AssetClass.Silver -> "Gümüş"
    AssetClass.Fx -> "Döviz"
    AssetClass.Fund -> "Fon"
    AssetClass.Cash -> "Nakit"
}

enum class GoldSubtype {
    Gram,
    Quarter,
    Half,
    Full,
    Ata,
    Jewelry,
    Bullion;

    fun label(): String = when (this) {
        // Ayar artik AYRI seciliyor (bkz. [usesKarat]); etikete gomulu "24 ayar"
        // 22 ayar gram tutan biri icin yanlisti.
        Gram -> "Gram"
        Quarter -> "Çeyrek"
        Half -> "Yarım"
        Full -> "Tam"
        Ata -> "Ata/Cumhuriyet"
        Jewelry -> "Bilezik/Takı"
        Bullion -> "Has/Külçe"
    }

    /**
     * Bu formda ayar SORULUR mu.
     *
     * Kural: gramla satilan formlarda ayar kullanicinin bilgisidir, adetle
     * satilanlarda formun kendisi ayari belirler (ceyrek/yarim/tam/ata hep 22).
     * Has/Kulce zaten saf altindir (995), sorulacak bir sey yok.
     *
     * Once ayar YALNIZ Bilezik/Takı'da soruluyordu: 22 ayar gram altini olan
     * biri onu "Bilezik/Takı" diye kaydetmek zorunda kaliyordu - hesap dogru
     * cikiyor ama ekranda yanlis sey yaziyordu.
     */
    fun usesKarat(): Boolean = this == Gram || this == Jewelry

    /**
     * Ayar secilmeden onceki varsayilan.
     *
     * Gram'da 24: "gram altin" denince akla saf altin gelir ve eski kayitlarda
     * ayar kolonu bostur - varsayilan 24 olunca [Karat.K24] `gold_gram`
     * anahtarina dustugu icin o kayitlarin fiyati ZERRE degismez.
     * Bilezikte 22: Turkiye'de ziynetin yaygin ayari.
     */
    fun defaultKarat(): Karat = if (this == Gram) Karat.K24 else Karat.K22
}

enum class Karat(val milyem: Int) {
    K14(585),
    K18(750),
    K22(916),
    K24(995);

    fun label(): String = when (this) {
        K14 -> "14 ayar"
        K18 -> "18 ayar"
        K22 -> "22 ayar"
        K24 -> "24 ayar"
    }
}

enum class QuantityUnit {
    Piece,
    Gram,
    Share,
    Currency;

    /** Nakit/doviz miktarinda birim yazilmaz - tutar zaten para olarak bicimlenir. */
    fun label(): String = when (this) {
        Piece -> "adet"
        Gram -> "gr"
        Share -> "pay"
        Currency -> ""
    }
}

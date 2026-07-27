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
        Gram -> "Gram (24 ayar)"
        Quarter -> "Çeyrek"
        Half -> "Yarım"
        Full -> "Tam"
        Ata -> "Ata/Cumhuriyet"
        Jewelry -> "Bilezik/Takı"
        Bullion -> "Has/Külçe"
    }
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

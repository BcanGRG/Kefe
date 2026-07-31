package com.kefe.app.ui.format

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.label

/**
 * Bir varlik sinifinin FIYATTA tasiyabilecegi en cok ondalik hane.
 *
 * Ust sinirdir, sabit hane sayisi degil: gercek hane sayisi degerden turer
 * (bkz. [Money.decimals]). Boylece ₺108,394521 tam yazilir, ₺10.063,88 iki
 * haneyle kalir, ₺40.359 ondaliksiz.
 */
fun AssetClass.maxPriceDecimals(): Int = when (this) {
    // TEFAS pay fiyatlarini ALTI haneye kadar veriyor; iki haneye yuvarlamak
    // kullanicinin elle girdigi fiyati bozuyordu.
    AssetClass.Fund -> 6
    // Kur dort haneye kadar anlamli (capraz kurlarda kurusun altina iner).
    AssetClass.Fx -> 4
    // Kiymetli maden kotasyonlari kurus mertebesinde.
    AssetClass.Gold, AssetClass.Silver -> 2
    AssetClass.Cash -> 2
}

/**
 * Bir birimin MIKTARDA tasiyabilecegi en cok ondalik hane.
 *
 * Fon payi kesirli alinir (₺1.000'lik emir 9,213847 pay eder); adet ise
 * bolunmez.
 */
fun QuantityUnit.maxQuantityDecimals(): Int = when (this) {
    QuantityUnit.Share -> 6
    QuantityUnit.Gram -> 3
    QuantityUnit.Currency -> 2
    QuantityUnit.Piece -> 0
}

/** Bu pozisyonun birim fiyatinin gercekte tasidigi hane sayisi. */
fun Position.priceDecimals(): Int =
    Money.decimals(unitPrice, assetClass.maxPriceDecimals())

/** Bu pozisyonun miktarinin gercekte tasidigi hane sayisi. */
fun Position.quantityDecimalsOf(amount: Double = quantity): Int =
    Money.decimals(amount, unit.maxQuantityDecimals())

/**
 * Varligin miktar metni: "8 adet", "62,4 gr", "12.400 pay × ₺24,60".
 *
 * Ayar bilgisi yalniz adda gecmiyorsa eklenir - "22 Ayar Bilezik" satirinda
 * tekrar etmesin, "Anneannemin Bileziği · 22 ayar" satirinda kaybolmasin diye.
 *
 * Varliklar ekraninda ozeldi; hedef detayindaki "Bu hedefi karsilayanlar" listesi
 * de ayni metni istedi - "₺19.587" tek basina kac ceyrek oldugunu soylemiyor.
 */
fun Position.quantityLabel(): String {
    val base = when (unit) {
        QuantityUnit.Piece, QuantityUnit.Gram ->
            Money.quantity(quantity, unit.label(), quantityDecimalsOf())

        QuantityUnit.Share ->
            Money.quantity(quantity, unit.label(), quantityDecimalsOf()) +
                " × " + Money.tl(unitPrice, decimals = priceDecimals())

        QuantityUnit.Currency ->
            if (assetClass == AssetClass.Cash) {
                "TL"
            } else {
                Money.number(quantity, quantityDecimalsOf()) +
                    " × " + Money.tl(unitPrice, decimals = priceDecimals())
            }
    }

    val karatLabel = karat?.label()
    return if (karatLabel != null && !name.contains(karatLabel, ignoreCase = true)) {
        "$base · $karatLabel"
    } else {
        base
    }
}

/**
 * Miktarin YALNIZ kendisi - fiyatsiz, kisa hal: "2 adet", "100 $", "62,4 gr".
 *
 * Hedef listesinde birim fiyat zaten satirin sagindaki tutarda yasiyor; ikinci
 * kez yazmak satiri sisirir.
 */
fun Position.shortQuantityLabel(): String = shortQuantityLabel(quantity)

/**
 * Ayni kisa hal ama BASKA bir miktar icin: "15 adet".
 *
 * Hedef atamasi artik varligin tamamini degil bir KISMINI tutabiliyor; liste
 * "16 adet" yazarken hedefe 15'i sayiyorsa ekran yalan soyluyor demektir.
 */
fun Position.shortQuantityLabel(amount: Double): String = when (unit) {
    QuantityUnit.Piece, QuantityUnit.Gram, QuantityUnit.Share ->
        Money.quantity(amount, unit.label(), quantityDecimalsOf(amount))

    QuantityUnit.Currency ->
        if (assetClass == AssetClass.Cash) "TL" else Money.number(amount, quantityDecimalsOf(amount))
}

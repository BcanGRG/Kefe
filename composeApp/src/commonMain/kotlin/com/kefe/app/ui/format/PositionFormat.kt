package com.kefe.app.ui.format

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.label

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
        QuantityUnit.Piece ->
            Money.quantity(quantity, unit.label())

        QuantityUnit.Gram ->
            Money.quantity(quantity, unit.label(), quantityDecimals())

        QuantityUnit.Share ->
            Money.quantity(quantity, unit.label()) + " × " + Money.tl(unitPrice, decimals = 2)

        QuantityUnit.Currency ->
            if (assetClass == AssetClass.Cash) {
                "TL"
            } else {
                Money.number(quantity) + " × " + Money.tl(unitPrice, decimals = 2)
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
    QuantityUnit.Piece -> Money.quantity(amount, unit.label())
    QuantityUnit.Gram -> Money.quantity(amount, unit.label(), quantityDecimals(amount))
    QuantityUnit.Share -> Money.quantity(amount, unit.label())
    QuantityUnit.Currency ->
        if (assetClass == AssetClass.Cash) "TL" else Money.number(amount)
}

/** Tam sayi miktarlarda ondalik yazilmaz: 15 gr, 62,4 gr. */
private fun Position.quantityDecimals(amount: Double = quantity): Int =
    if (amount == amount.toLong().toDouble()) 0 else 1

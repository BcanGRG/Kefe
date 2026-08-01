package com.kefe.app.domain.model

/**
 * Portfoydeki tek varlik satiri. `value` ve `cost` hazir tutulur; miktar x
 * birim fiyat yuvarlamasi ile guncel deger arasinda kucuk farklar olabilir.
 */
data class Position(
    val id: String,
    val name: String,
    val assetClass: AssetClass,
    val subtype: GoldSubtype? = null,
    val karat: Karat? = null,
    val quantity: Double,
    val unit: QuantityUnit,
    val unitPrice: Double,
    val value: Double,
    val cost: Double,
    val manualPrice: Boolean = false,
    val dailyChangePercent: Double = 0.0,
    /**
     * Haftalik/aylik fiyat degisimi. null = bilmiyoruz (bkz. [PeriodChanges]);
     * gunlukten farkli olarak sifira dusurulmez - "degismedi" ile "veri yok"
     * ayri seylerdir.
     */
    val weekChangePercent: Double? = null,
    val monthChangePercent: Double? = null,
) {
    val profit: Double get() = value - cost

    val profitPercent: Double get() = if (cost == 0.0) 0.0 else profit / cost * 100.0
}

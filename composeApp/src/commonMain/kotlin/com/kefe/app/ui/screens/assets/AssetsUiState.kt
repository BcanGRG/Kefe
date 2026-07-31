package com.kefe.app.ui.screens.assets

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Position

/**
 * Siralama olcutu. Uc secenek de gercekten calisir: siralama grup ICINDEKI
 * satirlara uygulanir, gruplarin kendi sirasi her zaman toplam degere gore
 * buyukten kucugedir (tasarimdaki grup sirasi da budur).
 */
enum class AssetSort(val label: String) {
    Value("Değere göre"),
    Profit("Kâra göre"),
    Alphabetical("A-Z"),
}

/** Varlik sinifina gore tek grup: baslik rakamlari + satirlar. */
data class AssetGroup(
    val assetClass: AssetClass,
    val total: Double,
    /**
     * Bu grubun TL kar/zarari (deger - maliyet).
     *
     * Basligin sag rakami once toplam icindeki PAY idi (%95,4). Iki sorun vardi:
     * pay zaten Ozet'teki "Ne kadari nerede" halkasinda duruyordu, ve yuzdeyle
     * arasi iyi olmayan biri icin "ne kadar kazandik" sorusunun karsiligi
     * ekranin hicbir yerinde yoktu - yalniz varlik detayina girince goruluyordu.
     */
    val profit: Double,
    /** Kar/zararin maliyete orani - yuzde cinsinden (21.6). */
    val profitPercent: Double,
    val positions: List<Position>,
)

data class AssetsUiState(
    val loading: Boolean = true,
    val groups: List<AssetGroup> = emptyList(),
    val totalValue: Double = 0.0,
    val sort: AssetSort = AssetSort.Value,
    // Nakit grubu kapali baslar - tasarimdaki varsayilan hal.
    val collapsed: Set<AssetClass> = setOf(AssetClass.Cash),
)

sealed interface AssetsIntent {
    data class SelectSort(val sort: AssetSort) : AssetsIntent
    data class ToggleGroup(val assetClass: AssetClass) : AssetsIntent
}

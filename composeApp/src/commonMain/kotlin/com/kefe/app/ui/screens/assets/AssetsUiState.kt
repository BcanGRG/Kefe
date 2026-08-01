package com.kefe.app.ui.screens.assets

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.PeriodTotal
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.periodTotalOf
import com.kefe.app.ui.format.ChangePeriod
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.UnknownChangeText
import com.kefe.app.ui.format.changeIn

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

/**
 * Listedeki degisim rakaminin PENCERESI.
 *
 * Once iki ayri sayi ust uste duruyordu: TL kar (hep toplam) ve altinda donem
 * yuzdesi. Ayni satirda iki farkli soruya cevap vermek karisikti - "+₺190.503"
 * bugune kadarki kar, "Gün −0,93%" ise dune gore. Artik TEK rakam cifti var ve
 * cip hangi soruyu sordugunu soyluyor: TL de yuzde de birlikte degisir.
 *
 * [period] null olan tek secenek "Toplam"dir: onun karsiligi kar/zarardir,
 * yuzdesi de MALIYETE oranlanir (getiri tanimi). Digerlerinde payda donem
 * basindaki degerdir.
 */
enum class AssetChange(val label: String, val period: ChangePeriod?) {
    Day("Gün", ChangePeriod.Day),
    Week("Hafta", ChangePeriod.Week),
    Month("Ay", ChangePeriod.Month),
    Total("Toplam", null),
}

/** Pozisyonun secili penceredeki degisimi; bilinmiyorsa null. */
fun Position.changeIn(mode: AssetChange): PeriodTotal? = when (val period = mode.period) {
    null -> PeriodTotal(amount = profit, percent = profitPercent)
    else -> periodTotalOf(value, changeIn(period))
}

/**
 * "+₺190.503,60 · +39,70%" / "—"
 *
 * TOPLAM penceresinde yuzde sifirsa yazilmaz: orada sifir "maliyet yok"
 * demektir (nakit, elle girilmemis) ve oran anlamsizdir.
 *
 * Donem penceresinde ise sifir GERCEK bir cevaptir - "bugun degismedi". O
 * yuzden yazilir; "₺0" tek basina eksik veri gibi okunuyordu.
 */
fun changeLabel(change: PeriodTotal?, mode: AssetChange): String = when {
    change == null -> UnknownChangeText
    mode == AssetChange.Total && change.percent == 0.0 -> Money.tlSignedExact(change.amount)
    else -> Money.tlSignedExact(change.amount) + " · " + Money.delta(change.percent)
}

/** Varlik sinifina gore tek grup: baslik rakamlari + satirlar. */
data class AssetGroup(
    val assetClass: AssetClass,
    val total: Double,
    /**
     * Basligin sag rakami - SECILI pencereye gore (bkz. [AssetChange]).
     *
     * Once toplam icindeki PAY yaziyordu (%95,4). Iki sorun vardi: pay zaten
     * Ozet'teki "Ne kadari nerede" halkasinda duruyordu, ve yuzdeyle arasi iyi
     * olmayan biri icin "ne kadar kazandik" sorusunun karsiligi ekranin
     * hicbir yerinde yoktu - yalniz varlik detayina girince goruluyordu.
     *
     * Donem penceresinde DEGER AGIRLIKLI; null ise o pencere icin grubun
     * hicbir satirinda veri yok.
     */
    val change: PeriodTotal?,
    /** Siralama olcutu HER ZAMAN toplam kar - cip penceresi onu degistirmez. */
    val profit: Double,
    val positions: List<Position>,
)

data class AssetsUiState(
    val loading: Boolean = true,
    val groups: List<AssetGroup> = emptyList(),
    val totalValue: Double = 0.0,
    val sort: AssetSort = AssetSort.Value,
    // Nakit grubu kapali baslar - tasarimdaki varsayilan hal.
    val collapsed: Set<AssetClass> = setOf(AssetClass.Cash),
    /**
     * Degisim penceresi. Varsayilan TOPLAM: listeye bakan once "ne kadar
     * kazandim" diye sorar, "bugun ne oldu" ikinci sorudur.
     */
    val change: AssetChange = AssetChange.Total,
)

sealed interface AssetsIntent {
    data class SelectSort(val sort: AssetSort) : AssetsIntent
    data class ToggleGroup(val assetClass: AssetClass) : AssetsIntent
    data class SelectChange(val change: AssetChange) : AssetsIntent
}

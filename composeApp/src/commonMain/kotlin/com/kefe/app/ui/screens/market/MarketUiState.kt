package com.kefe.app.ui.screens.market

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.ui.format.ChangePeriod
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.maxPriceDecimals

/**
 * Piyasa tablosunun tek satiri. Rakamlar burada bicimlenir: ekran yalniz cizer,
 * bicimleme kurali (kurus var mi, alis kotasyonu var mi) tek yerde kalir.
 */
data class MarketRow(
    val assetKey: String,
    val name: String,
    /** Fonlarda alis kotasyonu yoktur; o zaman tire gosterilir. */
    val bidText: String,
    val askText: String,
    /**
     * SECILI DONEMIN degisimi; null ise o donem icin veri yok ve
     * [changeText] tire yazar.
     *
     * Sutun tek: telefonda gun/hafta/ay ayri sutunlara sigmiyor (urun adina
     * ~16dp kaliyordu). Hangi donemin yazildigini ustteki cipler soyler.
     */
    val changePercent: Double?,
    val changeText: String,
    /** "Serbest piyasa · 14:32" / "TEFAS · 09:00" / "Elle · dün 21:30" */
    val sourceLine: String,
    val isManual: Boolean,
)

/** Varlik sinifi bolumu. Tasarimda her bolum kendi kartina alinir. */
data class MarketSection(
    val assetClass: AssetClass,
    val title: String,
    val rows: List<MarketRow>,
    /** Yalniz fonlarda dolu: gunde bir guncellenme notu. */
    val note: String? = null,
)

/** Elle fiyat girme sayfasinin durumu; null ise sayfa kapali. */
data class MarketPriceEdit(
    val assetKey: String,
    val name: String,
    val input: String = "",
    /** Satirda halihazirda elle girilmis bir fiyat var mi - sifirlama eylemi buna bagli. */
    val isManual: Boolean = false,
    val invalid: Boolean = false,
)

data class MarketUiState(
    val sections: List<MarketSection> = emptyList(),
    val updatedAtLabel: String = "",
    val freshness: PriceFreshness = PriceFreshness.Offline,
    val refreshing: Boolean = false,
    /** Kisitlanan yenileme bilgisi - hata degil, Ozet ile ayni. */
    val notice: String? = null,
    val edit: MarketPriceEdit? = null,
    /** Degisim sutununun penceresi. Gunluk varsayilan: en cok bakilan olcu. */
    val period: ChangePeriod = ChangePeriod.Day,
)

sealed interface MarketIntent {
    data object Refresh : MarketIntent
    data object DismissNotice : MarketIntent
    data class SelectPeriod(val period: ChangePeriod) : MarketIntent
    data class OpenManualPrice(val assetKey: String) : MarketIntent
    data object DismissManualPrice : MarketIntent
    data class ChangeManualPrice(val text: String) : MarketIntent
    data object SetManualPrice : MarketIntent
    data class ClearManualPrice(val assetKey: String) : MarketIntent
}

/**
 * Kurus gosterimi varlik sinifina baglidir: altin/gumus tam TL ile,
 * doviz ve fon iki hane ile yazilir - tasarimdaki tablo boyle.
 */
fun AssetClass.priceDecimals(value: Double): Int = when (this) {
    AssetClass.Cash -> 0
    // KURUS TABANI: kaynak dakikada bir guncelleniyor ve hareket cogu zaman
    // kurus mertebesinde. Tam liraya yuvarlandiginda tablo saatlerce ayni
    // rakami gosteriyor, fiyat donmus saniliyordu - o yuzden en az iki hane.
    //
    // Ust sinir varlik sinifindan gelir ve gercek hane sayisi DEGERDEN turer:
    // fon payi alti haneye kadar iner (₺3,714523), iki haneye sabitlemek
    // kullanicinin gordugu fiyati kirpiyordu.
    else -> Money.decimals(value, maxPriceDecimals(), min = 2)
}

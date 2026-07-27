package com.kefe.app.domain.repository

import com.kefe.app.domain.model.Price
import kotlinx.coroutines.flow.Flow

/**
 * Fiyat tazeligi. Tasarimda bu uc hal Ozet ekranindaki seride birebir karsilik
 * bulur: taze -> yalniz saat satiri, bayat -> sari serit, cevrimdisi -> gri serit.
 * Hicbiri rakamlari gizlemez.
 */
enum class PriceFreshness {
    /** Son guncelleme 2 saatten yeni. */
    Fresh,

    /** 2 saatten eski; rakamlar gosterilmeye devam eder, yenile onerilir. */
    Stale,

    /** Ag yok; son bilinen fiyatlarla hesaplaniyor. */
    Offline,
}

/** Fiyat tablosunun tamami - ekranlar tek nesneden beslenir. */
data class PriceBoard(
    val prices: List<Price>,
    val updatedAtLabel: String,
    val freshness: PriceFreshness,
) {
    fun byKey(assetKey: String): Price? = prices.firstOrNull { it.assetKey == assetKey }
}

/**
 * Fiyat kaynagi.
 *
 * Elle fiyat girme birinci sinif bir ozelliktir, hata yolu degil: kaynak coktugunde
 * uygulamanin ayakta kalmasini bu saglar. Elle girilen deger otomatik yenilemede
 * EZILMEZ; yalniz [clearManualPrice] ile temizlenir.
 */
interface PriceRepository {

    fun observePrices(): Flow<PriceBoard>

    /** Kaynaktan yeniden ceker. Basarisizlikta son bilinen fiyatlar korunur. */
    suspend fun refresh(): Result<Unit>

    suspend fun setManualPrice(assetKey: String, value: Double)

    suspend fun clearManualPrice(assetKey: String)
}

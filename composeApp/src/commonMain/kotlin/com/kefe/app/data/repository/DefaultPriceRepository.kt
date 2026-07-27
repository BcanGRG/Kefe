package com.kefe.app.data.repository

import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fiyat deposu.
 *
 * Elle girilen fiyatlar ayri bir haritada tutulur ve uzak kaynaktan gelen veriye
 * HER ZAMAN ustun gelir. Boylece kaynak geri geldiginde kullanicinin elle girdigi
 * deger sessizce ezilmez - tasarimdaki "sifirla" eylemi bunun tek geri donusudur.
 */
class DefaultPriceRepository(
    private val remote: PriceRemoteDataSource,
) : PriceRepository {

    private val remotePrices = MutableStateFlow(emptyList<Price>())
    private val manualPrices = MutableStateFlow(emptyMap<String, Double>())
    private val board = MutableStateFlow(
        PriceBoard(emptyList(), "—", PriceFreshness.Offline)
    )

    override fun observePrices(): Flow<PriceBoard> = board.asStateFlow()

    override suspend fun refresh(): Result<Unit> = runCatching {
        val fetched = remote.fetchPrices()
        remotePrices.value = fetched
        publish(PriceFreshness.Fresh)
    }.onFailure {
        // Ag yoksa son bilinen fiyatlar ekranda kalir; yalniz tazelik etiketi duser.
        publish(PriceFreshness.Offline)
    }

    override suspend fun setManualPrice(assetKey: String, value: Double) {
        manualPrices.value = manualPrices.value + (assetKey to value)
        publish(board.value.freshness)
    }

    override suspend fun clearManualPrice(assetKey: String) {
        manualPrices.value = manualPrices.value - assetKey
        publish(board.value.freshness)
    }

    private fun publish(freshness: PriceFreshness) {
        val manual = manualPrices.value
        val merged = remotePrices.value.map { price ->
            val override = manual[price.assetKey]
            if (override == null) {
                price
            } else {
                price.copy(ask = override, source = PriceSource.Manual, isManual = true)
            }
        }
        board.value = PriceBoard(
            prices = merged,
            updatedAtLabel = merged.firstOrNull { !it.isManual }?.timestamp ?: "—",
            freshness = freshness,
        )
    }
}

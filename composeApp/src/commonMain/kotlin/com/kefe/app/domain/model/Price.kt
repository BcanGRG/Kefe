package com.kefe.app.domain.model

/** Piyasa fiyati. Fonlarda alis kotasyonu olmadigi icin `bid` null olabilir. */
data class Price(
    val assetKey: String,
    val label: String,
    val bid: Double?,
    val ask: Double,
    val changePercent: Double,
    val timestamp: String,
    val source: PriceSource,
    val isManual: Boolean = false,
    val assetClass: AssetClass,
)

enum class PriceSource {
    FreeMarket,
    Tefas,
    Manual;

    fun label(): String = when (this) {
        FreeMarket -> "Serbest piyasa"
        Tefas -> "TEFAS"
        Manual -> "Elle"
    }
}

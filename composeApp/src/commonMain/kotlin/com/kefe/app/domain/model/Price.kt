package com.kefe.app.domain.model

/** Piyasa fiyati. Fonlarda alis kotasyonu olmadigi icin `bid` null olabilir. */
data class Price(
    val assetKey: String,
    val label: String,
    val bid: Double?,
    val ask: Double,
    /** GUNLUK degisim - kaynagin kendi verdigi rakam. */
    val changePercent: Double,
    val timestamp: String,
    val source: PriceSource,
    val isManual: Boolean = false,
    val assetClass: AssetClass,
    /**
     * Haftalik/aylik degisim. null = bilmiyoruz, ekran "—" yazar.
     *
     * Kaynaklar gecmis vermiyor (serbest piyasa yalniz bugunu, TCMB gunluk
     * bulteni); bu yuzden depo bunlari cihazdaki `price_history` tablosundan
     * hesaplar. Fonlar istisna: TEFAS zaten bir aylik seri donuyor, o seri
     * [history] ile tasinip gecmise yazildigi icin fonlar ilk gunden doludur.
     */
    val weekChangePercent: Double? = null,
    val monthChangePercent: Double? = null,
    /**
     * Kaynagin verdigi GUNLUK fiyat serisi - yalniz CEKIM ANINDA doludur.
     *
     * Onbellege (cached_prices) yazilmaz; depo bunu `price_history`'ye dokup
     * birakir. Onbellekten okunan bir [Price] her zaman bos seri tasir.
     */
    val history: List<PricePoint> = emptyList(),
)

enum class PriceSource {
    FreeMarket,
    Tefas,
    /** Borsa kotasyonu (BIST ve ABD borsalari). */
    Exchange,
    Manual;

    fun label(): String = when (this) {
        FreeMarket -> "Serbest piyasa"
        Tefas -> "TEFAS"
        Exchange -> "Borsa"
        Manual -> "Elle"
    }
}

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
    /**
     * Kaynagin KENDI para birimindeki fiyat ve o birimin kodu - YALNIZ GOSTERIM.
     *
     * Hesap her yerde TL ile yapilir: [ask] tek bir sayidir ve portfoy toplami,
     * dagilim, hedef ilerlemesi, net deger grafigi hep onu toplar. Apple'i dolar,
     * altini lira tutup ikisini toplamak anlamsiz bir rakam uretirdi.
     *
     * Ama kullanicinin kafasindaki sayi ₺14.690 degil $308,91. Bu cift, TL
     * degerin yanina o rakami yazabilmek icin var.
     *
     * TAHMIN EDILMEZ, KAYNAKTAN GELIR: sembol ekinden ("`.L` ise sterlin")
     * cikarilabilirdi ama Londra'da dolarla isleyen satirlar da var - yanlis
     * para birimi yazmak hic yazmamaktan kotudur.
     *
     * Altin, doviz, fon ve BIST hissesinde null: onlarda zaten TL disinda bir
     * fiyat yok.
     */
    val nativePrice: Double? = null,
    val nativeCurrency: String? = null,
    /**
     * Bu kotasyonun ait oldugu ISLEM GUNU. null = bilmiyoruz.
     *
     * [timestamp]'ten AYRI: o bir gosterim metni ("10:36", "TCMB"), bu ise
     * hesaba giren bir olgu. [changePercent] "onceki kotasyondan bu yana" ne
     * kadar oynandigini soyler; o degisimin BUGUNE ait olup olmadigi ancak
     * buradan bilinir.
     *
     * Neden gerekti: cumartesi gunu borsa kapaliyken "bugunku getiri" cumanin
     * hareketini yaziyordu. Rakam yanlis degildi, ETIKETI yanlisti - kullanicinin
     * serveti cumartesi degismemisti. Artik gunluk degisim yalnizca kotasyon
     * BUGUNE aitse sayiliyor (bkz. [Position.valuedAt]).
     */
    val quoteDate: KefeDate? = null,
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

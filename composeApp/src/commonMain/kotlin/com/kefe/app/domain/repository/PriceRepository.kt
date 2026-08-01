package com.kefe.app.domain.repository

import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PricePoint
import kotlinx.coroutines.flow.Flow

/**
 * Fiyat tazeligi. Tasarimda bu uc hal Ozet ekranindaki seride birebir karsilik
 * bulur: taze -> yalniz saat satiri, bayat -> sari serit, cevrimdisi -> gri serit.
 * Hicbiri rakamlari gizlemez.
 */
/**
 * Ekrandaki fiyatlarin durumu.
 *
 * [Loading] ile [Offline] AYRI OLMAK ZORUNDA. Once ikisi de "Çevrimdışı" idi:
 * elinde hic fiyat olmayan bir uygulama - ilk acilis, temizlenmis veri - daha
 * ilk istegi yolday iken cevrimdisi oldugunu soyluyordu. Kullanicinin "hep hata
 * veriyor gibi" dedigi sey buydu; oysa istek gidiyor ve basariyla donuyordu.
 *
 * Kural: "Çevrimdışı" ancak GERCEKTEN DENENIP BASARISIZ olunca soylenir.
 */
enum class PriceFreshness {
    /** Ilk fiyatlar henuz alinmadi - hata degil, bilgi yoklugu. */
    Loading,

    /** Son guncelleme 2 saatten yeni. */
    Fresh,

    /** 2 saatten eski; rakamlar gosterilmeye devam eder, yenile onerilir. */
    Stale,

    /** Deneme yapildi ve basarisiz oldu; son bilinen fiyatlarla hesaplaniyor. */
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

    /**
     * Tek varligin gunluk fiyat gecmisi, eskiden yeniye.
     *
     * Guncel fiyat tablosu her yenilemede uzerine yazildigi icin gecmis ayri
     * tutulur. Yeni bir varlikta liste bostur ya da tek elemanlidir - grafik o
     * zaman cizilmez, uydurma bir egri gosterilmez.
     *
     * TARIHLERIYLE birlikte doner: grafigin ustundeki etiket serinin gercekten
     * hangi araligi kapsadigini yazmali. Once yalniz fiyatlar donuyor ve etiket
     * sabit "12 ay" diyordu - fonlarda TEFAS'in aylik serisi gecmise
     * yazilmaya baslayinca bu acikca yanlis oldu.
     */
    fun observePriceHistory(assetKey: String): Flow<List<PricePoint>>

    /**
     * Kaynaktan yeniden ceker. Basarisizlikta son bilinen fiyatlar korunur.
     *
     * Sonuc [RefreshOutcome] tasir: "cekildi" ile "az once cekilmisti" ayni sey
     * degil. Ikisi de Result.success donuyordu ve kisitlanan yenileme ekranda
     * hicbir iz birakmiyordu - kullanicinin gordugu, dokundugu ama hicbir sey
     * olmayan bir dugmeydi.
     */
    suspend fun refresh(): Result<RefreshOutcome>

    suspend fun setManualPrice(assetKey: String, value: Double)

    suspend fun clearManualPrice(assetKey: String)
}

/**
 * Bir yenileme denemesinin sonucu.
 *
 * Basarili cekme ile kisitlanmis cekme AYRI TUTULUR. Ikisi de "basarili"
 * doniyordu; kullanici yenileye basiyor, ag da kaynak da yerinde oluyor ama
 * ekranda hicbir sey degismiyordu. Tek yapabildigi tekrar basmakti.
 */
sealed interface RefreshOutcome {
    /** Kaynaga cikildi ve fiyatlar yazildi. */
    data object Fetched : RefreshOutcome

    /**
     * Son basarili cekmenin uzerinden yeterli sure gecmedi; aga cikilmadi.
     * [retryInSeconds] kullaniciya soylenecek kalan sure.
     */
    data class Throttled(val retryInSeconds: Int) : RefreshOutcome
}

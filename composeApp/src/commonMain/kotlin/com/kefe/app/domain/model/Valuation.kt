package com.kefe.app.domain.model

/**
 * Varligin fiyat tablosundaki anahtari.
 *
 * Pozisyonun KENDI alanlarindan turer, kimliginden degil: kimlik
 * "pos_<anahtar>" bicimindeydi ama bu sozlesme islem ekleme ekranindaki bir
 * yardimciya gomuluydu. Tek istisna fon: fonun anahtari (fon kodu) baska hicbir
 * alanda tasinmiyor.
 *
 * Nakitte null doner - nakdin birim fiyati yoktur, girilen tutar dogrudan
 * degerdir.
 */
fun Position.priceKey(): String? = when (assetClass) {
    // Gramla satilan formlarda fiyati AYAR belirler (14/18/22 ayar gramin
    // kendi kotasyonu var); adetle satilanlarda formun kendisi belirler.
    //
    // Eski kayitlarda ayar kolonu bos: Gram icin varsayilan 24, o da
    // `gold_gram`a dustugu icin bu degisiklik mevcut pozisyonlarin fiyatini
    // degistirmez.
    AssetClass.Gold -> subtype?.let { s ->
        if (s.usesKarat()) (karat ?: s.defaultKarat()).priceKey() else s.priceKey()
    }

    AssetClass.Silver -> "silver_gram"
    AssetClass.Fx -> id.removePrefix(PositionIdPrefix).takeIf { it.isNotBlank() }
    AssetClass.Fund -> id.removePrefix(PositionIdPrefix).takeIf { it.isNotBlank() }
    // Fon ile ayni sozlesme: anahtar (sembol) baska hicbir alanda tasinmiyor,
    // kimlikten okunur. `pos_stock_THYAO.IS` -> `stock_THYAO.IS`.
    AssetClass.Stock -> id.removePrefix(PositionIdPrefix).takeIf { it.isNotBlank() }
    AssetClass.Cash -> null
}

const val PositionIdPrefix: String = "pos_"

fun GoldSubtype.priceKey(): String? = when (this) {
    GoldSubtype.Gram -> "gold_gram"
    GoldSubtype.Quarter -> "gold_quarter"
    GoldSubtype.Half -> "gold_half"
    GoldSubtype.Full -> "gold_full"
    GoldSubtype.Ata -> "gold_ata"
    // Has/Kulcenin KENDI kotasyonu var ve gram altindan farkli: tahtada Has
    // ₺6.627,24 iken gram ₺6.660,55 (9 Agustos 2026). Once gram anahtarina
    // dusuluyordu ve Has tutan bir pozisyon yaklasik %0,5 fazla degerleniyordu.
    // Kaynak bu satiri zaten cekiyor (LivePriceRemoteDataSource: HAS).
    GoldSubtype.Bullion -> "gold_bullion"
    GoldSubtype.Jewelry -> null
}

fun Karat.priceKey(): String = when (this) {
    Karat.K14 -> "gold_k14"
    Karat.K18 -> "gold_k18"
    Karat.K22 -> "gold_k22"
    Karat.K24 -> "gold_gram"
}

/**
 * BUGUN SATSAK elimize gececek birim fiyat - kuyumcunun/bankanin ALIS fiyati.
 *
 * Portfoy degeri bununla olculur. Once her yerde [buyPrice] kullaniliyordu, yani
 * portfoy "bugun ayni seyleri satin alsam ne oderdim" sorusunu yanitliyordu;
 * sorulan soru ise "elimdekini satsam ne alirim".
 *
 * Fonda alis kotasyonu yoktur, tek fiyat vardir.
 */
fun Price.sellPrice(): Double = bid ?: ask

/** ODEYECEGIMIZ birim fiyat - kuyumcunun/bankanin SATIS fiyati. */
fun Price.buyPrice(): Double = ask

/**
 * Makas: alis ile satis arasindaki fark, satis fiyatinin yuzdesi olarak.
 *
 * Ekranda gorunur olmali. Aksi halde bugun alinan varlik ilk anda zararda
 * gorunur ve bu bir hata sanilir - oysa gercek: kuyumcuya geri satsaniz
 * gercekten bu kadar aliyorsunuz.
 */
fun Price.spreadPercent(): Double? {
    val buying = bid ?: return null
    if (ask <= 0.0) return null
    return (ask - buying) / ask * 100.0
}

/**
 * Pozisyonu guncel fiyatla degerler.
 *
 * Saklanan `unitPrice` pozisyon ILK olustugunda yazilip bir daha hic
 * guncellenmiyordu: ceyrek ₺10.018'e alindiysa ekran haftalar sonra da ₺10.018
 * gosteriyordu. Ana ekrandaki en buyuk rakam yanlisti ve hedef ilerlemesi,
 * dagilim, getiri, gunluk fotograf hepsi ondan turuyordu.
 *
 * [price] null ise - tabloda o varlik yoksa - saklanan deger korunur: son
 * bilinen fiyat, sifirdan iyidir.
 */
fun Position.valuedAt(price: Price?, today: KefeDate): Position {
    if (price == null) return this
    val unit = price.sellPrice()
    if (unit <= 0.0) return this
    return copy(
        unitPrice = unit,
        value = quantity * unit,
        dailyChangePercent = price.todayChangePercent(today),
        weekChangePercent = price.weekChangePercent,
        monthChangePercent = price.monthChangePercent,
    )
}

/**
 * BUGUNE ait gunluk degisim - kotasyon bugunden degilse SIFIR.
 *
 * [Price.changePercent] "onceki kotasyondan bu yana" demektir, "bugun" demek
 * degil. Borsa cuma kapandiysa cumartesi okunan bu deger cumanin hareketidir;
 * kullanicinin serveti ise cumartesi hic degismemistir - varligi cuma
 * aksamindaki degerinde durur. Dolayisiyla dogru katki SIFIRDIR, cumanin
 * yuzdesi degil.
 *
 * Hafta sonu "bugunku getiri" satirinin dolu gorunmesinin ve gun icinde
 * degismesinin sebebi buydu.
 *
 * Gunu BILINMEYEN kotasyon da bugun sayilmaz: elle girilen fiyatin gunluk
 * hareketi yoktur, TCMB bulteninin hangi gune ait oldugunu ise bilmiyoruz.
 * Ikisinde de dogru cevap "bugun oynamadi".
 *
 * Haftalik/aylik degisim BU KURALDAN ETKILENMEZ: onlar gecmis serisinden ve
 * bir tolerans penceresiyle hesaplaniyor, yani zaten kapali gunlere dayanikli.
 *
 * SIFIR ile NULL ayri seylerdir. Kotasyon bugunden degilse cevap SIFIRDIR -
 * bunu biliyoruz, piyasa bugun oynamadi. Kotasyon bugundense ama degisim
 * bilinmiyorsa (kaynak sustu ve dunun kaydi da yok) cevap NULL'dur: ekran "—"
 * yazar ve o pozisyon grup toplamlarina hic girmez.
 */
fun Price.todayChangePercent(today: KefeDate): Double? =
    if (quoteDate == today) changePercent else 0.0

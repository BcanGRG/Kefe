package com.kefe.app.domain.model

/**
 * Varligin KIMLIK anahtari - "bu hangi varlik" sorusunun tek cevabi.
 *
 * Aylik plan "10 gr gram altin" dediginde o ay hangi alimlarin bu satira
 * sayilacagini bilmek zorunda. Pozisyon kimligi bunu soyleyemez: yeni kayitlar
 * "pos_<anahtar>" bicimindeyken eski ve ornek kayitlar `pos_ceyrek`, `pos_afa`,
 * `pos_usd`, `pos_vadeli` gibi serbest kimlikler tasiyor. Anahtar bu yuzden
 * kimlikten degil, pozisyonun KENDI ALANLARINDAN turetilir; kimlik yalniz
 * alanlarin bilmedigi seyde (fon kodu, hisse sembolu) okunur.
 *
 * Fiyat anahtari ([priceKey]) ile ayni sey DEGILDIR: fiyat anahtari "neyle
 * fiyatlanir", bu "hangi satir" sorusunu yanitlar. Has/Kulce ve takida ikisi
 * ayrilir.
 */

const val SilverAssetKey: String = "silver_gram"
const val CashAssetKey: String = "cash"
const val FundKeyPrefix: String = "fund_"
const val StockKeyPrefix: String = "stock_"

/**
 * Altin varliginin kimlik anahtari.
 *
 * KIMLIK ETKIN AYARI TASIMAK ZORUNDA. Once butun gram formlari `gold_gram`a
 * dusuyordu ve islem ekleme ekrani ayari kiyasladigi icin 22 ayar gram secimi
 * mevcut 24 ayar pozisyonuna ESLESMIYOR, ama yeni pozisyon kimligi tam da ONUN
 * kimligi oluyordu: iki ayri varligin defteri tek pozisyonda birlesiyor ve eski
 * miktar yeni ayarin kotasyonuyla degerleniyordu. 100 gr 24 ayarin yanina 10 gr
 * 22 ayar eklemek ekranda ~52 bin TL'lik sahte bir kayip yaziyordu.
 *
 * [Karat.K24] zaten `gold_gram` donuyor - mevcut 24 ayar pozisyonlarinin
 * kimligi DEGISMEZ.
 *
 * (Islem ekleme ekranindaydi; plan da ayni anahtari kullanmak zorunda oldugu
 * icin alan katmanina tasindi.)
 */
fun goldAssetKey(subtype: GoldSubtype, karat: Karat): String = when {
    subtype == GoldSubtype.Jewelry -> "gold_jewelry_${karat.milyem}"
    subtype.usesKarat() -> karat.priceKey()
    // Has/Kulcenin kendi kotasyonu var ve ayri bir varliktir - yoksa 24 ayar
    // gramla ayni satira duserdi.
    subtype == GoldSubtype.Bullion -> "gold_bullion"
    else -> subtype.priceKey().orEmpty()
}

/**
 * Pozisyonun kimlik anahtari; cozulemiyorsa null.
 *
 * null YALNIZ eski ve eksik kayitlarda cikar: alt turu bos bir altin satiri ya
 * da adi "KOD · Ad" bicimine uymayan eski bir fon. Bunlar plana eslesemez -
 * yanlis bir satira eslesmektense hic eslesmemek dogrusu.
 */
fun Position.assetKey(): String? {
    val idKey = id.removePrefix(PositionIdPrefix)
    return when (assetClass) {
        // Ayari bos eski kayit formun varsayilanina duser - islem ekleme ekrani
        // da o kaydi boyle eslestiriyor, iki taraf ayni satiri gormeli.
        AssetClass.Gold -> subtype?.let { goldAssetKey(it, karat ?: it.defaultKarat()) }

        AssetClass.Silver -> SilverAssetKey

        AssetClass.Fx -> (
            Currency.fromPriceKey(idKey)
                ?: Currency.entries.firstOrNull { it.code.equals(idKey, ignoreCase = true) }
                ?: Currency.entries.firstOrNull { it.label() == name }
            )?.priceKey()

        AssetClass.Fund -> if (idKey.startsWith(FundKeyPrefix)) {
            idKey.lowercase()
        } else {
            codeFromName(name)?.let { FundKeyPrefix + it.lowercase() }
        }

        // Hisse anahtari sembolun kucuk harfli halidir (bkz. stockAssetKey).
        AssetClass.Stock -> if (idKey.startsWith(StockKeyPrefix, ignoreCase = true)) {
            idKey.lowercase()
        } else {
            codeFromName(name)?.let { StockKeyPrefix + it.lowercase() }
        }

        // Nakit tek varliktir: "Vadeli Mevduat" ile "Vadesiz Hesap" ayni
        // anahtara duser - islem ekleme ekrani da nakit alimini ilk nakit
        // pozisyonuna yaziyor.
        AssetClass.Cash -> CashAssetKey
    }
}

/**
 * "AFA · Ak Portföy Altın" -> "AFA". Kod bicimine uymayan ad null doner: eski
 * bir satirin serbest adindan anahtar uydurmak, iki farkli fonu tek satira
 * dusurebilirdi.
 */
private fun codeFromName(name: String): String? {
    if (!name.contains(" · ")) return null
    val code = name.substringBefore(" · ").trim()
    return code.takeIf { CodePattern.matches(it) }
}

private val CodePattern = Regex("^[A-Za-z0-9][A-Za-z0-9.\\-]{1,14}$")

/** Anahtarin tasidigi bilgi - [parseAssetKey] ile anahtardan geri cozulur. */
data class AssetKeyInfo(
    val assetClass: AssetClass,
    val unit: QuantityUnit,
    val goldSubtype: GoldSubtype? = null,
    val karat: Karat? = null,
    val currency: Currency? = null,
    /** Fon kodu ya da hisse sembolu, buyuk harfle. */
    val code: String? = null,
)

/**
 * Anahtardan varligi geri cozer; taninmayan anahtar null doner.
 *
 * Plan satiri varligi ANAHTAR olarak saklar - henuz hic alinmamis bir fonun
 * pozisyonu yoktur. Birim ve sinif buradan okunur.
 */
fun parseAssetKey(key: String): AssetKeyInfo? {
    val lower = key.lowercase()
    fun gold(subtype: GoldSubtype, karat: Karat?) = AssetKeyInfo(
        assetClass = AssetClass.Gold,
        unit = if (subtype.isGramBased()) QuantityUnit.Gram else QuantityUnit.Piece,
        goldSubtype = subtype,
        karat = karat,
    )
    return when {
        lower == "gold_gram" -> gold(GoldSubtype.Gram, Karat.K24)
        lower == "gold_k22" -> gold(GoldSubtype.Gram, Karat.K22)
        lower == "gold_k18" -> gold(GoldSubtype.Gram, Karat.K18)
        lower == "gold_k14" -> gold(GoldSubtype.Gram, Karat.K14)
        lower == "gold_bullion" -> gold(GoldSubtype.Bullion, null)
        lower == "gold_quarter" -> gold(GoldSubtype.Quarter, null)
        lower == "gold_half" -> gold(GoldSubtype.Half, null)
        lower == "gold_full" -> gold(GoldSubtype.Full, null)
        lower == "gold_ata" -> gold(GoldSubtype.Ata, null)
        lower.startsWith("gold_jewelry_") -> {
            val milyem = lower.removePrefix("gold_jewelry_").toIntOrNull()
            Karat.entries.firstOrNull { it.milyem == milyem }?.let { gold(GoldSubtype.Jewelry, it) }
        }

        lower == SilverAssetKey -> AssetKeyInfo(AssetClass.Silver, QuantityUnit.Gram)
        lower == CashAssetKey -> AssetKeyInfo(AssetClass.Cash, QuantityUnit.Currency)
        lower.startsWith(FundKeyPrefix) && lower.length > FundKeyPrefix.length ->
            AssetKeyInfo(AssetClass.Fund, QuantityUnit.Share, code = lower.removePrefix(FundKeyPrefix).uppercase())

        lower.startsWith(StockKeyPrefix) && lower.length > StockKeyPrefix.length ->
            AssetKeyInfo(AssetClass.Stock, QuantityUnit.Lot, code = lower.removePrefix(StockKeyPrefix).uppercase())

        else -> Currency.fromPriceKey(lower)?.let {
            AssetKeyInfo(AssetClass.Fx, QuantityUnit.Currency, currency = it)
        }
    }
}

/** Gramla olculen altin formlari; digerleri adetle. */
fun GoldSubtype.isGramBased(): Boolean =
    this == GoldSubtype.Gram || this == GoldSubtype.Jewelry || this == GoldSubtype.Bullion

/**
 * Anahtarin fiyat tablosundaki karsiligi; nakitte null.
 *
 * Takinin fiyati ayarinin gram kotasyonudur; Has/Kulcenin kendi kotasyonu var.
 */
fun priceKeyOfAsset(key: String): String? {
    val info = parseAssetKey(key) ?: return null
    return when (info.assetClass) {
        AssetClass.Gold -> when (info.goldSubtype) {
            GoldSubtype.Jewelry -> info.karat?.priceKey()
            null -> null
            else -> key.lowercase()
        }

        AssetClass.Cash -> null
        else -> key.lowercase()
    }
}

/**
 * Anahtarin gorunen adi - henuz pozisyonu olmayan bir varlik icin.
 *
 * Pozisyon varsa onun adi tercih edilir (kullanicinin gordugu ad odur); bu
 * yalniz plan kataloğundaki ve hic alinmamis satirlardaki ad.
 */
fun catalogName(key: String): String {
    val info = parseAssetKey(key) ?: return key
    return when (info.assetClass) {
        AssetClass.Gold -> when (info.goldSubtype) {
            GoldSubtype.Gram -> if (info.karat == null || info.karat == Karat.K24) {
                "Gram Altın"
            } else {
                "${info.karat.label()} Gram Altın"
            }

            GoldSubtype.Jewelry -> "${info.karat?.label().orEmpty()} Takı".trim()
            GoldSubtype.Bullion -> "Has/Külçe Altın"
            GoldSubtype.Quarter -> "Çeyrek Altın"
            GoldSubtype.Half -> "Yarım Altın"
            GoldSubtype.Full -> "Tam Altın"
            GoldSubtype.Ata -> "Ata/Cumhuriyet Altın"
            null -> "Altın"
        }

        AssetClass.Silver -> "Gram Gümüş"
        AssetClass.Fx -> info.currency?.label() ?: key
        AssetClass.Fund, AssetClass.Stock -> info.code ?: key
        AssetClass.Cash -> "Nakit"
    }
}

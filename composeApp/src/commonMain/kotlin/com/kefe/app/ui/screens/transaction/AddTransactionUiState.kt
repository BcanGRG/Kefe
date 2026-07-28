package com.kefe.app.ui.screens.transaction

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.Karat
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.label
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trLower

/** Iki adimli akis: once "ne", sonra "ne kadar". */
enum class AddTransactionStep { Asset, Amount }

/** Alt tur satiri - ad modelden, fiyat metni fiyat tablosundan gelir. */
data class SubtypeOption(
    val subtype: GoldSubtype,
    val priceText: String,
)

/** Ayar cipi ve altindaki hesap satiri icin gram fiyati. */
data class KaratOption(
    val karat: Karat,
    val gramPrice: Double,
)

/** Fon arama sonucu satiri. */
data class FundResult(
    val assetKey: String,
    val code: String,
    val name: String,
    val issuer: String,
    val price: Double,
    val changePercent: Double,
)

/**
 * "Tekrar ekle" kisayolunun kaynagi. Yalniz etiket degil, formu dolduracak
 * alanlar da tasinir - kisayol tek dokunusta ikinci adima gecebilsin diye.
 */
data class LastAdded(
    val label: String,
    val assetClass: AssetClass,
    val subtype: GoldSubtype,
    val karat: Karat,
    val quantityText: String,
)

data class AddTransactionUiState(
    val step: AddTransactionStep = AddTransactionStep.Asset,

    // --- 1. adim -----------------------------------------------------------
    val assetClass: AssetClass = AssetClass.Gold,
    val subtypes: List<SubtypeOption> = emptyList(),
    val selectedSubtype: GoldSubtype = GoldSubtype.Quarter,
    val karatOptions: List<KaratOption> = emptyList(),
    val karat: Karat = Karat.K22,
    val gramText: String = "",
    val fundQuery: String = "",
    val fundResults: List<FundResult> = emptyList(),
    val selectedFundKey: String? = null,

    // --- 2. adim -----------------------------------------------------------
    val side: TradeSide = TradeSide.Buy,
    val quantityText: String = "",
    /** Piyasadan gelen birim fiyat - "Güncele dön" bu degere doner. */
    val marketPrice: Double = 0.0,
    /** Alanda gorunen fiyat metni; elle degistirilince [priceManual] acilir. */
    val unitPriceText: String = "",
    val priceManual: Boolean = false,
    /** ViewModel gercek gunle doldurur; buradaki deger yalniz onizleme icindir. */
    val date: KefeDate = KefeDate(2026, 7, 12),
    val isToday: Boolean = true,
    val extraExpanded: Boolean = false,
    val feeText: String = "",
    val note: String = "",
    val storage: String = "",

    // --- Ortak -------------------------------------------------------------
    val lastAdded: LastAdded? = null,
    /** Cevrimdisi kayit: serit, "Bekliyor" rozeti ve farkli CTA metni. */
    val offline: Boolean = false,
    /** Alt notta adi gecen diger uye - kayit ona da gorunecek. */
    val partnerName: String = "",
    val saving: Boolean = false,
)

/**
 * Bir kereye mahsus olanlar.
 *
 * "Kaydedildi" bir DURUM degil, bir OLAYDIR: once `saved: Boolean` olarak
 * durumda tutuluyordu ve tuketildikten sonra elle temizlenmesi gerekiyordu -
 * temizlenmezse sheet bir daha acilir acilmaz kapaniyordu.
 */
sealed interface AddTransactionEffect {
    /** Kayit yazildi, sheet kapanmali. */
    data object Saved : AddTransactionEffect

    /** Yazma patladi. Kullaniciya soylenmezse girdigi islem sessizce kaybolur. */
    data class SaveFailed(val message: String) : AddTransactionEffect
}

sealed interface AddTransactionIntent {
    data class SelectAssetClass(val assetClass: AssetClass) : AddTransactionIntent
    data class SelectSubtype(val subtype: GoldSubtype) : AddTransactionIntent
    data class SelectKarat(val karat: Karat) : AddTransactionIntent
    data class ChangeGram(val text: String) : AddTransactionIntent
    data class ChangeFundQuery(val text: String) : AddTransactionIntent
    data class SelectFund(val assetKey: String) : AddTransactionIntent

    data object Continue : AddTransactionIntent
    data object Back : AddTransactionIntent
    data object RepeatLast : AddTransactionIntent

    data class SelectSide(val side: TradeSide) : AddTransactionIntent
    data class ChangeQuantity(val text: String) : AddTransactionIntent
    data object IncrementQuantity : AddTransactionIntent
    data object DecrementQuantity : AddTransactionIntent
    data class ChangeUnitPrice(val text: String) : AddTransactionIntent
    data object ResetPriceToMarket : AddTransactionIntent

    data object ToggleExtra : AddTransactionIntent
    data class ChangeFee(val text: String) : AddTransactionIntent
    data class ChangeNote(val text: String) : AddTransactionIntent
    data class ChangeStorage(val text: String) : AddTransactionIntent

    data object Save : AddTransactionIntent
}

// --- Turetilen degerler ------------------------------------------------------

val AddTransactionUiState.isFirstStep: Boolean
    get() = step == AddTransactionStep.Asset

val AddTransactionUiState.stepTitle: String
    get() = if (isFirstStep) "Ne?" else "Ne kadar?"

val AddTransactionUiState.stepLabel: String
    get() = if (isFirstStep) "1. adım · Varlık türünü seçin" else "2. adım · $selectionName"

/** Secimin okunur adi - ikinci adimin alt basligi ve pozisyon adi. */
val AddTransactionUiState.selectionName: String
    get() = when (assetClass) {
        AssetClass.Gold -> when (selectedSubtype) {
            GoldSubtype.Jewelry -> "${karat.label()} ${GoldSubtype.Jewelry.label()}"
            GoldSubtype.Gram -> "Gram Altın"
            GoldSubtype.Bullion -> "Has/Külçe Altın"
            else -> "${selectedSubtype.label()} Altın"
        }

        AssetClass.Fund -> selectedFund?.let { "${it.code} · ${it.name}" } ?: assetClass.label()
        else -> assetClass.label()
    }

val AddTransactionUiState.selectedFund: FundResult?
    get() = fundResults.firstOrNull { it.assetKey == selectedFundKey }

/** Miktarin birimi secime bagli: cil altin adet, bilezik gram, fon pay. */
val AddTransactionUiState.quantityUnit: QuantityUnit
    get() = when (assetClass) {
        AssetClass.Gold -> when (selectedSubtype) {
            GoldSubtype.Jewelry, GoldSubtype.Gram, GoldSubtype.Bullion -> QuantityUnit.Gram
            else -> QuantityUnit.Piece
        }

        AssetClass.Silver -> QuantityUnit.Gram
        AssetClass.Fund -> QuantityUnit.Share
        AssetClass.Fx, AssetClass.Cash -> QuantityUnit.Currency
    }

/** Fon ve doviz kurusla gosterilir; kalanlar tam TL. */
val AddTransactionUiState.priceDecimals: Int
    get() = when (assetClass) {
        AssetClass.Fund, AssetClass.Fx -> 2
        else -> 0
    }

val AddTransactionUiState.quantity: Double get() = quantityText.parseTrNumber()

val AddTransactionUiState.unitPrice: Double get() = unitPriceText.parseTrNumber()

val AddTransactionUiState.fee: Double get() = feeText.parseTrNumber()

val AddTransactionUiState.total: Double get() = quantity * unitPrice + fee

val AddTransactionUiState.priceDifference: Double get() = unitPrice - marketPrice

/** Elle fiyat girildiginde alanin altindaki karsilastirma satiri. */
val AddTransactionUiState.priceCompareLine: String
    get() = "Güncel piyasa: " + Money.tl(marketPrice, decimals = priceDecimals) +
        " · Fark " + Money.tlSigned(priceDifference, decimals = priceDecimals)

/** Bilezik/taki icin secili ayarin gram fiyati; digerlerinde kullanilmaz. */
val AddTransactionUiState.karatGramPrice: Double
    get() = karatOptions.firstOrNull { it.karat == karat }?.gramPrice ?: 0.0

/** "22 ayar gram fiyatı ₺15.308 · 62,4 gr ≈ ₺955.219" */
val AddTransactionUiState.karatLine: String
    get() {
        val grams = gramText.parseTrNumber()
        return karat.label() + " gram fiyatı " + Money.tl(karatGramPrice) + " · " +
            Money.quantity(grams, "gr", decimals = 1) + " ≈ " + Money.tl(grams * karatGramPrice)
    }

val AddTransactionUiState.ctaText: String
    get() = when {
        isFirstStep -> "Devam"
        offline -> "Çevrimdışı kaydet"
        else -> "Kaydet"
    }

/** Altligin ince aciklama satiri: cevrimdisiyken uyari, aksi halde hesap. */
val AddTransactionUiState.footNote: String
    get() {
        if (offline) {
            val partner = partnerName.takeIf { it.isNotBlank() }
                ?: return "Kayıt cihazda tutulur; bağlanınca eşitlenir."
            return "Kayıt cihazda tutulur; bağlanınca ${trGenitive(partner)} telefonunda da görünür."
        }
        val head = quantityText.ifBlank { "0" } + " × " +
            Money.tl(unitPrice, decimals = priceDecimals)
        return if (fee > 0.0) head + " + " + Money.tl(fee) + " işçilik" else head
    }

val AddTransactionUiState.canContinue: Boolean
    get() = when (assetClass) {
        AssetClass.Gold ->
            if (selectedSubtype == GoldSubtype.Jewelry) gramText.parseTrNumber() > 0.0 else true

        AssetClass.Fund -> selectedFundKey != null
        else -> true
    }

val AddTransactionUiState.canSave: Boolean
    get() = quantity > 0.0 && unitPrice > 0.0

val AddTransactionUiState.canSubmit: Boolean
    get() = if (isFirstStep) canContinue else canSave && !saving

/** Fon aramasi: kod ya da ad icinde gecen sorgu. Bos sorgu tum listeyi verir. */
fun FundResult.matches(query: String): Boolean {
    val q = query.trim().trLower()
    if (q.isEmpty()) return true
    return code.trLower().contains(q) || name.trLower().contains(q) || issuer.trLower().contains(q)
}

// --- Ayristirma / ek yardimcilari -------------------------------------------

/**
 * tr-TR sayi girisi: ondalik virgul, binlik nokta. Virgul yoksa nokta binlik
 * ayraci sayilir - kullanici "26.400" yazdiginda 26,4 olmaz.
 */
fun String.parseTrNumber(): Double {
    val cleaned = trim().replace(" ", "").replace(Money.LIRA, "")
    val normalized = if (cleaned.contains(',')) {
        cleaned.replace(".", "").replace(',', '.')
    } else {
        cleaned.replace(".", "")
    }
    return normalized.toDoubleOrNull() ?: 0.0
}

private const val Vowels = "aeıioöuüAEİIOÖUÜ"

/**
 * Isme tamlayan eki: "Ayşe" -> "Ayşe'nin". Son unlu kalinlik/incelik ve
 * duzluk/yuvarlaklik belirler; sesli ile bitende araya kaynastirma "n" girer.
 */
internal fun trGenitive(name: String): String {
    if (name.isBlank()) return name
    val lastVowel = name.lastOrNull { it in Vowels }?.toString()?.trLower()?.firstOrNull() ?: 'e'
    val suffix = when (lastVowel) {
        'a', 'ı' -> "ın"
        'e', 'i' -> "in"
        'o', 'u' -> "un"
        else -> "ün"
    }
    val buffer = if (name.last() in Vowels) "n" else ""
    return name + "'" + buffer + suffix
}

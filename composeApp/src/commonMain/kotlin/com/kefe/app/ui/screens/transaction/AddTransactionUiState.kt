package com.kefe.app.ui.screens.transaction

import com.kefe.app.data.sync.CloudState
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Currency
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.Karat
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.label
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.maxPriceDecimals
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

/** Doviz satiri - ad modelden, fiyat metni fiyat tablosundan gelir. */
data class CurrencyOption(
    val currency: Currency,
    val priceText: String,
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
    /** TEFAS'ta canli fon aramasi surerken - buton yerine donen halka. */
    val fundSearching: Boolean = false,
    /** Canli arama sonuc getirmediyse alanin altindaki uyari. */
    val fundSearchError: String? = null,
    /** Doviz secimi. Once secim yoktu ve her kayit sessizce USD oluyordu. */
    val currency: Currency = Currency.Usd,
    val currencyOptions: List<CurrencyOption> = emptyList(),

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
    /**
     * Duzenlenen islemin kimligi. null ise yeni kayit.
     *
     * Sheet ikisini de gorur: baslik, CTA ve alt not buna gore degisir. Yanlis
     * girilen islem eskiden yalniz silinip yeniden eklenerek duzeltilebiliyordu;
     * satirdaki kalem ikonu hicbir sey yapmiyordu.
     */
    val editingTransactionId: String? = null,
    val lastAdded: LastAdded? = null,
    /**
     * FIYAT cevrimdisi: guncel kotasyon alinamadi, son bilinen fiyat kullaniliyor.
     * Kaydin esitlenmesiyle ILGISI YOK - bunun icin [cloudState] var.
     */
    val offline: Boolean = false,
    /** Esitleme durumu: serit, "Bekliyor" rozeti ve CTA metnini bu belirler. */
    val cloudState: CloudState = CloudState.Off,
    /** Alt notta adi gecen diger uye - kayit ona da gorunecek. */
    val partnerName: String = "",
    val saving: Boolean = false,

    /** Hedef seciciye dokulen hedefler. Bos ise secici hic cizilmez. */
    val availableGoals: List<Goal> = emptyList(),
    /**
     * Bu varligin atanacagi hedef. null = hedefsiz (dogrudan toplam birikime).
     * Secilirse kayit sonrasi varlik o hedefe atanir ve "karsilayanlar"a duser.
     */
    val selectedGoalId: String? = null,
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
    /** Varligi bir hedefe ata (ya da tekrar dokununca kaldir). null = hedefsiz. */
    data class SelectGoal(val goalId: String?) : AddTransactionIntent
    data class SelectKarat(val karat: Karat) : AddTransactionIntent
    data class SelectCurrency(val currency: Currency) : AddTransactionIntent
    data class ChangeGram(val text: String) : AddTransactionIntent
    data class ChangeFundQuery(val text: String) : AddTransactionIntent
    data class SelectFund(val assetKey: String) : AddTransactionIntent
    /** Yereldeki 5 fonda yoksa girilen kodu TEFAS'tan canli cek. */
    data object SearchFundOnline : AddTransactionIntent

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

    /** Var olan bir kaydi forma yukler; sheet dogrudan ikinci adimda acilir. */
    data class EditTransaction(val transactionId: String) : AddTransactionIntent

    /**
     * Formu temiz bir yeni kayit icin hazirlar.
     *
     * ViewModel sheet kapaninca olmedigi icin bir onceki acilisin alanlari
     * duruyordu. Duzenlemeden sonra en tehlikelisi [editingTransactionId]:
     * temizlenmezse yeni kayit eski islemi de silerdi.
     */
    data class StartNew(
        val side: TradeSide,
        /**
         * Doldurulacak varlik. null ise form bostur ve 1. adimdan baslar.
         *
         * Varlik detayindan "Alım/Satış Ekle" denince kullanici hangi varlikta
         * oldugunu ZATEN soylemis oluyor; onu bir kez daha secmek gereksiz.
         */
        val positionId: String? = null,
    ) : AddTransactionIntent
}

// --- Turetilen degerler ------------------------------------------------------

val AddTransactionUiState.isFirstStep: Boolean
    get() = step == AddTransactionStep.Asset

val AddTransactionUiState.stepTitle: String
    get() = when {
        isEditing -> "İşlemi düzenle"
        isFirstStep -> "Ne?"
        else -> "Ne kadar?"
    }

val AddTransactionUiState.stepLabel: String
    get() = when {
        // Duzenlemede adim sayaci yaniltici olurdu: kullanici bir akisa
        // baslamiyor, var olan bir kaydi degistiriyor.
        isEditing -> selectionName
        isFirstStep -> "1. adım · Varlık türünü seçin"
        else -> "2. adım · $selectionName"
    }

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
        // "Döviz" degil "Euro": ikinci adimda hangi para biriminin girildigi
        // gorunmezse kullanici yanlis kuru kaydettigini anlamaz.
        AssetClass.Fx -> currency.label()
        else -> assetClass.label()
    }

val AddTransactionUiState.selectedFund: FundResult?
    get() = fundResults.firstOrNull { it.assetKey == selectedFundKey }

/**
 * TEFAS'ta canli aranabilecek fon kodu - yoksa null (arama satiri gizli).
 *
 * TEFAS kodlari HARF-RAKAM karisimidir ve harfle baslar: AFA, TTE, ama AN1, TP2,
 * GO1 de vardir. Once kosul "hepsi harf" idi; rakam tasiyan her kod arama
 * satirini HIC acmiyordu - kullanici "AN1" yazip bir sey olmamasini izliyordu.
 * Ilk karakterin harf olmasi sarti, "22" gibi salt sayilari eleme amacini
 * korur. ASCII disi harf kabul edilmez: TEFAS kodlarinda yoktur, "çeyrek"
 * yazan birini bosuna aga cikarmayalim.
 */
val AddTransactionUiState.fundSearchCode: String?
    get() {
        val q = fundQuery.trim()
        if (q.length !in 2..6) return null
        if (!q.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }) return null
        if (!q.first().isLetter()) return null
        if (fundResults.any { it.code.equals(q, ignoreCase = true) }) return null
        return q.uppercase()
    }

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

/**
 * "Güncel piyasa: ₺x" ipucusunun hane sayisi - DEGERDEN turer.
 *
 * Once fon ve doviz iki, kalani sifir haneye sabitti: kullanicinin alani
 * dolduran gercek fiyat (₺108,394521) ipucunda "₺108,39" gorunuyor, iki rakam
 * birbirini tutmuyordu.
 */
val AddTransactionUiState.priceDecimals: Int
    get() = Money.decimals(marketPrice, assetClass.maxPriceDecimals())

val AddTransactionUiState.quantity: Double get() = quantityText.parseTrNumber()

val AddTransactionUiState.unitPrice: Double get() = unitPriceText.parseTrNumber()

val AddTransactionUiState.fee: Double get() = feeText.parseTrNumber()

val AddTransactionUiState.total: Double get() = quantity * unitPrice + fee

/**
 * Bu varlik icin piyasadan bir fiyat gelmis mi.
 *
 * Sifir bir fiyat DEGILDIR; fiyatin olmamasidir. Cevrimdisi ilk acilista
 * onbellek bos oldugu icin sifir geliyordu ve ekran bunu "güncel fiyat" diye
 * gosterip Kaydet'i sebepsiz pasif birakiyordu.
 */
val AddTransactionUiState.hasMarketPrice: Boolean get() = marketPrice > 0.0

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

/**
 * Kayit su an buluta GIDEMEZ: serit cizilir, "Bekliyor" rozeti ve farkli CTA.
 *
 * Bulut KAPALI (giris yok) bunun disindadir: uygulama cevrimdisi tam calisir,
 * her kaydi "çevrimdışı" diye isaretlemek giris yapmamis kullaniciya bir sey
 * bozukmus gibi gosterirdi.
 */
val AddTransactionUiState.cloudUnreachable: Boolean
    get() = cloudState == CloudState.Unreachable

val AddTransactionUiState.ctaText: String
    get() = when {
        isFirstStep -> "Devam"
        cloudUnreachable -> "Çevrimdışı kaydet"
        isEditing -> "Güncelle"
        else -> "Kaydet"
    }

val AddTransactionUiState.isEditing: Boolean
    get() = editingTransactionId != null

/**
 * Altligin ince aciklama satiri.
 *
 * Sira onemli: buluta ulasilamiyorsa kaydin akibeti, fiyat eskiyse fiyatin
 * kaynagi, aksi halde hesap. Once ikisi tek bayraktan geliyordu ve fiyat ucu
 * tokezleyince ekran "bağlanınca eşitlenir" diyordu - esitleme zaten
 * calisiyorken.
 */
val AddTransactionUiState.footNote: String
    get() {
        if (cloudUnreachable) {
            val partner = partnerName.takeIf { it.isNotBlank() }
                ?: return "Kayıt cihazda tutulur; bağlanınca eşitlenir."
            return "Kayıt cihazda tutulur; bağlanınca ${trGenitive(partner)} telefonunda da görünür."
        }
        if (offline) return "Son bilinen fiyatla kaydedilir; tutarı elle düzeltebilirsiniz."
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

package com.kefe.app.ui.screens.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.label
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.domain.repository.RefreshOutcome
import com.kefe.app.ui.format.ChangePeriod
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.changeIn
import com.kefe.app.ui.format.changeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Piyasa ekrani. Bu ekran uygulamanin emniyet supabidir: kaynak coktugunde
 * kullanici fiyati elle girer ve hesap durmaz. Bu yuzden [MarketIntent.SetManualPrice]
 * ile [MarketIntent.ClearManualPrice] birinci sinif eylemlerdir, hata yolu degil.
 */
class MarketViewModel(
    private val priceRepository: PriceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    /** Sayfa acilirken alani mevcut fiyatla doldurabilmek icin ham fiyatlar saklanir. */
    private var lastPrices: List<Price> = emptyList()

    init {
        observePrices()
        refresh()
    }

    fun onIntent(intent: MarketIntent) {
        when (intent) {
            MarketIntent.Refresh -> refresh()

            MarketIntent.DismissNotice -> _state.value = _state.value.copy(notice = null)

            // Donem degisince tablo AYNI fiyatlarla yeniden bicimlenir; aga
            // cikilmaz - hafta ve ay zaten elde olan veriden hesaplaniyor.
            is MarketIntent.SelectPeriod -> _state.value = _state.value.copy(
                period = intent.period,
                sections = lastPrices.toSections(intent.period),
            )

            is MarketIntent.OpenManualPrice -> openEdit(intent.assetKey)

            MarketIntent.DismissManualPrice -> _state.value = _state.value.copy(edit = null)

            is MarketIntent.ChangeManualPrice -> _state.value = _state.value.copy(
                edit = _state.value.edit?.copy(input = intent.text, invalid = false),
            )

            MarketIntent.SetManualPrice -> commitManualPrice()

            is MarketIntent.ClearManualPrice -> viewModelScope.launch {
                priceRepository.clearManualPrice(intent.assetKey)
                if (_state.value.edit?.assetKey == intent.assetKey) {
                    _state.value = _state.value.copy(edit = null)
                }
            }
        }
    }

    private fun observePrices() {
        viewModelScope.launch {
            priceRepository.observePrices().collect { board ->
                lastPrices = board.prices
                _state.value = _state.value.copy(
                    sections = board.prices.toSections(_state.value.period),
                    updatedAtLabel = board.updatedAtLabel,
                    freshness = board.freshness,
                )
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, notice = null)
            val outcome = priceRepository.refresh().getOrNull()
            _state.value = _state.value.copy(
                refreshing = false,
                // Kisitlanan yenileme Ozet'teki ile AYNI: kullanici yenileye
                // basiyor, ekranda hicbir sey degismiyordu. Fiyat zaten taze,
                // uyari da hata degil - vurgu renginde bir bilgidir.
                notice = (outcome as? RefreshOutcome.Throttled)?.let {
                    "Fiyatlar az önce güncellendi — ${it.retryInSeconds} sn sonra tekrar denenebilir."
                },
            )
        }
    }

    private fun openEdit(assetKey: String) {
        val price = lastPrices.firstOrNull { it.assetKey == assetKey }
        _state.value = _state.value.copy(
            edit = MarketPriceEdit(
                assetKey = assetKey,
                name = displayName(assetKey, price?.label.orEmpty()),
                // Alan bos degil mevcut fiyatla acilir: kullanici genelde kucuk bir
                // duzeltme yapar, sifirdan yazmaz.
                input = price?.let {
                    Money.number(it.ask, it.assetClass.priceDecimals(it.ask))
                }.orEmpty(),
                isManual = price?.isManual == true,
            ),
        )
    }

    private fun commitManualPrice() {
        val edit = _state.value.edit ?: return
        val value = edit.input.toTurkishDoubleOrNull()
        if (value == null || value <= 0.0) {
            _state.value = _state.value.copy(edit = edit.copy(invalid = true))
            return
        }
        viewModelScope.launch {
            priceRepository.setManualPrice(edit.assetKey, value)
            _state.value = _state.value.copy(edit = null)
        }
    }
}

// --- Esleme ----------------------------------------------------------------

private const val FundNote = "Fon fiyatları günde bir kez güncellenir (TEFAS kapanışı)."

/**
 * Hisse bolumunun notu. Cevrimin YAZILMASI sart: ABD hissesinin satirinda TL
 * yaziyor ama kotasyon dolar - kur da oynadigi icin TL rakami hissenin kendi
 * degisiminden farkli hareket eder ve bu, aciklanmazsa hata gibi gorunur.
 */
private const val StockNote =
    "Borsa İstanbul ve ABD borsaları. Yabancı borsadaki fiyatlar günün kuruyla " +
        "TL'ye çevrilir; yüzde değişim hissenin kendi borsasındaki değişimidir."

/**
 * Tabloda gorunen kisa adlar. Depo etiketleri urun adidir ("Gram Altın"), tablo
 * ise dar sutuna sigan ve ayirt edici olani ister ("Gram Altın 24"). Haritadaki
 * sira ayni zamanda bolum ici siralamayi belirler.
 */
private val DisplayNames: Map<String, String> = linkedMapOf(
    "gold_gram" to "Gram Altın 24",
    "gold_quarter" to "Çeyrek",
    "gold_half" to "Yarım",
    "gold_full" to "Tam",
    "gold_ata" to "Ata / Cumh.",
    "gold_k22" to "22 Ayar",
    "gold_k18" to "18 Ayar",
    "gold_k14" to "14 Ayar",
    "silver_gram" to "Gram Gümüş",
    "usd_try" to "USD / TRY",
    "eur_try" to "EUR / TRY",
    "fund_afa" to "AFA · Ak Portföy",
    "fund_tte" to "TTE · Türkiye Tek.",
    "fund_ipv" to "IPV · İş Portföy",
)

private val RowOrder: List<String> = DisplayNames.keys.toList()

private fun displayName(assetKey: String, fallback: String): String =
    DisplayNames[assetKey] ?: fallback

/** Bilinmeyen anahtar sona duser; sortedBy kararli oldugu icin geldigi sirayi korur. */
private fun orderIndex(assetKey: String): Int =
    RowOrder.indexOf(assetKey).let { if (it < 0) Int.MAX_VALUE else it }

private fun List<Price>.toSections(period: ChangePeriod): List<MarketSection> =
    AssetClass.entries.mapNotNull { assetClass ->
        val rows = filter { it.assetClass == assetClass }
            .sortedBy { orderIndex(it.assetKey) }
            .map { it.toRow(period) }
        if (rows.isEmpty()) {
            null
        } else {
            MarketSection(
                assetClass = assetClass,
                title = assetClass.label(),
                rows = rows,
                note = when (assetClass) {
                    AssetClass.Fund -> FundNote
                    AssetClass.Stock -> StockNote
                    else -> null
                },
            )
        }
    }

private fun Price.toRow(period: ChangePeriod): MarketRow {
    val change = changeIn(period)
    return MarketRow(
        assetKey = assetKey,
        name = displayName(assetKey, label),
        // Alis ve satis AYRI hesaplanir: makas kurusun altinda olabiliyor ve
        // tek bir hane sayisi ikisinden birini kirpiyor.
        bidText = bid?.let { Money.tl(it, decimals = assetClass.priceDecimals(it)) } ?: "—",
        askText = Money.tl(ask, decimals = assetClass.priceDecimals(ask)),
        changePercent = change,
        changeText = changeText(change),
        sourceLine = source.label() + " · " + timestamp,
        isManual = isManual,
    )
}

/**
 * tr-TR giris: ondalik virgul, binlik nokta. Virgul yoksa metin oldugu gibi
 * denenir - "24.60" yazan kullaniciyi da kirmayalim diye degil, aksine nokta
 * binlik ayraci olabileceginden yalniz virgullu girdide temizlik yapilir.
 */
private fun String.toTurkishDoubleOrNull(): Double? {
    val cleaned = trim().replace(" ", "").replace("₺", "")
    val normalized = if (cleaned.contains(',')) {
        cleaned.replace(".", "").replace(',', '.')
    } else {
        cleaned
    }
    return normalized.toDoubleOrNull()
}

package com.kefe.app.ui.screens.transaction

import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.ActivityKind
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Currency
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.Karat
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.MemberRole
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.SyncState
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.buyPrice
import com.kefe.app.domain.model.newId
import com.kefe.app.domain.model.priceKey
import com.kefe.app.domain.model.sellPrice
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Islem ekleme sayfasi. MVI-lite: tek durum akisi, tek [onIntent] girisi.
 *
 * Fiyatlar hicbir yerde sabit YAZILMAZ; alt tur satirlarindaki tutarlar, ayar
 * gram fiyatlari ve fon kotasyonlari [PriceRepository] tablosundan gelir.
 * Boylece bu sayfa Piyasa ekraniyla ayni rakami gosterir ve kaynak degistiginde
 * ekran degismez.
 */
class AddTransactionViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val priceRepository: PriceRepository,
    private val clock: KefeClock,
) : MviViewModel<AddTransactionUiState, AddTransactionIntent, AddTransactionEffect>(
    // Tarih varsayilani sabit YAZILAMAZ: kayit artik diske gidiyor, yanlis tarih
    // kalici olur ve duzeltme ekrani yok.
    AddTransactionUiState(date = clock.today()),
) {

    private var board: PriceBoard? = null
    private var positions: List<Position> = emptyList()
    private var members: List<Member> = emptyList()

    init {
        observePortfolio()
        observePrices()
    }

    override fun onIntent(intent: AddTransactionIntent) {
        val s = _state.value
        when (intent) {
            is AddTransactionIntent.SelectAssetClass -> update(
                s.copy(
                    assetClass = intent.assetClass,
                    // Sinif degisince onceki sinifin secimi anlamsizlasir.
                    selectedFundKey = if (intent.assetClass == AssetClass.Fund) {
                        s.selectedFundKey
                    } else {
                        null
                    },
                )
            )

            is AddTransactionIntent.SelectSubtype -> update(s.copy(selectedSubtype = intent.subtype))

            is AddTransactionIntent.SelectKarat -> update(s.copy(karat = intent.karat))

            is AddTransactionIntent.SelectCurrency -> update(s.copy(currency = intent.currency))

            is AddTransactionIntent.ChangeGram -> update(s.copy(gramText = intent.text))

            is AddTransactionIntent.ChangeFundQuery -> update(s.copy(fundQuery = intent.text))

            is AddTransactionIntent.SelectFund -> update(s.copy(selectedFundKey = intent.assetKey))

            AddTransactionIntent.Continue -> if (s.canContinue) update(s.toAmountStep())

            AddTransactionIntent.Back -> update(s.copy(step = AddTransactionStep.Asset))

            AddTransactionIntent.RepeatLast -> s.lastAdded?.let { last ->
                update(
                    s.copy(
                        assetClass = last.assetClass,
                        selectedSubtype = last.subtype,
                        karat = last.karat,
                        selectedFundKey = null,
                    ).toAmountStep(quantityText = last.quantityText)
                )
            }

            // Fiyat tarafa bagli (alirken satis fiyati, satarken alis fiyati),
            // bu yuzden dogrudan durum yazmak yetmez - fiyatlar yeniden uygulanir.
            is AddTransactionIntent.SelectSide -> update(s.copy(side = intent.side))

            is AddTransactionIntent.ChangeQuantity ->
                _state.value = s.copy(quantityText = intent.text)

            AddTransactionIntent.IncrementQuantity ->
                _state.value = s.copy(quantityText = stepQuantity(s, up = true))

            AddTransactionIntent.DecrementQuantity ->
                _state.value = s.copy(quantityText = stepQuantity(s, up = false))

            is AddTransactionIntent.ChangeUnitPrice -> _state.value = s.copy(
                unitPriceText = intent.text,
                // Elle yazilan fiyat otomatik yenilemede EZILMEZ.
                priceManual = true,
            )

            AddTransactionIntent.ResetPriceToMarket -> _state.value = s.copy(
                priceManual = false,
                unitPriceText = Money.number(s.marketPrice, s.priceDecimals),
            )

            AddTransactionIntent.ToggleExtra ->
                _state.value = s.copy(extraExpanded = !s.extraExpanded)

            is AddTransactionIntent.ChangeFee -> _state.value = s.copy(feeText = intent.text)

            is AddTransactionIntent.ChangeNote -> _state.value = s.copy(note = intent.text)

            is AddTransactionIntent.ChangeStorage -> _state.value = s.copy(storage = intent.text)

            AddTransactionIntent.Save -> save()

            is AddTransactionIntent.EditTransaction -> loadForEdit(intent.transactionId)

            is AddTransactionIntent.StartNew -> update(
                // Tasinanlar yalniz sheet'e ait olmayanlar: kisayol ve es adi
                // portfoyden gelir, formun onceki icerigiyle ilgisi yok.
                AddTransactionUiState(
                    date = clock.today(),
                    side = intent.side,
                    lastAdded = s.lastAdded,
                    partnerName = s.partnerName,
                )
            )
        }
    }

    // --- Gozlemler ---------------------------------------------------------

    private fun observePortfolio() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePositions(),
                portfolioRepository.observeMembers(),
                portfolioRepository.observeActivity(),
            ) { positionList, memberList, activity ->
                positions = positionList
                members = memberList
                _state.value.copy(
                    lastAdded = lastAddedOf(activity, positionList),
                    partnerName = memberList.firstOrNull { it.role != MemberRole.Owner }?.name
                        ?: memberList.getOrNull(1)?.name.orEmpty(),
                )
            }.collect { _state.value = withPrices(it) }
        }
    }

    private fun observePrices() {
        viewModelScope.launch {
            priceRepository.observePrices().collect { latest ->
                board = latest
                _state.value = withPrices(_state.value)
            }
        }
    }

    /** Secimi degistiren her intent fiyat tablosunu yeniden uygular. */
    private fun update(next: AddTransactionUiState) {
        _state.value = withPrices(next)
    }

    /**
     * Fiyata bagli tum alanlari tek yerden doldurur: alt tur satirlari, ayar
     * fiyatlari, fon sonuclari ve secime karsilik gelen birim fiyat.
     */
    private fun withPrices(s: AddTransactionUiState): AddTransactionUiState {
        // "current" DEGIL: taban sinifin ayni adli durum ozelligini golgeler.
        val prices = board ?: return s
        val market = marketPriceOf(s, prices)
        return s.copy(
            subtypes = GoldSubtype.entries.map {
                SubtypeOption(it, subtypePriceText(it, prices))
            },
            karatOptions = Karat.entries.map {
                KaratOption(it, karatGramPrice(it, prices, s.side))
            },
            currencyOptions = Currency.entries.map { currency ->
                CurrencyOption(
                    currency = currency,
                    priceText = prices.byKey(currency.priceKey())
                        ?.let { Money.tl(it.buyPrice(), decimals = 2) }
                        .orEmpty(),
                )
            },
            fundResults = fundCatalog(prices).filter { it.matches(s.fundQuery) },
            marketPrice = market,
            unitPriceText = when {
                s.priceManual -> s.unitPriceText
                // Fiyat yoksa alan BOS kalir - "0" yazmak fiyatin sifir oldugunu
                // soyler ve kullanici ustune yazmak icin once silmek zorunda.
                market > 0.0 -> Money.number(market, s.priceDecimals)
                else -> ""
            },
            offline = prices.freshness == PriceFreshness.Offline,
        )
    }

    // --- Duzenleme ---------------------------------------------------------

    /**
     * Var olan kaydi forma tasir.
     *
     * Birim fiyat ELLE girilmis sayilir: kayit gecmise ait, bugunun piyasa
     * fiyatiyla degistirilirse kullanici yalniz miktari duzeltmek isterken alis
     * fiyatini da sessizce degistirmis olur.
     */
    private fun loadForEdit(transactionId: String) {
        viewModelScope.launch {
            val transaction = portfolioRepository.observeAllTransactions().first()
                .firstOrNull { it.id == transactionId } ?: return@launch
            val position = portfolioRepository.observePositions().first()
                .firstOrNull { it.id == transaction.positionId } ?: return@launch

            val s = _state.value
            update(
                s.copy(
                    step = AddTransactionStep.Amount,
                    editingTransactionId = transaction.id,
                    assetClass = position.assetClass,
                    selectedSubtype = position.subtype ?: s.selectedSubtype,
                    karat = position.karat ?: s.karat,
                    // Fon pozisyonunun kimligi "pos_<fon anahtari>" bicimindedir;
                    // secim bununla eslesmezse kayit yeni bir pozisyona yazilir.
                    selectedFundKey = position.id.removePrefix("pos_")
                        .takeIf { position.assetClass == AssetClass.Fund },
                    // Doviz de kimlikten cozulur; yoksa euro kaydi duzenlenirken
                    // dolar secili gelir ve kaydedince dolara tasinirdi.
                    currency = Currency.fromPriceKey(position.id.removePrefix("pos_"))
                        ?: s.currency,
                    side = transaction.side,
                    quantityText = Money.number(
                        transaction.quantity,
                        if (position.unit == QuantityUnit.Gram) 1 else 0,
                    ),
                    unitPriceText = Money.number(transaction.unitPrice, s.priceDecimals),
                    priceManual = true,
                    date = transaction.date,
                    isToday = false,
                    feeText = transaction.fee.takeIf { it > 0.0 }?.let { Money.number(it, 0) }
                        .orEmpty(),
                    note = transaction.note.orEmpty(),
                    storage = transaction.storage.orEmpty(),
                    // Not, iscilik veya saklama doluysa alan kapali kalmamali.
                    extraExpanded = transaction.fee > 0.0 ||
                        !transaction.note.isNullOrBlank() ||
                        !transaction.storage.isNullOrBlank(),
                )
            )
        }
    }

    // --- Kaydetme ----------------------------------------------------------

    private fun save() {
        val s = _state.value
        if (!s.canSave || s.saving) return
        _state.value = s.copy(saving = true)

        viewModelScope.launch {
            // Yazma artik DISKE gidiyor: dolu disk, kilitli veritabani, yabanci
            // anahtar ihlali gercek atma yollari. Yakalanmazsa `saving` takili
            // kalir (Kaydet dugmesi kalici olarak pasif), sheet kapanmaz ve
            // Android'de uygulama duser - girilen islem sessizce kaybolur.
            try {
                writeTransaction(s)
                _state.value = _state.value.copy(saving = false)
                emitEffect(AddTransactionEffect.Saved)
            } catch (error: Exception) {
                _state.value = _state.value.copy(saving = false)
                emitEffect(
                    AddTransactionEffect.SaveFailed(error.message ?: "Kayıt yazılamadı."),
                )
            }
        }
    }

    /**
     * Duzenleme once YAZAR, sonra siler.
     *
     * Ters sirada, tek islemi olan bir varlikta pozisyon bir an icin bosalir:
     * defter bosalinca pozisyon dusuyor ve Varlik Detayi kendini listeye atiyor.
     * Kullanici kaydini duzeltirken ekrandan atilmis olurdu.
     *
     * Silme yerine ustune yazmak da yetmez: kullanici varlik turunu de
     * degistirebilir (yanlislikla Ceyrek yerine Yarim girmis olabilir) ve kayit
     * o zaman baska bir pozisyona tasinmalidir.
     */
    private suspend fun writeTransaction(s: AddTransactionUiState) {
        val replaced = s.editingTransactionId
        run {
            val position = positions.firstOrNull { it.matches(s) }
            val positionId = position?.id ?: newPositionId(s)

            // Varlik portfoyde yoksa once TANITILIR. Islem tek basina varligin
            // adini, sinifini ve birimini tasimaz; pozisyon olmadan kayit oksuz
            // kalir ve hicbir ekranda gorunmez.
            if (position == null) {
                portfolioRepository.upsertPosition(
                    Position(
                        id = positionId,
                        name = newPositionName(s),
                        assetClass = s.assetClass,
                        subtype = s.selectedSubtype.takeIf { s.assetClass == AssetClass.Gold },
                        karat = s.karat.takeIf {
                            s.assetClass == AssetClass.Gold &&
                                s.selectedSubtype == GoldSubtype.Jewelry
                        },
                        // Miktar ve maliyet defterden turer; burada yalniz meta bilgi.
                        quantity = 0.0,
                        unit = s.quantityUnit,
                        unitPrice = s.unitPrice,
                        value = 0.0,
                        cost = 0.0,
                        manualPrice = s.priceManual,
                    )
                )
            }

            portfolioRepository.addTransaction(
                Transaction(
                    // UUID. Kimlik once icerikten turetiliyordu ve ayni gun ayni
                    // miktarda ikinci alim ayniyi uretiyordu; depo bunu yerelde
                    // "_2" ekleyerek cozuyordu. Iki cihazda o cozum bozulur:
                    // her cihaz kendi numaralandirmasini yapar.
                    id = newId(),
                    positionId = positionId,
                    date = s.date,
                    side = s.side,
                    quantity = s.quantity,
                    unitPrice = s.unitPrice,
                    fee = s.fee,
                    note = s.note.takeIf { it.isNotBlank() },
                    storage = s.storage.takeIf { it.isNotBlank() },
                    addedByMemberId = members.firstOrNull { it.role == MemberRole.Owner }?.id
                        ?: members.firstOrNull()?.id.orEmpty(),
                    // Cevrimdisi kayit cihazda bekler; baglaninca esitlenir.
                    syncState = if (s.offline) SyncState.Pending else SyncState.Synced,
                )
            )
        }

        if (replaced != null) portfolioRepository.deleteTransaction(replaced)
    }
}

// --- Adim gecisi -------------------------------------------------------------

/**
 * Ikinci adima gecerken miktar alani bos birakilmaz: gramla olculen varlikta
 * ilk adimda girilen gram tasinir, adetle olculende 1 ile baslanir.
 */
private fun AddTransactionUiState.toAmountStep(
    quantityText: String? = null,
): AddTransactionUiState = copy(
    step = AddTransactionStep.Amount,
    quantityText = quantityText
        ?: when {
            this.quantityText.isNotBlank() -> this.quantityText
            quantityUnit == QuantityUnit.Gram && gramText.isNotBlank() -> gramText
            else -> "1"
        },
)

/** -/+ butonlari: gramda 0,1 kademe, digerlerinde 1 adet. Taban 0. */
private fun stepQuantity(s: AddTransactionUiState, up: Boolean): String {
    val stepSize = if (s.quantityUnit == QuantityUnit.Gram) 0.1 else 1.0
    val decimals = if (s.quantityUnit == QuantityUnit.Gram) 1 else 0
    val next = (s.quantity + if (up) stepSize else -stepSize).coerceAtLeast(0.0)
    return Money.number(next, decimals)
}

// --- Fiyat eslemesi ----------------------------------------------------------

/**
 * Islemin tarafina gore fiyat.
 *
 * Alirken kuyumcunun SATIS fiyatini oderiz, satarken ALIS fiyatina satariz.
 * Onceden her iki durumda da satis fiyati oneriliyordu: satis kaydeden kullanici
 * makas kadar fazla hasilat gormus oluyordu.
 *
 * Has/kulce ise istisna: kulcede iscilik yok, iki yonde de alis kotasyonu esas.
 */
private fun Price.forSide(side: TradeSide, bullion: Boolean = false): Double =
    if (side == TradeSide.Sell || bullion) sellPrice() else buyPrice()

private fun subtypePrice(subtype: GoldSubtype, board: PriceBoard, side: TradeSide): Double? {
    val price = subtype.priceKey()?.let { board.byKey(it) } ?: return null
    return price.forSide(side, bullion = subtype == GoldSubtype.Bullion)
}

/** Alt tur listesindeki fiyat etiketi hep ALIS tarafini gosterir - odenecek tutar. */
private fun subtypePriceText(subtype: GoldSubtype, board: PriceBoard): String =
    subtypePrice(subtype, board, TradeSide.Buy)?.let { Money.tl(it) } ?: "ayar seçin"

private fun karatGramPrice(karat: Karat, board: PriceBoard, side: TradeSide): Double =
    board.byKey(karat.priceKey())?.forSide(side) ?: 0.0

/** Secime karsilik gelen guncel birim fiyat. */
private fun marketPriceOf(s: AddTransactionUiState, board: PriceBoard): Double =
    when (s.assetClass) {
        AssetClass.Gold -> if (s.selectedSubtype == GoldSubtype.Jewelry) {
            karatGramPrice(s.karat, board, s.side)
        } else {
            subtypePrice(s.selectedSubtype, board, s.side) ?: 0.0
        }

        AssetClass.Silver -> board.byKey("silver_gram")?.forSide(s.side) ?: 0.0
        AssetClass.Fx -> board.byKey(s.currency.priceKey())?.forSide(s.side) ?: 0.0
        AssetClass.Fund -> s.selectedFundKey?.let { key ->
            s.fundResults.firstOrNull { it.assetKey == key }?.price
        } ?: 0.0
        // Nakitte birim fiyat yoktur; girilen tutar dogrudan degerdir.
        AssetClass.Cash -> 1.0
    }

// --- Fon kataloğu ------------------------------------------------------------

/**
 * Aranabilir fon listesi. Kod/ad/kurucu bilgisi urun katalogundan gelir; fiyat
 * ve gunluk degisim fiyat tablosunda varsa oradan EZILIR - iki ekran arasinda
 * kotasyon farki olmasin diye.
 */
private data class FundEntry(
    val assetKey: String,
    val code: String,
    val name: String,
    val issuer: String,
    val price: Double,
    val changePercent: Double,
)

private val FundCatalog: List<FundEntry> = listOf(
    FundEntry("fund_tte", "TTE", "Türkiye Teknoloji Değişim Fonu", "İş Portföy", 21.40, -2.40),
    FundEntry("fund_tkf", "TKF", "Teknoloji Katılım Fonu", "Ziraat Portföy", 13.86, 0.74),
    FundEntry("fund_ytd", "YTD", "Yeni Teknolojiler Değişken Fon", "Yapı Kredi Portföy", 9.72, 0.31),
    FundEntry("fund_afa", "AFA", "Ak Portföy Altın Fonu", "Ak Portföy", 24.60, 0.52),
    FundEntry("fund_ipv", "IPV", "İş Portföy Değişken Fon", "İş Portföy", 24.41, 0.18),
)

private fun fundCatalog(board: PriceBoard): List<FundResult> = FundCatalog.map { entry ->
    val live = board.byKey(entry.assetKey)
    FundResult(
        assetKey = entry.assetKey,
        code = entry.code,
        name = entry.name,
        issuer = entry.issuer,
        price = live?.ask ?: entry.price,
        changePercent = live?.changePercent ?: entry.changePercent,
    )
}

// --- Son eklenen -------------------------------------------------------------

/**
 * "Son eklediğin" kisayolu aktivite akisindan turetilir: en yeni ekleme olayinin
 * metni ("2 Çeyrek Altın ekledi") portfoydeki bir pozisyonla eslesirse kisayol
 * gosterilir. Eslesmezse satir hic cizilmez - calismayan bir dugme gostermektense.
 */
private fun lastAddedOf(activity: List<ActivityEvent>, positions: List<Position>): LastAdded? {
    val event = activity.firstOrNull { it.kind == ActivityKind.AddTransaction } ?: return null
    val label = event.description.removeSuffix(" ekledi")
    val position = positions.firstOrNull { label.endsWith(it.name) } ?: return null
    return LastAdded(
        label = label,
        assetClass = position.assetClass,
        subtype = position.subtype ?: GoldSubtype.Quarter,
        karat = position.karat ?: Karat.K22,
        quantityText = label.removeSuffix(position.name).trim().ifBlank { "1" },
    )
}

// --- Kayit anahtarlari -------------------------------------------------------

/** Ayni varlik zaten portfoydeyse islem o pozisyona yazilir. */
private fun Position.matches(s: AddTransactionUiState): Boolean = when (s.assetClass) {
    AssetClass.Gold -> assetClass == AssetClass.Gold &&
        subtype == s.selectedSubtype &&
        (s.selectedSubtype != GoldSubtype.Jewelry || karat == s.karat)

    AssetClass.Fund -> assetClass == AssetClass.Fund &&
        s.selectedFund?.code?.let { name.startsWith(it) } == true

    // Para birimi de eslesmeli. Once yalniz sinifa bakiliyordu: euro girilirse
    // kayit dolar pozisyonunun defterine yaziliyor, iki para birimi tek satirda
    // toplaniyordu.
    AssetClass.Fx -> assetClass == AssetClass.Fx && id == "pos_" + s.currency.priceKey()

    else -> assetClass == s.assetClass
}

private fun assetKeyOf(s: AddTransactionUiState): String = when (s.assetClass) {
    AssetClass.Gold -> if (s.selectedSubtype == GoldSubtype.Jewelry) {
        "gold_jewelry_${s.karat.milyem}"
    } else {
        s.selectedSubtype.priceKey().orEmpty()
    }

    AssetClass.Fund -> s.selectedFundKey.orEmpty()
    AssetClass.Silver -> "silver_gram"
    AssetClass.Fx -> s.currency.priceKey()
    AssetClass.Cash -> "cash"
}

private fun newPositionId(s: AddTransactionUiState): String = "pos_" + assetKeyOf(s)

/** Yeni pozisyonun gorunen adi. */
private fun newPositionName(s: AddTransactionUiState): String = when (s.assetClass) {
    AssetClass.Gold -> if (s.selectedSubtype == GoldSubtype.Jewelry) {
        "${s.karat.label()} Takı"
    } else {
        s.selectedSubtype.label()
    }

    AssetClass.Fund -> s.selectedFund?.let { "${it.code} · ${it.name}" } ?: "Fon"
    AssetClass.Silver -> "Gram Gümüş"
    AssetClass.Fx -> s.currency.label()
    AssetClass.Cash -> "Nakit"
}


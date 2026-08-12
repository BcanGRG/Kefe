package com.kefe.app.ui.screens.transaction

import androidx.lifecycle.viewModelScope
import com.kefe.app.data.remote.StockApi
import com.kefe.app.data.remote.TefasApi
import com.kefe.app.data.remote.currencyConversion
import com.kefe.app.data.remote.stockAssetKey
import com.kefe.app.data.remote.stockSymbolOf
import com.kefe.app.data.sync.CloudState
import com.kefe.app.data.sync.SyncCoordinator
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.ActivityKind
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Currency
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.Karat
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.PositionIdPrefix
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.SyncState
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.buyPrice
import com.kefe.app.domain.model.newId
import com.kefe.app.domain.model.goalAssignmentChange
import com.kefe.app.domain.model.priceKey
import com.kefe.app.domain.model.sellPrice
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.rawAmount
import com.kefe.app.ui.mvi.MviViewModel
import kotlinx.coroutines.Job
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
    private val preferences: PreferencesRepository,
    private val clock: KefeClock,
    // Fon adiminda girilen kodu TEFAS'tan canli cekmek icin - yereldeki 5 fonun
    // disindaki fonlar da eklenebilsin.
    private val tefas: TefasApi,
    // Hisse adiminda sembol/ad aramasi ve secilen sembolun kotasyonu icin.
    private val stocks: StockApi,
    // Kaydin "Bekliyor" damgasi BULUT durumundan gelir, fiyat tazeliginden degil.
    private val syncCoordinator: SyncCoordinator,
) : MviViewModel<AddTransactionUiState, AddTransactionIntent, AddTransactionEffect>(
    // Tarih varsayilani sabit YAZILAMAZ: kayit artik diske gidiyor, yanlis tarih
    // kalici olur ve duzeltme ekrani yok.
    AddTransactionUiState(date = clock.today()),
) {

    private var board: PriceBoard? = null
    private var positions: List<Position> = emptyList()
    private var members: List<Member> = emptyList()

    // Varlik -> hedef atamasi. Hedef secici bir pozisyonun MEVCUT hedefini
    // onceden secsin diye tutulur.
    private var assignments: Map<String, GoalAssignment> = emptyMap()

    // Bu oturumda TEFAS'tan canli cekilen fonlar. fundResults her withPrices'ta
    // yeniden kuruldugu icin ayri saklanir; katalog ile birlestirilir.
    private var liveFunds: List<FundResult> = emptyList()

    // Ayni gerekce hisse icin: bu oturumda borsadan aranan/cekilen semboller.
    private var liveStocks: List<StockResult> = emptyList()

    // Yazdikca arama: her tusa basista bir istek atmamak icin onceki arama
    // iptal edilir. Fonda gerekmiyordu - orada arama elle tetikleniyor.
    private var stockSearchJob: Job? = null

    /**
     * Bu cihazin profili. Eklenen islem BUNA yazilir - once kosulsuz Owner'a
     * yaziliyordu, yani es kendi telefonundan girse bile kayit "Ben" gorunuyordu.
     * Bos ise (kurulum yarim) Owner'a duseriz.
     */
    private var activeMemberId: String = ""

    init {
        observePortfolio()
        observePrices()
        observeAssignments()
        observeCloud()
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
                    selectedStockKey = if (intent.assetClass == AssetClass.Stock) {
                        s.selectedStockKey
                    } else {
                        null
                    },
                )
            )

            // Form degisince ayar da o formun varsayilanina doner: bilezikten
            // grama gecen biri 22 ayar secili kalirsa, ayar panelini fark
            // etmediginde 24 ayar gramini 22 ayar fiyatiyla kaydeder.
            is AddTransactionIntent.SelectSubtype -> update(
                s.copy(
                    selectedSubtype = intent.subtype,
                    karat = if (intent.subtype.usesKarat()) {
                        intent.subtype.defaultKarat()
                    } else {
                        s.karat
                    },
                )
            )

            // Ayni hedefe tekrar dokunmak secimi KALDIRIR (hedefsiz'e doner).
            is AddTransactionIntent.SelectGoal -> update(
                s.copy(selectedGoalId = intent.goalId.takeIf { it != s.selectedGoalId }),
            )

            is AddTransactionIntent.SelectKarat -> update(s.copy(karat = intent.karat))

            is AddTransactionIntent.SelectCurrency -> update(s.copy(currency = intent.currency))

            is AddTransactionIntent.ChangeGram -> update(s.copy(gramText = intent.text))

            // Yazmaya baslayinca onceki "bulunamadi" uyarisi silinir.
            is AddTransactionIntent.ChangeFundQuery ->
                update(s.copy(fundQuery = intent.text, fundSearchError = null))

            is AddTransactionIntent.SelectFund -> update(s.copy(selectedFundKey = intent.assetKey))

            AddTransactionIntent.SearchFundOnline -> searchFundOnline()

            is AddTransactionIntent.ChangeStockQuery ->
                update(s.copy(stockQuery = intent.text, stockSearchError = null))

            is AddTransactionIntent.SelectStock -> {
                // Onceki secimin cevrilemez-para-birimi uyarisi silinir.
                update(s.copy(selectedStockKey = intent.assetKey, stockSearchError = null))
                // Arama fiyat getirmiyor; secilen sembolunkini simdi cek.
                fetchStockQuote(intent.assetKey)
            }

            AddTransactionIntent.SearchStockOnline -> searchStockOnline()

            // 2. adima gecerken secici, varlik zaten portfoydeyse onun MEVCUT
            // hedefini gosterir (yeni varlikta Hedefsiz).
            AddTransactionIntent.Continue ->
                if (s.canContinue) update(s.toAmountStep().withCurrentGoal())

            AddTransactionIntent.Back -> update(s.copy(step = AddTransactionStep.Asset))

            AddTransactionIntent.RepeatLast -> s.lastAdded?.let { last ->
                update(
                    s.copy(
                        assetClass = last.assetClass,
                        selectedSubtype = last.subtype,
                        karat = last.karat,
                        selectedFundKey = null,
                        selectedStockKey = null,
                    ).toAmountStep(quantityText = last.quantityText).withCurrentGoal()
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
                unitPriceText = rawAmount(s.marketPrice),
            )

            AddTransactionIntent.ToggleExtra ->
                _state.value = s.copy(extraExpanded = !s.extraExpanded)

            is AddTransactionIntent.ChangeFee -> _state.value = s.copy(feeText = intent.text)

            is AddTransactionIntent.ChangeNote -> _state.value = s.copy(note = intent.text)

            is AddTransactionIntent.ChangeStorage -> _state.value = s.copy(storage = intent.text)

            AddTransactionIntent.Save -> save()

            is AddTransactionIntent.EditTransaction -> loadForEdit(intent.transactionId)

            is AddTransactionIntent.StartNew -> {
                // Tasinanlar yalniz sheet'e ait olmayanlar: kisayol ve es adi
                // portfoyden gelir, formun onceki icerigiyle ilgisi yok.
                val fresh = AddTransactionUiState(
                    date = clock.today(),
                    side = intent.side,
                    lastAdded = s.lastAdded,
                    partnerName = s.partnerName,
                )
                val position = intent.positionId?.let { id ->
                    positions.firstOrNull { it.id == id }
                }
                update(if (position == null) fresh else fresh.forPosition(position))
            }
        }
    }

    // --- Gozlemler ---------------------------------------------------------

    private fun observePortfolio() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePositions(),
                portfolioRepository.observeMembers(),
                portfolioRepository.observeActivity(),
                preferences.observeAll(),
                portfolioRepository.observeGoals(),
            ) { positionList, memberList, activity, prefs, goalList ->
                positions = positionList
                members = memberList
                activeMemberId = prefs[PreferenceKeys.ActiveMemberId]
                    ?: memberList.firstOrNull()?.id.orEmpty()
                _state.value.copy(
                    lastAdded = lastAddedOf(activity, positionList),
                    // Hedef seciciye dokulen hedefler; hic yoksa secici cizilmez ve
                    // varlik dogrudan toplam birikime eklenir.
                    availableGoals = goalList,
                    // Cevrimdisi notundaki isim AKTIF OLMAYAN profildir: "baglaninca
                    // ESIN telefonunda gorunur" derken kastedilen o. Once hep
                    // Owner-disi aliniyordu; es kendi telefonundan girince kendi
                    // adini "esiniz" gibi gormesi tuhaf oluyordu.
                    partnerName = memberList.firstOrNull { it.id != activeMemberId }?.name.orEmpty(),
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

    private fun observeAssignments() {
        viewModelScope.launch {
            portfolioRepository.observeGoalAssets().collect { assignments = it }
        }
    }

    /**
     * Bulut durumu FIYAT tazeliginden ayri okunur. Once ikisi tek bayrakti:
     * ucretsiz fiyat ucu tokezleyince kayit, senkron calisirken bile "Bekliyor"
     * damgasiyla diske yaziliyordu (bkz. [CloudState]).
     */
    private fun observeCloud() {
        viewModelScope.launch {
            syncCoordinator.cloudState().collect { cloud ->
                _state.value = _state.value.copy(cloudState = cloud)
            }
        }
    }

    /**
     * Formu VERILEN VARLIK icin hazirlar ve dogrudan 2. adima gecer.
     *
     * Varlik detayindan "Alım/Satış Ekle" denince kullanici hangi varlikta
     * oldugunu zaten soylemis oluyor; onu bir kez daha secmek gereksiz bir adim.
     * Alanlar [loadForEdit] ile ayni yoldan cozulur - fon ve doviz kimligi
     * pozisyon kimliginden turer, yoksa kayit yeni bir pozisyona yazilir.
     */
    private fun AddTransactionUiState.forPosition(position: Position): AddTransactionUiState =
        copy(
            assetClass = position.assetClass,
            selectedSubtype = position.subtype ?: selectedSubtype,
            // Ayari bos eski kayitta formun varsayilanina duseriz - yoksa
            // "Gram Altın" duzenlenirken 22 ayar secili gelip kaydedince
            // varlik baska bir kotasyona tasinirdi.
            karat = position.karat ?: position.subtype?.defaultKarat() ?: karat,
            selectedFundKey = position.id.removePrefix("pos_")
                .takeIf { position.assetClass == AssetClass.Fund },
            selectedStockKey = position.id.removePrefix("pos_")
                .takeIf { position.assetClass == AssetClass.Stock },
            currency = Currency.fromPriceKey(position.id.removePrefix("pos_")) ?: currency,
        ).toAmountStep().withCurrentGoal()

    /**
     * Varlik bir pozisyonla eslesiyorsa hedef seciciyi o pozisyonun MEVCUT
     * hedefine ayarlar (yeni varlikta null = Hedefsiz). Boylece Ev'e atanmis bir
     * varliga daha ekleyen kullanici seciciyi "Ev" gorur; dokunmadan kaydederse
     * atama korunur, "Hedefsiz"e alirsa kaldirilir.
     */
    private fun AddTransactionUiState.withCurrentGoal(): AddTransactionUiState {
        val matched = positions.firstOrNull { it.matches(this) }
        return copy(selectedGoalId = matched?.let { assignments[it.id]?.goalId })
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
            fundResults = mergedFunds(prices).filter { it.matches(s.fundQuery) },
            // Hissede sorgu SUZMEZ: liste zaten borsadan sorguya karsilik geldi.
            // Yerelde suzmek "aselsan" yazildiginda ASELS satirini eleyip listeyi
            // bosaltirdi - ad ile sembol tutmuyor.
            stockResults = mergedStocks(prices),
            marketPrice = market,
            unitPriceText = when {
                s.priceManual -> s.unitPriceText
                // Fiyat yoksa alan BOS kalir - "0" yazmak fiyatin sifir oldugunu
                // soyler ve kullanici ustune yazmak icin once silmek zorunda.
                market > 0.0 -> rawAmount(market)
                else -> ""
            },
            offline = prices.freshness == PriceFreshness.Offline,
        )
    }

    /**
     * Fon oneri listesi: kullanicinin TUTTUGU fonlar (degere gore ilk 5) + bu
     * oturumda canli cekilenler. Hic fon yoksa BOS - once elle yazilmis 5 fon
     * gosteriliyordu, artik yalniz kullanicinin kendi fonlari onerilir.
     */
    private fun mergedFunds(prices: PriceBoard): List<FundResult> {
        val held = heldFundResults(positions, prices)
        val live = liveFunds.map { entry ->
            val onBoard = prices.byKey(entry.assetKey)
            entry.copy(
                price = onBoard?.ask ?: entry.price,
                changePercent = onBoard?.changePercent ?: entry.changePercent,
            )
        }
        // Tutulan fon hem oneride hem canlida olabilir; tutulan kazanir (once gelir).
        return (held + live).distinctBy { it.assetKey }
    }

    /**
     * Yereldeki 5 fonda olmayan bir kodu TEFAS'tan canli ceker. Bulunca sonucu
     * listeye katip secer; bulamayinca alanin altina uyari yazar. TEFAS ucu
     * ad-arama yapmaz, kod dogrudan sorgulanir.
     */
    private fun searchFundOnline() {
        val code = _state.value.fundSearchCode ?: return
        if (_state.value.fundSearching) return
        _state.value = _state.value.copy(fundSearching = true, fundSearchError = null)
        viewModelScope.launch {
            val quote = runCatching { tefas.fetchFund(code) }.getOrNull()
            if (quote == null || quote.price <= 0.0) {
                _state.value = _state.value.copy(
                    fundSearching = false,
                    fundSearchError = "'$code' bulunamadı. Kodu kontrol edin.",
                )
                return@launch
            }
            val result = FundResult(
                assetKey = "fund_${quote.code.lowercase()}",
                code = quote.code,
                name = quote.name.ifBlank { quote.code },
                issuer = "TEFAS",
                price = quote.price,
                changePercent = quote.changePercent,
            )
            // Ayni fon tekrar aranirsa cogaltma; en son cekileni tut.
            liveFunds = liveFunds.filter { it.assetKey != result.assetKey } + result
            // Bulunani listeye kat ve dogrudan sec - kullanici Devam'a gecebilsin.
            _state.value = withPrices(
                _state.value.copy(
                    fundSearching = false,
                    fundSearchError = null,
                    selectedFundKey = result.assetKey,
                ),
            )
        }
    }

    /**
     * Hisse oneri listesi: kullanicinin TUTTUGU hisseler + bu oturumda aranip
     * bulunanlar. Fiyat once fiyat tablosundan (TL'ye cevrilmis, canli), yoksa
     * aramada cekilen degerden gelir.
     */
    private fun mergedStocks(prices: PriceBoard): List<StockResult> {
        val held = heldStockResults(positions, prices)
        val live = liveStocks.map { entry ->
            val onBoard = prices.byKey(entry.assetKey)
            entry.copy(
                price = onBoard?.ask ?: entry.price,
                changePercent = onBoard?.changePercent ?: entry.changePercent,
            )
        }
        // Aranan sembol zaten portfoydeyse tutulan kazanir (once gelir).
        return (held + live).distinctBy { it.assetKey }
    }

    /**
     * Borsada ada ya da sembole gore arar.
     *
     * FONDAN FARKLI iki nokta var. Birincisi: TEFAS ucu ad-arama yapmaz, o
     * yuzden fonda kod dogrudan sorgulanir ve arama elle tetiklenir; borsa ucu
     * "aselsan" yazan biri icin ASELS.IS'i bulur, dolayisiyla yazdikca aranir ve
     * onceki istek iptal edilir.
     *
     * Ikincisi: arama FIYAT DONDURMEZ. Her sonuc icin ayri kotasyon cekmek 12
     * istek demekti; fiyat kullanici sec[me]digi sembol icin cekilir
     * ([fetchStockQuote]).
     */
    private fun searchStockOnline() {
        val query = _state.value.stockQuery.trim()
        stockSearchJob?.cancel()
        if (query.length < 2) {
            _state.value = _state.value.copy(stockSearching = false, stockSearchError = null)
            return
        }
        _state.value = _state.value.copy(stockSearching = true, stockSearchError = null)
        stockSearchJob = viewModelScope.launch {
            val results = runCatching { stocks.search(query) }.getOrNull()
            if (results.isNullOrEmpty()) {
                _state.value = _state.value.copy(
                    stockSearching = false,
                    stockSearchError = "'$query' için sonuç yok. Sembolü veya adı kontrol edin.",
                )
                return@launch
            }
            val mapped = results.map { hit ->
                StockResult(
                    assetKey = stockAssetKey(hit.symbol),
                    symbol = hit.symbol,
                    name = hit.name,
                    exchange = hit.exchange,
                    // Fiyat secimde cekilir; 0 = "henuz bilmiyoruz".
                    price = 0.0,
                    changePercent = 0.0,
                )
            }
            // Tutulan hisselerin fiyati zaten var; onlari ezmesin diye
            // sonuclarin ONUNE degil arkasina konur (bkz. mergedStocks).
            liveStocks = mapped
            _state.value = withPrices(
                _state.value.copy(stockSearching = false, stockSearchError = null),
            )
        }
    }

    /**
     * Secilen sembolun guncel kotasyonu. Arama fiyat getirmedigi icin ikinci
     * adima gecmeden once bir kez cekilir - yoksa "Güncel piyasa" bos kalir ve
     * kullanici fiyati elle yazmak zorunda kalirdi.
     */
    private fun fetchStockQuote(assetKey: String) {
        val symbol = stockSymbolOf(assetKey)
        viewModelScope.launch {
            val quote = runCatching { stocks.fetch(symbol) }.getOrNull() ?: return@launch
            // Arama DUNYA capinda sonuc veriyor: "AAPL" yazan biri Buenos Aires
            // ve Sao Paulo kotasyonlarini da goruyor. Onlarin para birimi icin
            // kurumuz yok, dolayisiyla fiyat cekilemez - satirda "—" birakmak
            // "yukleniyor" gibi gorunurdu, sebebi YAZILIR.
            val rate = fxRateFor(quote.currencyCode) ?: run {
                _state.value = _state.value.copy(
                    stockSearchError = "${quote.currencyCode} ile işlem gören borsalar " +
                        "desteklenmiyor; TL, dolar, euro ve sterlin kotasyonları çevrilebiliyor.",
                )
                return@launch
            }
            liveStocks = liveStocks.map { entry ->
                if (entry.assetKey != assetKey) {
                    entry
                } else {
                    val conversion = currencyConversion(quote.currencyCode)
                    entry.copy(
                        name = entry.name.ifBlank { quote.name },
                        price = quote.price * rate,
                        changePercent = quote.changePercent,
                        // Peni degil sterlin yazilir; bolen fiyat katmaniyla
                        // AYNI yerden gelir, yoksa iki ekran ayrisirdi.
                        nativePrice = conversion?.code?.let { quote.price / conversion.divisor },
                        currencyCode = conversion?.code,
                    )
                }
            }
            _state.value = withPrices(_state.value)
        }
    }

    /**
     * Borsanin para biriminden TL'ye kur. Tarif [currencyConversion]'dan gelir -
     * gunluk yenilemeyle AYNI kaynak, yoksa ayni hisse iki ekranda iki fiyat
     * gosterirdi. Kur bilinmiyorsa null ve fiyat CEKILMEZ: 180 sayisini TL
     * sanmaktansa alan bos kalsin.
     */
    private fun fxRateFor(currencyCode: String): Double? {
        val conversion = currencyConversion(currencyCode) ?: return null
        val code = conversion.code ?: return 1.0
        val currency = Currency.entries.firstOrNull { it.code == code } ?: return null
        val rate = board?.byKey(currency.priceKey())?.buyPrice() ?: return null
        return (rate / conversion.divisor).takeIf { it > 0.0 }
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
                    // Ayni gun ici sira korunsun diye tasinir.
                    editingCreatedAt = transaction.createdAt,
                    assetClass = position.assetClass,
                    selectedSubtype = position.subtype ?: s.selectedSubtype,
                    // Ayari bos eski kayitta formun varsayilani (bkz. forPosition).
                    karat = position.karat ?: position.subtype?.defaultKarat() ?: s.karat,
                    // Fon pozisyonunun kimligi "pos_<fon anahtari>" bicimindedir;
                    // secim bununla eslesmezse kayit yeni bir pozisyona yazilir.
                    selectedFundKey = position.id.removePrefix("pos_")
                        .takeIf { position.assetClass == AssetClass.Fund },
                    selectedStockKey = position.id.removePrefix("pos_")
                        .takeIf { position.assetClass == AssetClass.Stock },
                    // Doviz de kimlikten cozulur; yoksa euro kaydi duzenlenirken
                    // dolar secili gelir ve kaydedince dolara tasinirdi.
                    currency = Currency.fromPriceKey(position.id.removePrefix("pos_"))
                        ?: s.currency,
                    side = transaction.side,
                    // Ham deger (ayrac cizimde eklenir); imlec duzenlenirken yerinde durur.
                    quantityText = rawAmount(transaction.quantity),
                    unitPriceText = rawAmount(transaction.unitPrice),
                    priceManual = true,
                    date = transaction.date,
                    isToday = false,
                    feeText = transaction.fee.takeIf { it > 0.0 }?.let { rawAmount(it) }
                        .orEmpty(),
                    note = transaction.note.orEmpty(),
                    storage = transaction.storage.orEmpty(),
                    // Secici pozisyonun MEVCUT hedefini gosterir; kullanici
                    // duzenlerken varligin hangi hedefte oldugunu gorur.
                    selectedGoalId = assignments[position.id]?.goalId,
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
     * Hedef atamasi da bu sirayla dogru sonuca variyor: yeni kayit katkisini
     * uygular, eski kaydin silinmesi kendi katkisini geri alir. Katkilar
     * toplanabilir deltalar oldugu icin sira onemli degil (bkz. 9.sqm). Once
     * silen bir surumde de dogru olurdu ama pozisyon bir an bosalirdi.
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

            // Atama ve miktar YAZMADAN ONCE okunur. "Tum varlik" atamasini bu
            // isleme kadarki miktara sabitlemek gerekiyor (bkz.
            // nextAssignedQuantity) ve o miktar ancak burada bellidir - kayit
            // yazildiktan sonra pozisyon zaten yeni adedi tasiyor.
            val assignmentBefore = portfolioRepository.goalAssignmentOf(positionId)
            val quantityBefore = position?.quantity ?: 0.0

            // Atama degisikligi KAYITTAN ONCE hesaplanir: katkisi kaydin
            // uzerine yazilacak ki silme ve duzenleme onu geri alabilsin.
            val goalChange = goalAssignmentChange(
                current = assignmentBefore,
                selectedGoalId = s.selectedGoalId,
                transactionQuantity = s.quantity,
                isSell = s.side == TradeSide.Sell,
                quantityBefore = quantityBefore,
            )

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
                            s.assetClass == AssetClass.Gold && s.selectedSubtype.usesKarat()
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
                    // Aktif profile yazilir. Bos ise (kurulum yarim kalmis)
                    // ilk uyeye duseriz - kayit kimliksiz kalmasin.
                    addedByMemberId = activeMemberId.ifBlank {
                        members.firstOrNull()?.id.orEmpty()
                    },
                    // Damga BULUT durumundan gelir. Once fiyat tazeliginden
                    // geliyordu: fiyat ucu tokezleyince kayit, esitleme gayet
                    // calisirken bile "Bekliyor" olarak DISKE yaziliyordu.
                    syncState = when (s.cloudState) {
                        CloudState.Synced -> SyncState.Synced
                        // Bulut kapaliyken de kayit "esitlenmemis"tir: giris
                        // yapilinca ilk push'la gidecek.
                        CloudState.Off, CloudState.Unreachable -> SyncState.Pending
                    },
                    // Duzenlemede eski kaydin damgasi DEVREDILIR: yeni satir
                    // ayni gunun sonuna dusmesin. Yeni kayitta 0 kalir ve depo
                    // simdiyi damgalar.
                    createdAt = s.editingCreatedAt,
                    // Atamaya katkisi kaydin kendi alani: silme ve duzenleme
                    // ancak bu rakamla geri alabilir (bkz. 9.sqm).
                    goalId = goalChange?.goalId,
                    goalDelta = goalChange?.delta ?: 0.0,
                )
            )

            if (goalChange != null) {
                portfolioRepository.assignPositionToGoal(
                    positionId = positionId,
                    goalId = goalChange.goalId,
                    quantity = goalChange.quantity,
                )
            }
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
    val next = (s.quantity + if (up) stepSize else -stepSize).coerceAtLeast(0.0)
    return rawAmount(next)
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
        AssetClass.Gold -> if (s.selectedSubtype.usesKarat()) {
            karatGramPrice(s.karat, board, s.side)
        } else {
            subtypePrice(s.selectedSubtype, board, s.side) ?: 0.0
        }

        AssetClass.Silver -> board.byKey("silver_gram")?.forSide(s.side) ?: 0.0
        AssetClass.Fx -> board.byKey(s.currency.priceKey())?.forSide(s.side) ?: 0.0
        AssetClass.Fund -> s.selectedFundKey?.let { key ->
            s.fundResults.firstOrNull { it.assetKey == key }?.price
        } ?: 0.0

        AssetClass.Stock -> s.selectedStockKey?.let { key ->
            s.stockResults.firstOrNull { it.assetKey == key }?.price
        } ?: 0.0
        // Nakitte birim fiyat yoktur; girilen tutar dogrudan degerdir.
        AssetClass.Cash -> 1.0
    }

// --- Fon onerileri -----------------------------------------------------------

/**
 * Oneri listesi kullanicinin fon POZISYONLARINDAN turer: en cok tuttugu (degere
 * gore) ilk 5 fon. Hic fon yoksa BOS doner - once elle yazilmis sabit 5 fon
 * gosteriliyordu. Fiyat/degisim once fiyat tablosundan (canli), yoksa pozisyonun
 * son bilinen degerinden gelir.
 */
private fun heldFundResults(positions: List<Position>, board: PriceBoard): List<FundResult> =
    positions
        .filter { it.assetClass == AssetClass.Fund && it.quantity > 0.0 }
        .sortedByDescending { it.value }
        .take(5)
        .map { pos ->
            val assetKey = pos.id.removePrefix("pos_")
            val onBoard = board.byKey(assetKey)
            FundResult(
                assetKey = assetKey,
                code = assetKey.removePrefix("fund_").uppercase(),
                // Pozisyon adi "KOD · Tam Ad"; oneride tam ad gosterilir.
                name = pos.name.substringAfter(" · ", pos.name),
                issuer = "",
                price = onBoard?.ask ?: pos.unitPrice,
                changePercent = onBoard?.changePercent ?: pos.dailyChangePercent ?: 0.0,
            )
        }

/** Ayni gerekce hisse icin: en cok tutulan ilk 5 sembol. */
private fun heldStockResults(positions: List<Position>, board: PriceBoard): List<StockResult> =
    positions
        .filter { it.assetClass == AssetClass.Stock && it.quantity > 0.0 }
        .sortedByDescending { it.value }
        .take(5)
        .map { pos ->
            val assetKey = pos.id.removePrefix("pos_")
            val onBoard = board.byKey(assetKey)
            StockResult(
                assetKey = assetKey,
                symbol = stockSymbolOf(assetKey),
                // Pozisyon adi "SEMBOL · Tam Ad"; oneride tam ad gosterilir.
                name = pos.name.substringAfter(" · ", pos.name),
                exchange = "",
                price = onBoard?.ask ?: pos.unitPrice,
                changePercent = onBoard?.changePercent ?: pos.dailyChangePercent ?: 0.0,
                nativePrice = onBoard?.nativePrice,
                currencyCode = onBoard?.nativeCurrency,
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
    // Ayar da eslesmeli: 22 ayar gram ile 24 ayar gram AYRI varliklardir,
    // fiyatlari da ayri kotasyondan gelir.
    //
    // Eski kayitlarda ayar kolonu BOS. `karat == s.karat` deseydik mevcut
    // "Gram Altın" pozisyonu (null) 24 ayar secimiyle eslesmez, her ekleme
    // ikinci bir pozisyon acardi. Bu yuzden ETKIN ayar kiyaslanir.
    AssetClass.Gold -> assetClass == AssetClass.Gold &&
        subtype == s.selectedSubtype &&
        (
            !s.selectedSubtype.usesKarat() ||
                (karat ?: s.selectedSubtype.defaultKarat()) == s.karat
            )

    AssetClass.Fund -> assetClass == AssetClass.Fund &&
        s.selectedFund?.code?.let { name.startsWith(it) } == true

    // Hissede ad degil ANAHTAR kiyaslanir: sembol kimlige gomulu ve borsanin
    // dondurdugu sirket adi zamanla degisebiliyor.
    AssetClass.Stock -> assetClass == AssetClass.Stock &&
        s.selectedStockKey?.let { id == PositionIdPrefix + it } == true

    // Para birimi de eslesmeli. Once yalniz sinifa bakiliyordu: euro girilirse
    // kayit dolar pozisyonunun defterine yaziliyor, iki para birimi tek satirda
    // toplaniyordu.
    AssetClass.Fx -> assetClass == AssetClass.Fx && id == "pos_" + s.currency.priceKey()

    else -> assetClass == s.assetClass
}

/**
 * Altin varliginin KIMLIK anahtari - [com.kefe.app.domain.model.priceKey] ile
 * ayni sey degildir. Fiyat anahtari "bu neyle fiyatlanir", kimlik anahtari "bu
 * hangi satir" sorusunu yanitlar; Bilezik ikisinin ayrildigi eski ornektir.
 *
 * KIMLIK ETKIN AYARI TASIMAK ZORUNDA. Once butun gram formlari `gold_gram`a
 * dusuyordu ve [Position.matches] ayari kiyasladigi icin 22 ayar gram secimi
 * mevcut 24 ayar pozisyonuna ESLESMIYOR, ama [newPositionId] tam da ONUN
 * kimligini uretiyordu. upsertPosition mevcut satirin adini, alt turunu ve
 * ayarini eziyor, islem ayni positionId'ye yaziliyordu: iki ayri varligin
 * defteri tek pozisyonda birlesiyor ve ESKI miktar da yeni ayarin kotasyonuyla
 * degerleniyordu. 100 gr 24 ayarin yanina 10 gr 22 ayar eklemek ekranda ~52 bin
 * TL'lik sahte bir kayip yaziyordu.
 *
 * [Karat.K24] zaten `gold_gram` donuyor - mevcut 24 ayar pozisyonlarinin
 * kimligi bu degisiklikle DEGISMEZ.
 */
internal fun goldAssetKey(subtype: GoldSubtype, karat: Karat): String = when {
    subtype == GoldSubtype.Jewelry -> "gold_jewelry_${karat.milyem}"
    subtype.usesKarat() -> karat.priceKey()
    // Has/Kulce gram altinla AYNI kotasyondan fiyatlanir ama ayri bir varliktir.
    // Fiyat anahtari `gold_gram` kalir, kimligi ayrilir - yoksa 24 ayar gramla
    // ayni satira duserdi.
    subtype == GoldSubtype.Bullion -> "gold_bullion"
    else -> subtype.priceKey().orEmpty()
}

private fun assetKeyOf(s: AddTransactionUiState): String = when (s.assetClass) {
    AssetClass.Gold -> goldAssetKey(s.selectedSubtype, s.karat)

    AssetClass.Fund -> s.selectedFundKey.orEmpty()
    AssetClass.Stock -> s.selectedStockKey.orEmpty()
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
    AssetClass.Stock -> s.selectedStock?.let { "${it.symbol} · ${it.name}" } ?: "Hisse"
    AssetClass.Silver -> "Gram Gümüş"
    AssetClass.Fx -> s.currency.label()
    AssetClass.Cash -> "Nakit"
}


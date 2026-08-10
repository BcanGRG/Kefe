package com.kefe.app.ui.screens.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PricePoint
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.annualizedReturnPercent
import com.kefe.app.domain.model.buyPrice
import com.kefe.app.domain.model.costBasis
import com.kefe.app.domain.model.formatShort
import com.kefe.app.domain.model.holdingDays
import com.kefe.app.domain.model.priceKey
import com.kefe.app.domain.model.sellPrice
import com.kefe.app.domain.model.spreadPercent
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.ui.format.Money
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Tek varlik detayi. MVI-lite: tek [AssetDetailUiState] akisi + [onIntent].
 *
 * Ortalama alis, kar/zarar ve getiri ISLEM DEFTERINDEN turetilir - pozisyonda
 * hazir duran `cost` alanindan degil. Ayni varligi farkli tarihlerde farkli
 * fiyatlardan almis biri icin tek bir "maliyet" sayisi ancak islemlerden
 * hesaplanabilir; bkz. [costBasis].
 */
class AssetDetailViewModel(
    private val repo: PortfolioRepository,
    private val priceRepository: PriceRepository,
    private val clock: KefeClock,
    private val positionId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AssetDetailUiState())
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<AssetDetailEffect>(capacity = 4)
    val effects: Flow<AssetDetailEffect> = _effects.receiveAsFlow()

    private var historyJob: Job? = null
    private var watchedAssetKey: String? = null

    /**
     * Varlik bir kez gorulduyse, sonradan kaybolmasi SILINDIGI anlamina gelir.
     * Bu ayrim onemli: ilk yuklemede pozisyon henuz gelmemis olabilir, o zaman
     * ekrandan cikilmamali.
     */
    private var everSeen = false

    init {
        viewModelScope.launch {
            combine(
                repo.observePositions(),
                repo.observeTransactions(positionId),
                repo.observeMembers(),
                priceRepository.observePrices(),
            ) { positions, transactions, members, board ->
                val position = positions.firstOrNull { it.id == positionId }
                buildState(position, transactions, members, board)
            }.collect { next ->
                _state.value = next
                if (next.position != null) {
                    everSeen = true
                } else if (everSeen) {
                    everSeen = false
                    _effects.trySend(AssetDetailEffect.PositionGone)
                }
            }
        }
    }

    /**
     * Fiyat gecmisi AYRI toplanir: hangi varligin gecmisi olduğu pozisyondan,
     * yani yukaridaki akistan cikiyor. Ikisini tek combine'a koymak, pozisyon
     * her degistiginde gecmis sorgusunu yeniden kurmayi gerektirirdi.
     */
    private fun observeHistory(assetKey: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            priceRepository.observePriceHistory(assetKey).collect { series ->
                // Tek nokta egri degildir: iki noktadan az veri varken grafik
                // hic cizilmez (ekran zaten size >= 2 kontrolu yapiyor).
                _state.value = _state.value.copy(
                    priceSeries = series.map { it.price },
                    // Etiket serinin GERCEK araligini yazar; sabit "12 ay"
                    // fonlarda dolu bir aylik egrinin ustunde yalan oluyordu.
                    periodLabel = periodLabelOf(series),
                )
            }
        }
    }

    fun onIntent(intent: AssetDetailIntent) {
        when (intent) {
            is AssetDetailIntent.DeleteTransaction -> viewModelScope.launch {
                repo.deleteTransaction(intent.transactionId)
            }

            AssetDetailIntent.OpenMenu -> _state.value = _state.value.copy(menuOpen = true)
            AssetDetailIntent.CloseMenu -> _state.value = _state.value.copy(menuOpen = false)

            // Menu kapanir, onay acilir: silme geri alinamaz, tek dokunusla
            // olmamali.
            AssetDetailIntent.RequestDelete ->
                _state.value = _state.value.copy(menuOpen = false, confirmDelete = true)

            AssetDetailIntent.DismissDeleteConfirm ->
                _state.value = _state.value.copy(confirmDelete = false)

            // Silinen varligin ekrani da kapanir; PositionGone kabugu geri atar.
            AssetDetailIntent.ConfirmDelete -> viewModelScope.launch {
                _state.value = _state.value.copy(confirmDelete = false)
                repo.deletePosition(positionId)
            }
        }
    }

    private fun buildState(
        position: Position?,
        transactions: List<Transaction>,
        members: List<Member>,
        board: PriceBoard,
    ): AssetDetailUiState {
        if (position == null) {
            return AssetDetailUiState(loading = false, members = members)
        }

        // Fiyat gecmisi bu varligin anahtarina baglidir; anahtar cozulunce
        // dinlenmeye baslanir, degisirse eski dinleme birakilir.
        val price = board.matchTo(position)
        val assetKey = price?.assetKey
        if (assetKey != null && assetKey != watchedAssetKey) {
            watchedAssetKey = assetKey
            observeHistory(assetKey)
        }

        // GELEN SIRA KRONOLOJIKTIR (bkz. PortfolioRepository.observeTransactions)
        // ve hesaplar onu oyle bekler. Gorunum icin ayri bir liste kurulur:
        // tasarimda islem gecmisi en son islemle baslar. Siralama KARARLI, yani
        // ayni gunun kayitlari kendi aralarinda kronolojik kalir.
        val ordered = transactions.sortedWith(
            compareByDescending<Transaction> { it.date.year }
                .thenByDescending { it.date.month }
                .thenByDescending { it.date.day },
        )
        val oldest = ordered.lastOrNull()

        // Defterden turetilen maliyet - KRONOLOJIK liste uzerinden.
        //
        // Burada bir zamanlar ekran sirasi kullaniliyordu ve ayni gun once alip
        // sonra satan kullanicida satis tumden atlanabiliyordu: detay ekrani ile
        // varlik listesi ayni defterden farkli rakam gosteriyordu. Artik depo tek
        // ve dogru sirali defteri veriyor.
        //
        // Islem yoksa pozisyondaki hazir degerlere duseriz - ornek veride her
        // pozisyonun gecmisi henuz yok.
        val basis = transactions.costBasis()
        val hasLedger = transactions.isNotEmpty()
        val today = clock.today()

        val averagePrice = when {
            hasLedger && basis.quantity > 0.0 -> basis.averageCost
            position.quantity == 0.0 -> 0.0
            else -> position.cost / position.quantity
        }

        return AssetDetailUiState(
            loading = false,
            position = position,
            transactions = ordered,
            members = members,
            // Grafik AYRI akistan gelir; burada yeniden yazmak onu sifirlardi.
            priceSeries = _state.value.priceSeries,
            // Gecici UI bayraklari da korunur: akis fiyat/senkron her tiktiginde
            // yeniden kuruluyor; korunmazsa acilan menu ya da silme onayi bir
            // sonraki emisyonda ANINDA kapaniyordu (dialog "acilip hemen kapaniyor").
            menuOpen = _state.value.menuOpen,
            confirmDelete = _state.value.confirmDelete,
            // Isaretciler ve eksen etiketleri gecmis serisine dayaniyordu;
            // gercek seri gunluk ve daha yeni oldugu icin ikisi de bu turda
            // bos birakildi - uydurma bir eksen cizmektense hic cizmemek dogru.
            tradeMarkers = emptyList(),
            axisLabels = emptyList(),
            averageBuyPrice = averagePrice,
            spreadAmount = price?.let { it.buyPrice() - it.sellPrice() } ?: 0.0,
            spreadPercent = price?.spreadPercent(),
            firstBuyDate = if (hasLedger) basis.firstBuy else oldest?.date,
            priceSourceLabel = priceSourceLabel(price, position),
            // Elle fiyatta YAZILMAZ: kullanici TL girmistir, yaninda kaynaktan
            // kalmis eski bir dolar rakami durursa iki sayi birbirini tutmaz.
            nativeUnitPrice = price
                ?.takeIf { !it.isManual && !position.manualPrice }
                ?.let { p ->
                    val value = p.nativePrice ?: return@let null
                    val code = p.nativeCurrency ?: return@let null
                    Money.foreign(value, code)
                },
            unrealizedProfit = if (hasLedger) {
                basis.unrealizedProfit(position.unitPrice)
            } else {
                position.profit
            },
            realizedProfit = basis.realizedProfit,
            holdingDays = holdingDays(basis.firstBuy ?: oldest?.date, today),
            annualizedReturnPercent = annualizedReturnPercent(
                transactions = transactions,
                currentValue = position.value,
                today = today,
            ),
        )
    }

    // --- Fiyat kaynagi -----------------------------------------------------

    /** "Serbest piyasa · 14:32" - GERCEK fiyat tablosundan. */
    private fun priceSourceLabel(price: Price?, position: Position): String {
        val source = when {
            position.manualPrice || price?.isManual == true -> PriceSource.Manual
            else -> price?.source ?: PriceSource.FreeMarket
        }
        val timestamp = price?.timestamp
        return if (timestamp.isNullOrBlank()) source.label() else "${source.label()} · $timestamp"
    }
}

/**
 * Pozisyonu fiyat tablosundaki satirla eslestirir.
 *
 * Once ornek veriye bakiyordu; gercek fiyatlar bagli olmasina ragmen detay
 * ekrani sabit bir tabloyu okuyordu ve kaynak etiketi cogu zaman bos kaliyordu.
 *
 * Eslesme ADA gore, cunku pozisyonun assetKey'i yok: pozisyon kullanicinin
 * verdigi adi tasir ("Anneannemin Bileziği"), fiyat satiri ise turun adini
 * ("22 Ayar"). Once tam ad, sonra baslangic eslesmesi denenir.
 */
/**
 * Pozisyonun fiyat satiri.
 *
 * Once yalniz ETIKET esleniyordu ("Çeyrek Altın" == "Çeyrek Altın"); ad
 * degistiginde ya da tasarim etiketi guncellendiginde sessizce kopan bir bagdi.
 * Artik once anahtar denenir - pozisyonun kendi alanlarindan turer - etiket
 * eslemesi yalniz eski/ornek satirlar icin geride durur.
 */
private fun PriceBoard.matchTo(position: Position): Price? =
    position.priceKey()?.let { byKey(it) }
        ?: prices.firstOrNull { it.label == position.name }
        ?: prices.firstOrNull { position.name.startsWith(it.label) }
        ?: prices.firstOrNull { it.label.startsWith(position.name) }

/**
 * Grafigin ustundeki aralik etiketi: "1 Tem – 31 Tem".
 *
 * Once sabit "12 ay" yaziyordu ve bu bir varsayimdi: grafik cihazda BIRIKMIS
 * gunleri ciziyor, on iki aylik bir seri hicbir zaman olmuyordu. Fonlarda
 * TEFAS'in aylik serisi gecmise yazilmaya baslayinca etiket acikca yanlis
 * oldu - dolu gorunen bir egrinin ustunde "12 ay" duruyordu.
 *
 * Iki noktadan az veride grafik zaten cizilmiyor; etiket de bos kalir.
 */
private fun periodLabelOf(series: List<PricePoint>): String {
    if (series.size < 2) return ""
    val first = series.first().date
    val last = series.last().date
    return "${first.formatShort()} – ${last.formatShort()}"
}


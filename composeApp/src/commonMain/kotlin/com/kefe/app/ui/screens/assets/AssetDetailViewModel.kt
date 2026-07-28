package com.kefe.app.ui.screens.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.annualizedReturnPercent
import com.kefe.app.domain.model.costBasis
import com.kefe.app.domain.model.holdingDays
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private var historyJob: Job? = null
    private var watchedAssetKey: String? = null

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
            }.collect { _state.value = it }
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
                _state.value = _state.value.copy(priceSeries = series)
            }
        }
    }

    fun onIntent(intent: AssetDetailIntent) {
        when (intent) {
            is AssetDetailIntent.DeleteTransaction -> viewModelScope.launch {
                repo.deleteTransaction(intent.transactionId)
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

        // Yeniden eskiye: tasarimda islem gecmisi en son islemle baslar.
        val ordered = transactions.sortedWith(
            compareByDescending<Transaction> { it.date.year }
                .thenByDescending { it.date.month }
                .thenByDescending { it.date.day },
        )
        val oldest = ordered.lastOrNull()

        // Defterden turetilen maliyet. Islem yoksa pozisyondaki hazir degerlere
        // duseriz - ornek veride her pozisyonun gecmisi henuz yok.
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
            // Isaretciler ve eksen etiketleri gecmis serisine dayaniyordu;
            // gercek seri gunluk ve daha yeni oldugu icin ikisi de bu turda
            // bos birakildi - uydurma bir eksen cizmektense hic cizmemek dogru.
            tradeMarkers = emptyList(),
            axisLabels = emptyList(),
            averageBuyPrice = averagePrice,
            firstBuyDate = if (hasLedger) basis.firstBuy else oldest?.date,
            priceSourceLabel = priceSourceLabel(price, position),
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
private fun PriceBoard.matchTo(position: Position): Price? =
    prices.firstOrNull { it.label == position.name }
        ?: prices.firstOrNull { position.name.startsWith(it.label) }
        ?: prices.firstOrNull { it.label.startsWith(position.name) }


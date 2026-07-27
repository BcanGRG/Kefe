package com.kefe.app.ui.screens.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.sample.SampleData
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.annualizedReturnPercent
import com.kefe.app.domain.model.costBasis
import com.kefe.app.domain.model.holdingDays
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.ui.charts.TradeMarker
import com.kefe.app.ui.format.trUpper
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
    private val clock: KefeClock,
    private val positionId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AssetDetailUiState())
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.observePositions(),
                repo.observeTransactions(positionId),
                repo.observeMembers(),
            ) { positions, transactions, members ->
                val position = positions.firstOrNull { it.id == positionId }
                buildState(position, transactions, members)
            }.collect { _state.value = it }
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
    ): AssetDetailUiState {
        if (position == null) {
            return AssetDetailUiState(loading = false, members = members)
        }

        // Yeniden eskiye: tasarimda islem gecmisi en son islemle baslar.
        val ordered = transactions.sortedWith(
            compareByDescending<Transaction> { it.date.year }
                .thenByDescending { it.date.month }
                .thenByDescending { it.date.day },
        )
        val oldest = ordered.lastOrNull()
        val newest = ordered.firstOrNull()

        val series = priceSeries(position)
        val start = newest?.date?.let { monthIndex(it) - (SeriesLength - 1) }

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
            priceSeries = if (start == null) emptyList() else series,
            tradeMarkers = if (start == null) emptyList() else markers(ordered, start),
            axisLabels = if (start == null) emptyList() else axisLabels(start),
            averageBuyPrice = averagePrice,
            firstBuyDate = if (hasLedger) basis.firstBuy else oldest?.date,
            priceSourceLabel = priceSourceLabel(position),
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

    // --- Fiyat serisi ------------------------------------------------------

    /**
     * 12 aylik birim fiyat serisi. Depoda pozisyon bazli fiyat gecmisi yok;
     * seri, tasarimdaki egrinin bicimi guncel birim fiyata olceklenerek uretilir.
     * Gercek fiyat gecmisi geldiginde yalniz bu fonksiyon degisir.
     */
    private fun priceSeries(position: Position): List<Double> =
        SeriesShape.map { it * position.unitPrice }

    private fun markers(transactions: List<Transaction>, startMonth: Int): List<TradeMarker> =
        transactions.mapNotNull { tx ->
            val index = monthIndex(tx.date) - startMonth
            if (index in 0 until SeriesLength) {
                TradeMarker(index = index, isBuy = tx.side == TradeSide.Buy)
            } else {
                null
            }
        }

    private fun axisLabels(startMonth: Int): List<String> =
        listOf(0, SeriesLength / 2, SeriesLength - 1).map { offset ->
            val date = dateOf(startMonth + offset)
            "${date.monthLabel()} ${date.year % 100}"
        }

    // --- Fiyat kaynagi -----------------------------------------------------

    /**
     * "Serbest piyasa · 14:32". Fiyat tablosu ornek veriden okunur: detay
     * gorunumu fiyat deposuna baglanmadan da tutarli kalsin diye.
     */
    private fun priceSourceLabel(position: Position): String {
        val price = matchPrice(position)
        val source = when {
            position.manualPrice || price?.isManual == true -> PriceSource.Manual
            else -> price?.source ?: PriceSource.FreeMarket
        }
        val timestamp = price?.timestamp
        return if (timestamp.isNullOrBlank()) source.label() else "${source.label()} · $timestamp"
    }

    private fun matchPrice(position: Position): Price? {
        val prices = SampleData.prices
        return prices.firstOrNull { it.label == position.name }
            ?: prices.firstOrNull { position.name.startsWith(it.label) }
            ?: prices.firstOrNull { position.name.startsWith(it.assetKey.substringAfterLast('_').trUpper()) }
    }
}

/** Grafikteki nokta sayisi - tasarimdaki 12 aylik pencere. */
private const val SeriesLength = 12

/**
 * Tasarimdaki egrinin bicimi: son noktaya orani. Grafik kendi araligini
 * olceklendirdigi icin bu oranlar egriyi birebir yeniden uretir.
 */
private val SeriesShape = listOf(
    0.7472, 0.7717, 0.7898, 0.8143, 0.8449, 0.8664,
    0.8872, 0.9117, 0.9298, 0.9513, 0.9728, 1.0,
)

private fun monthIndex(date: KefeDate): Int = date.year * 12 + (date.month - 1)

private fun dateOf(monthIndex: Int): KefeDate =
    KefeDate(year = monthIndex / 12, month = (monthIndex % 12) + 1)

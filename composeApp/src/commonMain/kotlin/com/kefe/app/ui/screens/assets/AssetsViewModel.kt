package com.kefe.app.ui.screens.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.GainDayDays
import com.kefe.app.domain.model.GainMonthDays
import com.kefe.app.domain.model.GainWeekDays
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.gainIn
import com.kefe.app.domain.model.minusDays
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.domain.model.total
import com.kefe.app.domain.model.totalValue
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.PeriodTotal
import com.kefe.app.domain.model.weightedPeriodTotal
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.ui.format.ChangePeriod
import com.kefe.app.ui.format.changeIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Varliklar listesi. MVI-lite: tek [AssetsUiState] akisi + tek [onIntent] girisi.
 *
 * Gruplama ve siralama burada yapilir; ekran hazir listeyi cizer. Ham pozisyonlar
 * ayrica tutulur, boylece siralama degistiginde depoyu yeniden dinlemeye gerek
 * kalmadan liste yeniden kurulur.
 */
class AssetsViewModel(
    private val portfolioRepository: PortfolioRepository,
    // Donem kazanci islem tarihlerini bugunle kiyaslar (bkz. HoldingGain).
    private val clock: KefeClock,
) : ViewModel() {

    private val _state = MutableStateFlow(AssetsUiState())
    val state: StateFlow<AssetsUiState> = _state.asStateFlow()

    private var positions: List<Position> = emptyList()
    private var transactionsByPosition: Map<String, List<Transaction>> = emptyMap()

    init {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePositions(),
                portfolioRepository.observeAllTransactions(),
            ) { list, transactions -> list to transactions }
                .collect { (list, transactions) ->
                    positions = list
                    transactionsByPosition = transactions.groupBy { it.positionId }
                    rebuild()
                }
        }
    }

    fun onIntent(intent: AssetsIntent) {
        when (intent) {
            is AssetsIntent.SelectSort -> {
                _state.value = _state.value.copy(sort = intent.sort)
                rebuild()
            }

            // Pencere degisince gruplar AYNI pozisyonlardan yeniden kurulur:
            // hafta ve ay zaten fiyat tablosundan geliyor, depoya gidilmez.
            is AssetsIntent.SelectChange -> {
                _state.value = _state.value.copy(change = intent.change)
                rebuild()
            }

            is AssetsIntent.ToggleGroup -> {
                val current = _state.value.collapsed
                _state.value = _state.value.copy(
                    collapsed = if (intent.assetClass in current) {
                        current - intent.assetClass
                    } else {
                        current + intent.assetClass
                    },
                )
            }
        }
    }

    private fun rebuild() {
        val today = clock.today()
        val total = positions.totalValue()
        val mode = _state.value.change
        val comparator = comparatorFor(_state.value.sort)

        val groups = positions
            .groupBy { it.assetClass }
            .map { (assetClass, rows) ->
                val groupTotal = rows.sumOf { it.value }
                // Maliyet defterden gelir; sifirsa (nakit, elle girilmemis)
                // oran hesaplanamaz ve 0 kalir - "%∞ kar" yazmaktansa sessiz.
                val groupCost = rows.sumOf { it.cost }
                val groupProfit = groupTotal - groupCost
                // Donemde satirlarin KAZANCI: donem basinda elde olan donem basi
                // fiyatiyla, donem ici alim/satim islem tutariyla (bkz. HoldingGain).
                val gains = mode.period?.let { period ->
                    rows.associate { position ->
                        position.id to position.gainIn(
                            percent = position.changeIn(period),
                            transactions = transactionsByPosition[position.id].orEmpty(),
                            // Yuzde hangi gunle olculduyse islem penceresi de oradan.
                            since = position.since(period) ?: today.minusDays(period.daysBack()),
                        )
                    }
                }.orEmpty()
                AssetGroup(
                    assetClass = assetClass,
                    total = groupTotal,
                    change = when (val period = mode.period) {
                        // Toplam: kar/zarar ve onun MALIYETE orani (getiri).
                        null -> PeriodTotal(
                            amount = groupProfit,
                            percent = if (groupCost <= 0.0) {
                                0.0
                            } else {
                                groupProfit / groupCost * 100.0
                            },
                        )
                        // Donem: satir kazanclarinin toplami - TL'ler toplanir,
                        // yuzdeler ortalanmaz.
                        else -> gains.values.toList().total()
                    },
                    profit = groupProfit,
                    positions = rows.sortedWith(comparator),
                    rowChanges = gains.mapValues { (_, gain) -> gain?.toPeriodTotal() },
                )
            }
            .sortedByDescending { it.total }

        _state.value = _state.value.copy(
            loading = false,
            groups = groups,
            totalValue = total,
            periodNote = periodNoteOf(mode.period, positions, today),
        )
    }

    private fun comparatorFor(sort: AssetSort): Comparator<Position> = when (sort) {
        AssetSort.Value -> compareByDescending<Position> { it.value }
        // "Kâra göre" TL cinsinden kar/zarardir: en cok para kazandiran ustte.
        // Yuzde degil - Ozet ekranindaki "one cikan hareketler" ile ayni olcut.
        AssetSort.Profit -> compareByDescending<Position> { it.profit }
        AssetSort.Alphabetical -> Comparator { a, b -> compareTurkish(a.name, b.name) }
    }
}

/**
 * Turkce alfabetik siralama. Ortak kodda java.text.Collator yok; harf sirasi
 * elle tanimlanir, aksi halde "Ç" ile "C", "İ" ile "I" yanlis yere duser.
 */
private const val TurkishAlphabet = "aâbcçdefgğhıiîjklmnoöprsştuüûvwxyqz"

private fun letterRank(ch: Char): Int {
    val lower = when (ch) {
        'I' -> 'ı'
        'İ' -> 'i'
        else -> ch.lowercaseChar()
    }
    val index = TurkishAlphabet.indexOf(lower)
    // Alfabede olmayanlar (rakam, nokta, bosluk) harflerden once gelir.
    return if (index >= 0) index + 100 else lower.code
}

private fun compareTurkish(a: String, b: String): Int {
    val size = minOf(a.length, b.length)
    for (i in 0 until size) {
        val diff = letterRank(a[i]) - letterRank(b[i])
        if (diff != 0) return diff
    }
    return a.length - b.length
}

/** Donemin geriye bakilan gunu - fiyat yuzdesinin pencereleriyle ayni. */
private fun ChangePeriod.daysBack(): Int = when (this) {
    ChangePeriod.Day -> GainDayDays
    ChangePeriod.Week -> GainWeekDays
    ChangePeriod.Month -> GainMonthDays
}

/** Yuzdenin olculdugu gun; gunlukte ve bilinmiyorsa null (pencere sabit). */
private fun Position.since(period: ChangePeriod): KefeDate? = when (period) {
    ChangePeriod.Day -> null
    ChangePeriod.Week -> weekSince
    ChangePeriod.Month -> monthSince
}

/**
 * Yaklasik olcunun notu: en degerli varligin yuzdesi tam gunun fiyati yerine
 * en yakin kayitli gunle olculduyse o gun yazilir. Gecmis yalniz uygulamanin
 * acildigi (ya da hesaptan gelen) gunleri tutar; gun yazilmadan kullanmak
 * yaklasik olcuyu kesin gibi gostermek olurdu.
 */
private fun periodNoteOf(period: ChangePeriod?, positions: List<Position>, today: KefeDate): String? {
    val (days, tolerance) = when (period) {
        ChangePeriod.Week -> GainWeekDays to 3
        ChangePeriod.Month -> GainMonthDays to 7
        else -> return null
    }
    val since = positions.sortedByDescending { it.value }.firstNotNullOfOrNull { it.since(period) } ?: return null
    val age = today.toEpochDay() - since.toEpochDay()
    if (age in days.toLong()..(days + tolerance).toLong()) return null
    // Ay kisaltmasina ek getirilmez ("Eyl'den", "Kas'tan" her ayda ayri kural ister).
    return "Kıyas: ${since.day} ${since.monthLabel()} fiyatı · aradaki günlerin fiyatı kayıtlı değil"
}

package com.kefe.app.domain.model

/** Varlik dagilimi dilimi - donut ve legend'in ortak girdisi. */
data class AllocationSlice(
    val assetClass: AssetClass,
    val value: Double,
    val percent: Double,
)

/**
 * Ozet ekraninin rakamlari. Hepsi POZISYONLARDAN VE ISLEM DEFTERINDEN turer -
 * elle yazilmis sabit yoktur; bir islem eklendiginde bu rakamlar da degisir.
 */
data class PortfolioTotals(
    val totalValue: Double,
    val todayChange: Double,
    val todayChangePercent: Double,
    val profit: Double,
    val profitPercent: Double,
    /** Yatirilan anapara - pozisyonlarin maliyet toplami. */
    val principal: Double,
    val monthAdded: Double,
    val monthTarget: Double,
)

/**
 * "One cikan hareketler" karti icin en cok kazandiran / gerileyen pozisyon.
 *
 * Tasarimda bu iki rakam elle yazilmisti ve ne toplam kar/zararla ne gunluk
 * degisimle uyusuyordu. Metrik TOPLAM KAR/ZARAR olarak sabitlendi ve
 * pozisyonlardan turetiliyor; boylece kart Varliklar tablosuyla celismiyor.
 *
 * Siralama TL cinsindendir: "en cok kazandiran" = en cok PARA kazandiran,
 * en yuksek yuzde degil.
 */
data class TopMover(
    val position: Position,
    val profit: Double,
    val profitPercent: Double,
)

// --- Turetme ----------------------------------------------------------------

fun List<Position>.totalValue(): Double = sumOf { it.value }

fun List<Position>.totalCost(): Double = sumOf { it.cost }

private fun Position.asMover() = TopMover(this, profit, profitPercent)

fun List<Position>.topGainer(): TopMover? =
    filter { it.profit > 0 }.maxByOrNull { it.profit }?.asMover()

fun List<Position>.topLoser(): TopMover? =
    filter { it.profit < 0 }.minByOrNull { it.profit }?.asMover()

/** Sinif bazinda toplam deger - buyukten kucuge sirali. */
fun List<Position>.byClass(): Map<AssetClass, Double> {
    val sums = LinkedHashMap<AssetClass, Double>()
    for (position in this) {
        sums[position.assetClass] = (sums[position.assetClass] ?: 0.0) + position.value
    }
    val ordered = LinkedHashMap<AssetClass, Double>()
    for (entry in sums.entries.sortedByDescending { it.value }) {
        ordered[entry.key] = entry.value
    }
    return ordered
}

fun List<Position>.allocation(): List<AllocationSlice> {
    val total = totalValue()
    if (total <= 0.0) return emptyList()
    return byClass().map { (assetClass, value) ->
        AllocationSlice(assetClass, value, value / total * 100.0)
    }
}

/**
 * Bugunku degisimin TL karsiligi.
 *
 * Her pozisyonun gunluk yuzdesi kendi degerine uygulanir; toplam portfoyun
 * gunluk yuzdesi bunlarin agirlikli birlesimidir. Tek bir "portfoy yuzdesi"
 * varsaymak buyuk pozisyonlarin etkisini yok sayardi.
 */
fun List<Position>.todayChange(): Double = sumOf { position ->
    val previous = position.value / (1.0 + position.dailyChangePercent / 100.0)
    position.value - previous
}

/**
 * Bu ay eklenen net para: alimlar eksi satislar.
 *
 * "Ne kadari yeni para, ne kadari deger artisi" ayrimini bu besler - piyasa
 * hareketi bu rakama GIRMEZ.
 */
fun List<Transaction>.netContributionIn(year: Int, month: Int): Double =
    filter { it.date.year == year && it.date.month == month }
        .sumOf { tx ->
            when (tx.side) {
                TradeSide.Buy -> tx.quantity * tx.unitPrice + tx.fee
                TradeSide.Sell -> -(tx.quantity * tx.unitPrice - tx.fee)
            }
        }

/**
 * Ozet rakamlarini pozisyonlardan ve defterden hesaplar.
 *
 * [monthTarget] kullanicinin aylik katki hedefi - ana hedeften ya da ayarlardan
 * gelir, turetilemez.
 */
fun portfolioTotals(
    positions: List<Position>,
    transactions: List<Transaction>,
    today: KefeDate,
    monthTarget: Double,
): PortfolioTotals {
    val total = positions.totalValue()
    val principal = positions.totalCost()
    val profit = total - principal
    val dayChange = positions.todayChange()
    val previousTotal = total - dayChange

    return PortfolioTotals(
        totalValue = total,
        todayChange = dayChange,
        todayChangePercent = if (previousTotal <= 0.0) 0.0 else dayChange / previousTotal * 100.0,
        profit = profit,
        profitPercent = if (principal <= 0.0) 0.0 else profit / principal * 100.0,
        principal = principal,
        monthAdded = transactions.netContributionIn(today.year, today.month),
        monthTarget = monthTarget,
    )
}

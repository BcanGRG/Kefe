package com.kefe.app.domain.model

/** Bir ayda hangi sinifa ne kadar konuldugu. */
data class ContributionSlice(
    val assetClass: AssetClass,
    val amount: Double,
)

/** Tek bir ayin katkisi. Segment yoksa o ay hic para konmamistir. */
data class MonthlyContribution(
    val date: KefeDate,
    /**
     * Ekrandaki cubugun parcalari - YALNIZ PARA KONAN siniflar.
     *
     * Net cikisla kapanan sinif burada yer almaz: cubukta negatif bir dilim
     * cizilemez ve "bu kadar biriktirdim" satirinda o siniftan cikan paranin
     * yeri yoktur.
     */
    val slices: List<ContributionSlice>,
    /**
     * O ayin NET rakami - dosyanin kendi tanimiyla "portfoye giren net para".
     *
     * Parcalardan turetiliyordu ve bu, tanimi bozuyordu: `slices` cikis yapan
     * siniflari eledigi icin toplam da onlari saymiyor, altin alip fon satan bir
     * ayda "biriktirilen" rakam gercekte olandan buyuk cikiyordu. Toplam
     * ELENMEMIS kovadan gelir; getiri satiri bu rakami dogrudan bir para tutari
     * olarak kullaniyor.
     */
    val total: Double,
) {
    /** Ekranda cizilecek bir sey yok - o ay katki yapilmamis sayilir. */
    val isEmpty: Boolean get() = slices.isEmpty()
}

/**
 * Aylik katki gecmisini ISLEM DEFTERINDEN cikarir.
 *
 * "Bu ay ne kadar biriktirdim" sorusunun cevabi defterde zaten var: o ay yapilan
 * ALIMLARIN toplami eksi SATISLARIN hasilati. Ekran bunu ornek bir listeden
 * okuyordu - kullanicinin kendi rakami tepede dururken altinda baskasinin katki
 * gecmisi duruyordu.
 *
 * Komisyon alista cebinden ciktigi icin katkiya DAHILDIR; satista hasilattan
 * duser. Boylece toplam, portfoye giren net parayi verir.
 *
 * Katki yapilmayan aylar da listede yer alir (bos segmentle): tasarim "Kasım'da
 * katkı yok" diyebilmek icin o boslugu gormek zorunda.
 */
fun monthlyContributions(
    transactions: List<Transaction>,
    positions: List<Position>,
    months: Int,
    today: KefeDate,
): List<MonthlyContribution> {
    if (months <= 0) return emptyList()

    val classOf = positions.associate { it.id to it.assetClass }
    val firstMonth = today.monthOrdinal() - (months - 1)

    // Once ay ve sinif bazinda topla, sonra bos aylari da ekleyerek sirala.
    val buckets = mutableMapOf<Int, MutableMap<AssetClass, Double>>()
    transactions.forEach { tx ->
        val ordinal = tx.date.monthOrdinal()
        if (ordinal < firstMonth || ordinal > today.monthOrdinal()) return@forEach
        val assetClass = classOf[tx.positionId] ?: return@forEach
        val signed = when (tx.side) {
            TradeSide.Buy -> tx.quantity * tx.unitPrice + tx.fee
            TradeSide.Sell -> -(tx.quantity * tx.unitPrice - tx.fee)
        }
        val month = buckets.getOrPut(ordinal) { mutableMapOf() }
        month[assetClass] = (month[assetClass] ?: 0.0) + signed
    }

    return (firstMonth..today.monthOrdinal()).map { ordinal ->
        val bucket = buckets[ordinal].orEmpty()
        MonthlyContribution(
            date = monthDateOf(ordinal),
            // Net cikisla kapanan sinif CUBUKTA yer almaz; o ay o siniftan para
            // CIKMIS demektir ve negatif bir dilim cizilemez.
            slices = bucket
                .filter { it.value > 0.0 }
                .map { (assetClass, amount) -> ContributionSlice(assetClass, amount) }
                .sortedByDescending { it.amount },
            // ...ama TOPLAM onlari da sayar: aksi halde "portfoye giren net
            // para" tanimi bozulur ve rakam oldugundan buyuk cikar.
            total = bucket.values.sum(),
        )
    }
}

internal fun KefeDate.monthOrdinal(): Int = year * 12 + (month - 1)

internal fun monthDateOf(ordinal: Int): KefeDate =
    KefeDate(year = ordinal / 12, month = (ordinal % 12) + 1)

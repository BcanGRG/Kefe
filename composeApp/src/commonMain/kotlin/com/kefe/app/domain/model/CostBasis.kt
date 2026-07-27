package com.kefe.app.domain.model

/**
 * Bir pozisyonun defterden turetilen maliyet durumu.
 *
 * `cost` alani ARTIK ELLE TUTULMAZ: farkli tarihlerde, farkli fiyatlardan alinan
 * ayni varligin tek bir "maliyet" sayisi ancak islemlerden hesaplanabilir.
 */
data class CostBasis(
    /** Elde kalan miktar (alimlar - satislar). */
    val quantity: Double,
    /** Elde kalan miktarin toplam maliyeti - iscilik/komisyon DAHIL. */
    val totalCost: Double,
    /** Agirlikli ortalama birim maliyet. */
    val averageCost: Double,
    /** Satislardan KESINLESMIS kar/zarar. */
    val realizedProfit: Double,
    /** Toplam yatirilan para (satislar dusulmeden) - "yatirdigim anapara". */
    val grossInvested: Double,
    val firstBuy: KefeDate?,
    val lastTransaction: KefeDate?,
) {
    /** Guncel fiyata gore HENUZ KESINLESMEMIS kar/zarar. */
    fun unrealizedProfit(currentPrice: Double): Double =
        (currentPrice - averageCost) * quantity

    fun currentValue(currentPrice: Double): Double = currentPrice * quantity

    /**
     * Basit yuzde getiri. DIKKAT: farkli tarihlerde alinan varliklarda bu sayi
     * "ne kadar iyi yatirim" sorusunu YANITLAMAZ - 3 ayda %20 ile 3 yilda %20
     * ayni gorunur. Zaman etkisi icin [annualizedReturn] kullanilir.
     */
    fun simpleReturnPercent(currentPrice: Double): Double =
        if (totalCost == 0.0) 0.0 else unrealizedProfit(currentPrice) / totalCost * 100.0

    companion object {
        val Empty = CostBasis(0.0, 0.0, 0.0, 0.0, 0.0, null, null)
    }
}

/**
 * Islem defterinden agirlikli ortalama maliyet hesaplar.
 *
 * YONTEM: agirlikli ortalama (weighted average). Secim gerekcesi:
 * - Tasarim zaten "Ortalama alış ₺20.940" gosteriyor.
 * - Kisisel takipte anlasilir olan budur: "gramini ortalama su fiyata aldim".
 * - Satista hangi partiyi sattigini secmek gerekmez.
 *
 * FIFO ALTERNATIFI: vergi raporlamasi gerekirse ilk-giren-ilk-cikar istenebilir.
 * Defter (tarih + miktar + fiyat) her iki yontemi de hesaplamaya yetecek bilgiyi
 * tuttugu icin yontem sonradan degistirilebilir; bu fonksiyon degisir, veri degismez.
 *
 * Iscilik/komisyon: ALISTA maliyete eklenir (odedigin paranin parcasi),
 * SATISTA hasilattan dusulur.
 */
fun List<Transaction>.costBasis(): CostBasis {
    if (isEmpty()) return CostBasis.Empty

    val ordered = sortedBy { it.date.toEpochDay() }

    var quantity = 0.0
    var totalCost = 0.0
    var realized = 0.0
    var grossInvested = 0.0
    var firstBuy: KefeDate? = null

    for (tx in ordered) {
        when (tx.side) {
            TradeSide.Buy -> {
                val spent = tx.quantity * tx.unitPrice + tx.fee
                totalCost += spent
                grossInvested += spent
                quantity += tx.quantity
                if (firstBuy == null) firstBuy = tx.date
            }

            TradeSide.Sell -> {
                // Elde olmayani satmak gecersiz veridir: kaydi tumden atlariz.
                // Yoksa satis miktari 0'a kirpilirken iscilik tek basina zarar
                // olarak yazilir ve olmayan bir satistan kayip uretilirdi.
                if (quantity > 0.0) {
                    // Satilan miktarin maliyeti, o andaki ortalamadan dusulur.
                    val average = totalCost / quantity
                    val sold = tx.quantity.coerceAtMost(quantity)
                    val proceeds = sold * tx.unitPrice - tx.fee
                    realized += proceeds - average * sold
                    totalCost -= average * sold
                    quantity -= sold
                }
            }
        }
    }

    // Kayan nokta birikimi: miktar sifirlandiginda maliyet de sifirlanmali.
    if (quantity <= 1e-9) {
        quantity = 0.0
        totalCost = 0.0
    }

    return CostBasis(
        quantity = quantity,
        totalCost = totalCost,
        averageCost = if (quantity > 0.0) totalCost / quantity else 0.0,
        realizedProfit = realized,
        grossInvested = grossInvested,
        firstBuy = firstBuy,
        lastTransaction = ordered.lastOrNull()?.date,
    )
}

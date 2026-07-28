package com.kefe.app.ui.screens.assets

import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Transaction
import com.kefe.app.ui.charts.TradeMarker

data class AssetDetailUiState(
    val loading: Boolean = true,
    val position: Position? = null,
    val transactions: List<Transaction> = emptyList(),
    val members: List<Member> = emptyList(),

    // Grafik: 12 aylik birim fiyat serisi + uzerindeki alim/satim isaretleri.
    val priceSeries: List<Double> = emptyList(),
    val tradeMarkers: List<TradeMarker> = emptyList(),
    /** Eksende gosterilen uc etiket: ilk, orta, son ay. */
    val axisLabels: List<String> = emptyList(),
    val periodLabel: String = "12 ay",

    // Ozet satirlari - hepsi ISLEM DEFTERINDEN turetilir
    val averageBuyPrice: Double = 0.0,
    val firstBuyDate: KefeDate? = null,
    val priceSourceLabel: String = "",

    /** Elde tutulanin henuz kesinlesmemis kar/zarari. */
    val unrealizedProfit: Double = 0.0,
    /** Satislardan kesinlesmis kar/zarar. Satis yoksa 0. */
    val realizedProfit: Double = 0.0,
    /** Ilk alimdan bugune gecen gun. */
    val holdingDays: Long? = null,
    /**
     * Yillik para-agirlikli getiri (XIRR), yuzde.
     *
     * null olabilir: tek islem, tum islemler ayni gun, ya da kok bulunamadi.
     * O durumda ekran bu satiri HIC gostermez - uydurma sayi uretilmez.
     */
    val annualizedReturnPercent: Double? = null,

    /**
     * Piyasa satis ile alis arasindaki fark - "aldigim gun neden zararliyim"in
     * cevabi. Fonda tek fiyat vardir, orada null.
     */
    val spreadAmount: Double = 0.0,
    val spreadPercent: Double? = null,

    /** Ust bardaki "..." menusu acik mi. */
    val menuOpen: Boolean = false,
    /** Varligi silme onayi bekleniyor. */
    val confirmDelete: Boolean = false,
) {
    /**
     * Elde tutma suresinin okunabilir hali: "2 yıl 5 ay", "7 ay", "12 gün".
     * Basit yuzde getirinin yaninda gosterilir ki hangi surede olustugu gorunsun.
     */
    val holdingLabel: String?
        get() {
            val days = holdingDays ?: return null
            if (days < 45) return "$days gün"
            val months = days / 30
            if (months < 12) return "$months ay"
            val years = months / 12
            val restMonths = months % 12
            return if (restMonths == 0L) "$years yıl" else "$years yıl $restMonths ay"
        }

    /** Uye kimliginden bas harf ve renk indeksi - islem satirlarindaki avatar icin. */
    fun memberIndexOf(memberId: String): Int =
        members.indexOfFirst { it.id == memberId }.coerceAtLeast(0)

    fun memberInitials(memberId: String): String =
        members.firstOrNull { it.id == memberId }?.initials.orEmpty()
}

sealed interface AssetDetailIntent {
    data class DeleteTransaction(val transactionId: String) : AssetDetailIntent

    data object OpenMenu : AssetDetailIntent
    data object CloseMenu : AssetDetailIntent

    /**
     * Varligi tumden silme. Islemleri tek tek silmek disinda varliktan kurtulma
     * yolu yoktu - `deletePosition` depoda duruyordu ama hicbir yerden
     * cagirilmiyordu.
     */
    data object RequestDelete : AssetDetailIntent
    data object ConfirmDelete : AssetDetailIntent
    data object DismissDeleteConfirm : AssetDetailIntent
}

sealed interface AssetDetailEffect {
    /**
     * Varlik artik listede degil - son islemi de silindi.
     *
     * Ekranda kalmak "Varlik bulunamadi" bos durumunda birakiyordu: kullanici
     * kendi sildigi seyin kaybolma haberini okuyup geri tusunu aramak zorunda
     * kaliyordu. Listeye donmek dogru davranis.
     */
    data object PositionGone : AssetDetailEffect
}

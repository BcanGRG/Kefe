package com.kefe.app.ui.screens.account

/**
 * Tutarin okunusu. Renk tek sinyal olmadigi icin satista metin ayrica eksi
 * isareti tasir; tutarsiz olaylarda alan bos kalir.
 */
enum class ActivityAmountTone { Incoming, Outgoing, Neutral }

data class ActivityRow(
    val id: String,
    val memberName: String,
    val memberInitials: String,
    val memberIndex: Int,
    val description: String,
    val timeLabel: String,
    val amountText: String,
    val amountTone: ActivityAmountTone,
    val isManualPrice: Boolean,
)

/** Gun basligi ("BUGÜN") ve o gunun satirlari. Bos gun listeye girmez. */
data class ActivityDay(
    val label: String,
    val rows: List<ActivityRow>,
)

/** `memberId` null ise "Tümü" secenegidir. */
data class ActivityFilterOption(
    val label: String,
    val memberId: String?,
)

data class ActivityUiState(
    val loading: Boolean = true,
    val filters: List<ActivityFilterOption> = emptyList(),
    val selectedFilterIndex: Int = 0,
    val days: List<ActivityDay> = emptyList(),
) {
    /** Suzgec sonucu bos ise de bos durum gosterilir - liste sessizce kaybolmaz. */
    val isEmpty: Boolean get() = !loading && days.isEmpty()
}

sealed interface ActivityIntent {
    data class SelectFilter(val index: Int) : ActivityIntent
}

package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.ActivityKind
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.ui.format.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Aktivite akisi. Suzgec GERCEKTEN suzer: secim degistiginde ayni ham liste
 * yeniden gruplanir, bos kalan gun basliklari da duser.
 */
class ActivityViewModel(
    private val portfolioRepository: PortfolioRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    private var members: List<Member> = emptyList()
    private var events: List<ActivityEvent> = emptyList()

    init {
        observeActivity()
    }

    fun onIntent(intent: ActivityIntent) {
        when (intent) {
            is ActivityIntent.SelectFilter -> {
                val index = intent.index.coerceIn(0, _state.value.filters.lastIndex.coerceAtLeast(0))
                _state.value = _state.value.copy(selectedFilterIndex = index)
                rebuild()
            }
        }
    }

    private fun observeActivity() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observeMembers(),
                portfolioRepository.observeActivity(),
            ) { memberList, activity -> memberList to activity }
                .collect { (memberList, activity) ->
                    members = memberList
                    // "Hedeflerden haric tutma" urunden cikarildi; eski kayitlari
                    // gostermek kullaniciya artik var olmayan bir ozelligi anlatir.
                    events = activity.filter { it.kind != ActivityKind.ExcludeFromGoals }
                    _state.value = _state.value.copy(
                        loading = false,
                        filters = buildFilters(memberList),
                    )
                    rebuild()
                }
        }
    }

    private fun buildFilters(memberList: List<Member>): List<ActivityFilterOption> =
        listOf(ActivityFilterOption("Tümü", null)) +
            memberList.map { ActivityFilterOption(it.name, it.id) }

    private fun rebuild() {
        val current = _state.value
        val selected = current.filters.getOrNull(current.selectedFilterIndex)?.memberId
        val visible = events.filter { selected == null || it.memberId == selected }

        // Gun sirasi depodan geldigi gibi korunur: yeniden siralamak "en yeni
        // ustte" varsayimini kirardi.
        val days = LinkedHashMap<String, MutableList<ActivityRow>>()
        for (event in visible) {
            val index = members.indexOfFirst { it.id == event.memberId }.coerceAtLeast(0)
            val member = members.getOrNull(index)
            days.getOrPut(event.dayGroup) { mutableListOf() }.add(
                ActivityRow(
                    id = event.id,
                    memberName = member?.name.orEmpty(),
                    memberInitials = member?.initials.orEmpty(),
                    memberIndex = index,
                    description = event.description,
                    timeLabel = event.timeLabel,
                    amountText = event.amountText(),
                    amountTone = event.tone(),
                    isManualPrice = event.isManualPrice,
                )
            )
        }

        _state.value = current.copy(
            days = days.map { (label, rows) -> ActivityDay(label, rows) },
        )
    }
}

/** Satista tutar eksi isaretiyle yazilir; renk tek basina yeterli degildir. */
private fun ActivityEvent.amountText(): String {
    val value = amount ?: return ""
    return when (kind) {
        ActivityKind.SellTransaction -> Money.tl(-value)
        else -> Money.tl(value)
    }
}

private fun ActivityEvent.tone(): ActivityAmountTone = when {
    amount == null -> ActivityAmountTone.Neutral
    kind == ActivityKind.SellTransaction -> ActivityAmountTone.Outgoing
    kind == ActivityKind.AddTransaction -> ActivityAmountTone.Incoming
    else -> ActivityAmountTone.Neutral
}

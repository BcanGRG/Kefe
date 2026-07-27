package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.MemberPermission
import com.kefe.app.domain.model.MemberRole
import com.kefe.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Paylasim. Yetki degisikligi yerel olarak tutulur: depoda uye yazma islemi
 * henuz yok, arayuzun dogru davranmasi icin durum burada saklanir.
 */
class ShareViewModel(
    private val portfolioRepository: PortfolioRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ShareUiState(inviteCode = InviteCode, inviteLink = InviteLink),
    )
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    /** Uye kimligi -> yetki. Depo yazma destekleyene kadar gecici kaynak. */
    private val permissionOverrides = mutableMapOf<String, MemberPermission>()
    private val removedMemberIds = mutableSetOf<String>()

    init {
        observeMembers()
    }

    fun onIntent(intent: ShareIntent) {
        when (intent) {
            ShareIntent.OpenInvite -> _state.value =
                _state.value.copy(inviteVisible = true, linkCopied = false)

            ShareIntent.DismissInvite -> _state.value = _state.value.copy(inviteVisible = false)

            ShareIntent.CopyLink -> _state.value = _state.value.copy(linkCopied = true)

            ShareIntent.ShareLink -> Unit

            ShareIntent.CancelInvite -> _state.value =
                _state.value.copy(inviteVisible = false, linkCopied = false)

            is ShareIntent.ToggleMemberMenu -> {
                val current = _state.value.permissionTargetId
                _state.value = _state.value.copy(
                    permissionTargetId = if (current == intent.memberId) null else intent.memberId,
                )
            }

            is ShareIntent.SetPermission -> {
                permissionOverrides[intent.memberId] = intent.permission
                _state.value = _state.value.copy(
                    members = _state.value.members.map {
                        if (it.id == intent.memberId) it.copy(permission = intent.permission) else it
                    },
                )
            }

            is ShareIntent.RemoveMember -> {
                removedMemberIds += intent.memberId
                _state.value = _state.value.copy(
                    members = _state.value.members.filterNot { it.id == intent.memberId },
                    permissionTargetId = null,
                )
            }
        }
    }

    private fun observeMembers() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePortfolio(),
                portfolioRepository.observeMembers(),
            ) { portfolio, members -> portfolio to members }
                .collect { (portfolio, members) ->
                    val rows = members
                        .filterNot { it.id in removedMemberIds }
                        .mapIndexed { index, member -> member.toRow(index) }
                    _state.value = _state.value.copy(
                        portfolioName = portfolio.name,
                        members = rows,
                        // Yetki bolumu ilk acilista karsi tarafi gosterir; sahip
                        // kendi yetkisini degistiremez.
                        permissionTargetId = _state.value.permissionTargetId
                            ?: rows.firstOrNull { !it.isCurrentUser }?.id,
                    )
                }
        }
    }

    /**
     * Oturum acan kullanici kavrami henuz yok; sahip rolu "siz" sayilir.
     * Kimlik katmani geldiginde yalniz bu satir degisir.
     */
    private fun Member.toRow(index: Int) = ShareMemberRow(
        id = id,
        name = name,
        initials = initials,
        index = index,
        isOwner = role == MemberRole.Owner,
        isCurrentUser = role == MemberRole.Owner,
        permission = permissionOverrides[id] ?: permission,
        lastSeen = lastSeen,
    )
}

private const val InviteCode = "472915"
private const val InviteLink = "kefe.app/katil/472915"

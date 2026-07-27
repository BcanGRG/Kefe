package com.kefe.app.ui.screens.account

import com.kefe.app.domain.model.MemberPermission

data class ShareMemberRow(
    val id: String,
    val name: String,
    val initials: String,
    val index: Int,
    val isOwner: Boolean,
    val isCurrentUser: Boolean,
    val permission: MemberPermission,
    val lastSeen: String,
)

data class ShareUiState(
    val portfolioName: String = "",
    val members: List<ShareMemberRow> = emptyList(),
    /** Yetki bolumunun acik oldugu uye. Sahip icin acilmaz. */
    val permissionTargetId: String? = null,
    val inviteVisible: Boolean = false,
    val inviteCode: String = "",
    val inviteLink: String = "",
    val inviteValidDays: Int = 7,
    val linkCopied: Boolean = false,
) {
    /** Tek kisilik portfoy tam calisir; paylasim bir ozelliktir, zorunluluk degil. */
    val isAlone: Boolean get() = members.size <= 1

    val permissionTarget: ShareMemberRow?
        get() = members.firstOrNull { it.id == permissionTargetId }
}

sealed interface ShareIntent {
    data object OpenInvite : ShareIntent
    data object DismissInvite : ShareIntent
    data object CopyLink : ShareIntent
    data object ShareLink : ShareIntent
    data object CancelInvite : ShareIntent
    data class ToggleMemberMenu(val memberId: String) : ShareIntent
    data class SetPermission(val memberId: String, val permission: MemberPermission) : ShareIntent
    data class RemoveMember(val memberId: String) : ShareIntent
}

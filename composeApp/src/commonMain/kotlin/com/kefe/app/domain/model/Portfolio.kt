package com.kefe.app.domain.model

/** Paylasilan portfoy. Uyeler yalniz kimlikle tutulur. */
data class Portfolio(
    val id: String,
    val name: String,
    val currency: String,
    val memberIds: List<String>,
)

data class Member(
    val id: String,
    val name: String,
    val initials: String,
    val role: MemberRole,
    val permission: MemberPermission,
    val lastSeen: String,
)

enum class MemberRole {
    Owner,
    Member;

    fun label(): String = when (this) {
        Owner -> "Sahip"
        Member -> "Üye"
    }
}

enum class MemberPermission {
    CanEdit,
    ViewOnly;

    fun label(): String = when (this) {
        CanEdit -> "Düzenleyebilir"
        ViewOnly -> "Sadece görüntüler"
    }
}

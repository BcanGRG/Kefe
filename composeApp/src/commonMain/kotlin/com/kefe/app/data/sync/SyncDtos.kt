package com.kefe.app.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sunucuya giden satirlarin bicimi. Kolon adlari Postgres'te snake_case; JSON
 * anahtari da oyle olmali - @SerialName eslemesi burada, sema tarafinda degil.
 *
 * user_id HER SATIRDA acikca gonderilir. Sunucuda varsayilani auth.uid() olsa da
 * bilesik anahtarli tabloda (daily_snapshots) upsert'in cakisma hedefini
 * bulabilmesi icin anahtarin tam olmasi gerekir; ustelik RLS "auth.uid() =
 * user_id" zaten yanlis bir kimligi reddeder.
 *
 * NULL'lar ACIKCA gonderilir (bkz. SyncLocalSource'taki Json): silinmis bir satir
 * dirildiginde (deletedAt tekrar null) sunucudaki mezar tasinin temizlenmesi ancak
 * "deleted_at: null" yazilirsa olur; alan atlanirsa eski deger kalirdi.
 *
 * Sunucuya YAZILMAYAN alanlar (turetilir / cihaz-yerel) DTO'da da yok: pozisyonun
 * miktar/maliyet/degeri, gunluk degisim, hedefin tahmini tarihi, islemin
 * syncState'i.
 */

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("position_id") val positionId: String,
    @SerialName("date_year") val dateYear: Long,
    @SerialName("date_month") val dateMonth: Long,
    @SerialName("date_day") val dateDay: Long,
    val side: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double,
    val fee: Double,
    val note: String?,
    val storage: String?,
    @SerialName("added_by_member_id") val addedByMemberId: String,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
    /**
     * Ayni gune dusen islemler arasindaki sira.
     *
     * Varsayilan 0: kolonu tasimayan bir yanit gelirse cozme patlamasin -
     * cagiran o durumda [updatedAt]'e duser.
     */
    @SerialName("created_at") val createdAt: Long = 0L,
    /**
     * Kaydin hedef atamasina katkisi - silmenin geri alabilmesi icin (9.sqm).
     *
     * Varsayilanlar var: kolonu tasimayan bir yanit cozme hatasi vermesin.
     * O durumda es cihazda silme atamaya dokunmaz, yani eski davranisa duser -
     * yanlis bir rakam uretmez.
     */
    @SerialName("goal_id") val goalId: String? = null,
    @SerialName("goal_delta") val goalDelta: Double = 0.0,
)

@Serializable
data class PositionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("asset_class") val assetClass: String,
    val subtype: String?,
    val karat: String?,
    val unit: String,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("manual_price") val manualPrice: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
)

@Serializable
data class GoalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("icon_key") val iconKey: String,
    val amount: Double,
    val unit: String,
    @SerialName("target_year") val targetYear: Long,
    @SerialName("target_month") val targetMonth: Long,
    @SerialName("target_day") val targetDay: Long,
    @SerialName("monthly_contribution") val monthlyContribution: Double,
    @SerialName("is_main") val isMain: Boolean,
    /**
     * ESKI ALAN. Hicbir hesap okumuyor; sunucudaki kolon NOT NULL oldugu icin
     * gonderilmeye devam ediyor. Gelen deger goz ardi edilir.
     */
    val allocation: String = "AllWealth",
    val status: String,
    @SerialName("sort_order") val sortOrder: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
)

@Serializable
data class GoalAssetDto(
    @SerialName("position_id") val positionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("goal_id") val goalId: String,
    /**
     * Atanan miktar; -1 = tum varlik. Varsayilan var cunku bu kolon sonradan
     * geldi: eski bir cihazin gonderdigi satirda alan hic bulunmayabilir ve
     * o satir "tum varlik" demektir.
     */
    val quantity: Double = -1.0,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
)

@Serializable
data class MemberDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val initials: String,
    @SerialName("sort_order") val sortOrder: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class SnapshotDto(
    @SerialName("user_id") val userId: String,
    @SerialName("date_year") val dateYear: Long,
    @SerialName("date_month") val dateMonth: Long,
    @SerialName("date_day") val dateDay: Long,
    @SerialName("total_value") val totalValue: Double,
    val principal: Double,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class ActivityDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("member_id") val memberId: String,
    val kind: String,
    val description: String,
    val amount: Double?,
    @SerialName("is_manual_price") val isManualPrice: Boolean,
    @SerialName("occurred_year") val occurredYear: Long,
    @SerialName("occurred_month") val occurredMonth: Long,
    @SerialName("occurred_day") val occurredDay: Long,
    @SerialName("time_label") val timeLabel: String?,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
)

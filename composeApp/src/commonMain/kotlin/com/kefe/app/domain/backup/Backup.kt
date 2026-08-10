package com.kefe.app.domain.backup

import kotlinx.serialization.Serializable

/**
 * Yedek dosyasinin bicimi.
 *
 * Veri YALNIZ cihazda duruyor: telefon kaybolursa ya da uygulama silinirse
 * birikim gecmisi de gider. Sunucu esitlemesi gelene kadar tek guvence bu.
 *
 * [version] okurken kontrol edilir. Ileride alan eklenirse eski yedek yine
 * okunabilmeli; okunamayacak kadar yeni bir yedek ise sessizce yarim
 * yuklenmektense reddedilir.
 *
 * FIYATLAR YEDEKLENMEZ. Onbellek ve gunluk fiyat gecmisi kaynaktan yeniden
 * gelir; yedege konsaydi geri yukleyen kullanici eski fiyatlarla acilirdi.
 * Yedek kullanicinin GIRDIGI seylerdir: varliklar, defter, hedefler, atamalar,
 * tercihler.
 */
@Serializable
data class BackupFile(
    val version: Int = CurrentBackupVersion,
    /** Yedegin alindigi gun - geri yuklerken kullaniciya gosterilir. */
    val takenOn: String,
    val portfolioName: String,
    val members: List<BackupMember> = emptyList(),
    val positions: List<BackupPosition> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val goals: List<BackupGoal> = emptyList(),
    val goalAssets: List<BackupGoalAsset> = emptyList(),
    val snapshots: List<BackupSnapshot> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

const val CurrentBackupVersion: Int = 1

@Serializable
data class BackupMember(
    val id: String,
    val name: String,
    val initials: String,
)

@Serializable
data class BackupPosition(
    val id: String,
    val name: String,
    val assetClass: String,
    val subtype: String? = null,
    val karat: String? = null,
    val unit: String,
    val unitPrice: Double,
    val manualPrice: Boolean,
)

@Serializable
data class BackupTransaction(
    val id: String,
    val positionId: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val side: String,
    val quantity: Double,
    val unitPrice: Double,
    val fee: Double,
    val note: String? = null,
    val storage: String? = null,
    val addedByMemberId: String,
    /**
     * Ayni gun ici sira (bkz. 8.sqm). Eski yedeklerde YOK: alan varsayilanla
     * 0 gelir ve geri yukleme dosya sirasina duser - o yedegin tasidigi tek
     * bilgi zaten oydu.
     */
    val createdAt: Long = 0L,
)

@Serializable
data class BackupGoal(
    val id: String,
    val name: String,
    val iconKey: String,
    val amount: Double,
    val unit: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val monthlyContribution: Double,
    val isMain: Boolean,
    val allocation: String,
    val status: String,
    val order: Int,
)

@Serializable
data class BackupGoalAsset(
    val positionId: String,
    val goalId: String,
    /**
     * Atanan miktar; -1 = tum varlik. Varsayilan var cunku alan sonradan geldi:
     * eski yedeklerde hic bulunmaz ve o kayitlar "tum varlik" demektir.
     */
    val quantity: Double = -1.0,
)

@Serializable
data class BackupSnapshot(
    val year: Int,
    val month: Int,
    val day: Int,
    val totalValue: Double,
    val principal: Double,
)

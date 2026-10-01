package com.kefe.app.domain.backup

import kotlinx.serialization.Serializable

/**
 * Yedek dosyasinin bicimi.
 *
 * Hesapsiz kullanimda veri YALNIZ cihazda duruyor: telefon kaybolursa ya da
 * uygulama silinirse birikim gecmisi de gider; tek guvence bu dosya. Hesaba
 * bagli cihazda kayitlar - plan, gelir, gider ve butce dahil - hesapta da
 * durur; yedek orada hesaptan bagimsiz ikinci bir kopyadir. (NEYDI: bu not
 * esitlemeden once yazilmisti ve "sunucu gelene kadar" diyordu.)
 *
 * [version] okurken kontrol edilir. Ileride alan eklenirse eski yedek yine
 * okunabilmeli; okunamayacak kadar yeni bir yedek ise sessizce yarim
 * yuklenmektense reddedilir.
 *
 * FIYATLAR YEDEKLENMEZ. Onbellek ve gunluk fiyat gecmisi kaynaktan yeniden
 * gelir; yedege konsaydi geri yukleyen kullanici eski fiyatlarla acilirdi.
 * Yedek kullanicinin GIRDIGI seylerdir: varliklar, defter, hedefler, atamalar,
 * aylik plan, gelir, gider, butce, tercihler.
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
    // Aylik plan, gelir, gider, butce (bkz. 12.sqm). Eski yedeklerde YOK -
    // bos liste gelir. Surum ARTIRILMADI (createdAt/goalId/quantity ile ayni
    // emsal): eski bir surum yeni yedegi acarsa bu listeleri sessizce atlar,
    // artirilsaydi yedegi tumden reddederdi. Iki telefon birlikte
    // guncellendigi icin atlamak daha az zararli.
    val planItems: List<BackupPlanItem> = emptyList(),
    val incomes: List<BackupIncome> = emptyList(),
    val expenses: List<BackupExpense> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
)

const val CurrentBackupVersion: Int = 1

@Serializable
data class BackupPlanItem(
    val id: String,
    val year: Int,
    val month: Int,
    val assetKey: String,
    val assetName: String,
    val mode: String,
    val target: Double,
    val goalId: String? = null,
    val unitPriceAtPlan: Double? = null,
)

@Serializable
data class BackupIncome(
    val id: String,
    val year: Int,
    val month: Int,
    val memberId: String,
    val kind: String,
    val amount: Double,
)

@Serializable
data class BackupExpense(
    val id: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val category: String,
    val amount: Double,
    val note: String? = null,
    val addedByMemberId: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class BackupBudget(
    val id: String,
    val year: Int,
    val month: Int,
    val category: String,
    val amount: Double,
)

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
    /**
     * Kaydin hedef atamasina katkisi (bkz. 9.sqm). Eski yedeklerde YOK ve
     * uydurulamaz: 0 gelir, o kayitlarin silinmesi atamaya dokunmaz.
     */
    val goalId: String? = null,
    val goalDelta: Double = 0.0,
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
    val status: String,
    val order: Int,
    // Kura bagli hedef ve harcanan hedef (bkz. 13.sqm). Eski yedekte YOK - null.
    val anchorUnit: String? = null,
    val anchorAmount: Double? = null,
    val spentAt: Long? = null,
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

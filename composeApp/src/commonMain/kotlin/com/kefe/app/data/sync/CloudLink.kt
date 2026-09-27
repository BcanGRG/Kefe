package com.kefe.app.data.sync

import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository

/**
 * Hesaba baglanmadan ONCE bakilan tablo: cihazda ve hesapta ne var.
 *
 * NEYDI. Baglanti adimi hesabi indirip HEMEN uyguluyordu. Cihazda da kayit
 * varsa ikisi sessizce birlesiyordu: baska bir hesaba (ya da esin kendi
 * e-postasiyla actigi bos hesaba) girilince iki ayri portfoy geri donussuz
 * karisiyordu; kullanici bunu ancak Ozet'teki rakamlar tutmayinca fark ediyordu.
 * Artik once bakilir, gerekiyorsa sorulur, sonra uygulanir.
 *
 * "Kayit" = CANLI islem ve hedef: kullanicinin elle girdigi, sayilabilen seyler.
 */
data class LinkPreview(
    /** Cihazdaki canli islem + hedef sayisi. */
    val localRecords: Int,
    /** Hesaptaki canli islem + hedef sayisi. */
    val serverRecords: Int,
    /**
     * Iki tarafta da bulunan kayit kimligi sayisi (mezar taslari dahil).
     * YALNIZ rastgele (UUID) kimlikler sayilir: `pos_*` ve `member_*` her
     * cihazda ayni turetilir, iki yabanci portfoyde de "ortak" gorunurdu.
     */
    val shared: Int,
    /** Hesapta adlandirilmis (damgali) bir profil var mi. */
    val serverNamed: Boolean,
    /**
     * Cihaz hesapsizken yedekten geri yuklendi. Geri yukleme her satiri "simdi"
     * damgalar: ortak kimlikler olsa bile LWW, yedegin eski halini hesabin yeni
     * halinin ustune yazardi. Bu yuzden karar her zaman sorulur.
     */
    val localRestored: Boolean,
)

/** Baglanti adiminin karari (bkz. [classifyLink]). */
enum class LinkDecision {
    /** Cihazda kayit yok: hesaptakiler iner. Sorulmaz. */
    Download,

    /** Hesapta kayit yok: cihazdakiler hesaba gider. Sorulmaz - giris ekrani soyledi. */
    Upload,

    /**
     * Ayni hesaba donus (orn. acik cikistan sonra yeniden giris): iki taraf
     * ortak kayitlar tasiyor. Olagan LWW; ne soru ne profil secimi.
     */
    Relink,

    /** Iki tarafta da kayit var ve ortak gecmis yok: kullaniciya sorulur. */
    Conflict,
}

/**
 * Karar tablosu. SAF: her satiri testte denenebilsin (bkz. LinkDecisionTest).
 *
 * | Cihaz | Hesap | Ortak / geri yukleme           | Karar    |
 * |-------|-------|--------------------------------|----------|
 * | 0     | her   | -                              | Download |
 * | >0    | 0     | -                              | Upload   |
 * | >0    | >0    | ortak >= 1 ve geri yukleme yok | Relink   |
 * | >0    | >0    | ortak 0 ya da geri yukleme var | Conflict |
 *
 * Ayni hesaba donus kimlik paylasir, yani Relink. Baska bir hesap ya da esin
 * kendi e-postasi hic paylasmaz: Conflict ya da Upload.
 */
fun classifyLink(preview: LinkPreview): LinkDecision = when {
    preview.localRecords == 0 -> LinkDecision.Download
    preview.serverRecords == 0 -> LinkDecision.Upload
    preview.shared >= 1 && !preview.localRestored -> LinkDecision.Relink
    else -> LinkDecision.Conflict
}

/** Cihazda da hesapta da kayit varken kullanicinin secimi. */
enum class ConflictChoice {
    /** "Hesaptakileri kullan": cihazdaki kayitlar silinir, hesabinkiler iner. */
    UseAccount,

    /** "Birleştir": iki tarafin kayitlari birlikte kalir. */
    Merge,
}

/**
 * Karar ve secimden uygulama bicimi. SAF.
 *
 * Bos cihazin indirmesi de [ApplyMode.Merge]: cihazda birlestirilecek kayit yok
 * ama acilista cekilmis "bugun" fotografi var; o gunun fotografi hesaptan
 * gelmeli (bkz. SyncLocalSink.applySnapshots).
 */
fun applyModeFor(decision: LinkDecision, choice: ConflictChoice?): ApplyMode = when (decision) {
    LinkDecision.Download -> ApplyMode.Merge
    LinkDecision.Upload -> ApplyMode.Adopt
    LinkDecision.Relink -> ApplyMode.Lww
    LinkDecision.Conflict -> when (choice) {
        ConflictChoice.UseAccount -> ApplyMode.Replace
        ConflictChoice.Merge -> ApplyMode.Merge
        null -> error("Çakışmada seçim yapılmadan bağlanılmaz")
    }
}

/**
 * Onizlemeyi kurar. SAF: yerel kayitlar ve hesabin satirlari verilir.
 */
fun buildLinkPreview(local: LocalRecords, batch: PullBatch, localRestored: Boolean): LinkPreview {
    val serverLive = batch.transactions.count { it.deletedAt == null } +
        batch.goals.count { it.deletedAt == null }
    val serverIds = batch.transactions.mapTo(mutableSetOf()) { it.id } +
        batch.goals.map { it.id }
    return LinkPreview(
        localRecords = local.liveTransactions.size + local.liveGoals.size,
        serverRecords = serverLive,
        shared = sharedRecordCount(local.allRecordIds, serverIds),
        serverNamed = batch.members.any { it.updatedAt > 0L },
        localRestored = localRestored,
    )
}

/**
 * Iki tarafta da bulunan RASTGELE kimlik sayisi. SAF.
 *
 * Belirlenimci kimlikler sayilmaz: `pos_<varlik>` ve `member_owner` her cihazda
 * ayni uretilir; eski surumlerin icerikten turettigi `tx_...`/`goal_...`
 * kimlikleri de iki yabanci portfoyde ayni cikabilir. Yalniz UUID bir kaydin
 * GERCEKTEN ayni kayit oldugunu soyler.
 */
internal fun sharedRecordCount(local: Set<String>, server: Set<String>): Int =
    local.count { it in server && it.isRandomRecordId() }

/** 8-4-4-4-12 onaltilik: newId()'nin urettigi bicim. */
internal fun String.isRandomRecordId(): Boolean {
    if (length != 36) return false
    return withIndex().all { (i, ch) ->
        if (i == 8 || i == 13 || i == 18 || i == 23) ch == '-' else ch.isHexDigitChar()
    }
}

private fun Char.isHexDigitChar(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * Bu cihazda girilmis ve hesapta OLMAYAN canli islemlerin ekleyene gore sayisi.
 * Profil secimi degisirse bunlar yeni profile aktarilir; ekran sayisini soyler.
 */
internal fun localOnlyByAuthor(local: LocalRecords, batch: PullBatch): Map<String, Int> {
    val serverTx = batch.transactions.mapTo(mutableSetOf()) { it.id }
    return local.liveTransactions
        .filterKeys { it !in serverTx }
        .values
        .groupingBy { it }
        .eachCount()
}

/**
 * Onizlenmis, henuz UYGULANMAMIS baglanti. Kullanici karar verene kadar
 * bellekte durur; [AccountLinker.commit] onu tek islemde yazar.
 */
class PreparedLink(
    val batch: PullBatch,
    val preview: LinkPreview,
    val decision: LinkDecision,
    /** Hesabin ilk profilinin adi - yalniz adlandirilmissa. */
    val serverOwnerName: String?,
    val serverPartnerName: String?,
    /** Bu cihazda girilip hesapta olmayan canli islemler, ekleyene gore. */
    val localOnlyByAuthor: Map<String, Int>,
) {
    val serverNamed: Boolean get() = serverOwnerName != null || serverPartnerName != null
}

/** Onizlemeyi ve karari hesabin satirlarindan kurar. SAF. */
fun prepareLink(batch: PullBatch, local: LocalRecords, localRestored: Boolean): PreparedLink {
    val preview = buildLinkPreview(local, batch, localRestored)
    fun named(id: String) = batch.members.firstOrNull { it.id == id && it.updatedAt > 0L }?.name
    return PreparedLink(
        batch = batch,
        preview = preview,
        decision = classifyLink(preview),
        serverOwnerName = named(LocalOwnerMemberId),
        serverPartnerName = named(LocalPartnerMemberId),
        localOnlyByAuthor = localOnlyByAuthor(local, batch),
    )
}

/**
 * Baglantiyla birlikte yazilan tercihler. SAF.
 *
 * - [PreferenceKeys.LastPushedAt]: "Hesaptakileri kullan"da SIMDI - cihaz artik
 *   tam olarak hesabin kopyasi, geri gonderilecek bir sey yok. Digerlerinde
 *   null: cihazdaki her sey hesaba gider (esit damgalari sunucunun LWW korumasi
 *   yok sayar, tekrar gondermek zararsiz). NEYDI: eski hesabin watermark'i
 *   kaliyordu; ondan once kurulan pozisyon ve hedefler yeni hesaba hic gitmiyordu.
 * - [PreferenceKeys.LocalRestoredAt] silinir: yedegin izi artik bir sonraki
 *   baglantiyi ilgilendirmez.
 * - [PreferenceKeys.LastSyncedAt] silinir: onceki baglantinin ani gosterilmez.
 */
fun linkSettings(
    userId: String,
    email: String,
    activeMemberId: String,
    mode: ApplyMode,
    now: Long,
): Map<String, String?> = mapOf(
    PreferenceKeys.ActiveMemberId to activeMemberId,
    PreferenceKeys.CloudLinkUserId to userId,
    PreferenceKeys.CloudLinkEmail to email,
    PreferenceKeys.LocalRestoredAt to null,
    PreferenceKeys.LastPushedAt to if (mode == ApplyMode.Replace) now.toString() else null,
    PreferenceKeys.LastSyncedAt to null,
)

/**
 * Hesaba baglanma: once BAK ([preview]), karar verilince TEK ISLEMDE yaz
 * ([commit]).
 *
 * BAGLANTIYI YALNIZ BURASI YAZAR. Esitleme baglanti anahtari yazildigi an
 * baslar (bkz. CloudMode); bu yuzden onizleme patlarsa ya da kullanici
 * vazgecerse hicbir sey yazilmaz ve sunucuya tek bir satir gitmez.
 */
class AccountLinker(
    private val pullEngine: PullEngine,
    private val sink: SyncLocalSink,
    private val preferences: PreferencesRepository,
    private val clock: KefeClock,
) {

    /**
     * Hesabi indirir ve karari verir; HICBIR SEY YAZMAZ. Jeton yoksa null.
     * Ag/sunucu hatasi firlatilir - cagiran "Tekrar dene"yi gosterir.
     */
    suspend fun preview(): PreparedLink? {
        val batch = pullEngine.fetch() ?: return null
        val local = sink.readLocalRecords()
        val restored = preferences.get(PreferenceKeys.LocalRestoredAt) != null
        return prepareLink(batch, local, restored)
    }

    /**
     * Baglantiyi kurar. [choice] yalniz [LinkDecision.Conflict]'te gerekir.
     *
     * Bu telefonun profili onceki secimden farkliysa ([PreferenceKeys.ActiveMemberId])
     * cihazda girilmis kayitlar yeni profile aktarilir - "Hesaptakileri kullan"
     * disinda (orada cihazin kayitlari zaten silinir).
     */
    suspend fun commit(
        prepared: PreparedLink,
        choice: ConflictChoice?,
        userId: String,
        email: String,
        activeMemberId: String,
        renames: List<MemberRename> = emptyList(),
    ) {
        val mode = applyModeFor(prepared.decision, choice)
        val previous = preferences.get(PreferenceKeys.ActiveMemberId)
        val remap = previous
            ?.takeIf { it != activeMemberId && mode != ApplyMode.Replace }
            ?.let { AuthorRemap(from = it, to = activeMemberId) }
        val now = clock.nowEpochMillis()
        sink.commitLink(
            batch = prepared.batch,
            mode = mode,
            renames = renames,
            remap = remap,
            settings = linkSettings(userId, email, activeMemberId, mode, now),
            now = now,
        )
    }
}

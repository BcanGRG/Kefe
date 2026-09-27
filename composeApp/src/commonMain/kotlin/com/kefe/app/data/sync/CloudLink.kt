package com.kefe.app.data.sync

import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.incomeIdOf
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
 * "Kayit" = CANLI islem, hedef ve plan tablolarinin satirlari (plan, gelir,
 * harcama, butce): kullanicinin elle girdigi, sayilabilen seyler. Planin neden
 * sayildigi: bkz. [buildLinkPreview].
 */
data class LinkPreview(
    /** Cihazdaki canli islem + hedef + plan/gelir/harcama/butce sayisi. */
    val localRecords: Int,
    /** Hesaptaki ayni sayim. */
    val serverRecords: Int,
    /**
     * Iki tarafta da bulunan kayit kimligi sayisi (mezar taslari dahil).
     * YALNIZ rastgele (UUID) kimlikler sayilir - islem, hedef, harcama:
     * `pos_*` ve `member_*` her cihazda ayni turetilir, iki yabanci portfoyde
     * de "ortak" gorunurdu. Plan, gelir ve butce kimlikleri de icerikten
     * turer (`pi_2026_10_gold_gram`); ayni nedenle sayilmaz.
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
 *
 * PLAN, GELIR, HARCAMA VE BUTCE DE "KAYIT" SAYILIR - iki yanda da.
 *
 * NEDEN. Sayilmasalar yalniz plan girilmis bir cihaz "bos" gorunurdu: dolu bir
 * hesaba baglaninca soru sorulmadan indirme (Birleştir kurallari) calisir,
 * cihazin Ekim maasi hesabin Ekim maasiyla ayni kimlikte bulusup sessizce
 * hesabinkine doner, cihazdaki harcamalar da sorulmadan hesaba eklenirdi. Tersi
 * de: yalniz plani olan bir hesap "bos" sayilir, cihazdakiler sorusuz ona
 * akardi. En az sasirtan kural en basiti: kullanicinin elle girdigi her satir
 * bir kayittir; iki yanda da kayit varsa ve ortak gecmis yoksa SORULUR. Ekrandaki
 * "Bu cihaz: n kayıt · Hesap: m kayıt" da boylece dogru olur.
 *
 * Ortaklik yalniz UUID kimliklerden (islem, hedef, harcama): plan, gelir ve
 * butce kimlikleri icerikten turer, iki yabanci portfoyde de ayni cikar (bkz.
 * [sharedRecordCount]).
 */
fun buildLinkPreview(local: LocalRecords, batch: PullBatch, localRestored: Boolean): LinkPreview {
    val serverLive = batch.transactions.count { it.deletedAt == null } +
        batch.goals.count { it.deletedAt == null } +
        batch.planItems.count { it.deletedAt == null } +
        batch.incomes.count { it.deletedAt == null } +
        batch.expenses.count { it.deletedAt == null } +
        batch.budgets.count { it.deletedAt == null }
    val serverIds = batch.transactions.mapTo(mutableSetOf()) { it.id } +
        batch.goals.map { it.id } +
        batch.expenses.map { it.id }
    return LinkPreview(
        localRecords = local.liveCount,
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
 * Profil secimi degisirse YENI profile aktarilacak kayitlarin, eski profile
 * gore sayisi. Ekran bunu "n kayıt da ... adına aktarılır" diye soyler (bkz.
 * SyncLocalSink.commitLink, remapNote).
 *
 * Islem ve harcamada: bu cihazda girilmis, hesapta OLMAYAN canli satirlar
 * (ekleyene gore). Ekleyeni bos eski bir harcama sayilmaz: o aktarilmaz da.
 *
 * Gelirde: yalniz GERCEKTEN tasinacak satirlar, aktarimin kendi kurali ile
 * (bkz. [planIncomeRemap]; iki profil var, hedef hep otekisi). NEYDI: hesapta
 * olmayan her canli gelir sayiliyordu. Oysa hedefi hesapta dolu olan satir
 * hesabinkine birakilip siliniyor, hedefi bu cihazda dolu olan yerinde
 * kaliyordu: ekran "5 kayıt aktarılır" deyip 3'unu tasiyordu.
 */
internal fun localOnlyByAuthor(local: LocalRecords, batch: PullBatch): Map<String, Int> {
    val serverTx = batch.transactions.mapTo(mutableSetOf()) { it.id }
    val serverExpenses = batch.expenses.mapTo(mutableSetOf()) { it.id }
    val authors = local.liveTransactions.filterKeys { it !in serverTx }.values +
        local.liveExpenses.filterKeys { it !in serverExpenses }.values.filterNotNull()
    val counts = authors.groupingBy { it }.eachCount().toMutableMap()
    val profiles = listOf(LocalOwnerMemberId to LocalPartnerMemberId, LocalPartnerMemberId to LocalOwnerMemberId)
    for ((from, to) in profiles) {
        val moved = planIncomeRemap(local.liveIncomes, batch.incomes, AuthorRemap(from, to))
            .count { it is IncomeMove.Move }
        if (moved > 0) counts[from] = (counts[from] ?: 0) + moved
    }
    return counts
}

/** Bu cihazin bir gelir satirinin aktarimdaki akibeti (bkz. [planIncomeRemap]). */
internal sealed interface IncomeMove {
    /** Yeni profile YENI kimlikle yazilir, eski kimlik mezar taslanir. */
    data class Move(val source: LocalIncome, val targetId: String) : IncomeMove

    /** Hedefte hesabin canli satiri var: hesabinki kalir, cihazinki mezar taslanir. */
    data class YieldToAccount(val sourceId: String) : IncomeMove
}

/**
 * Profil degisince bu cihazin gelirlerine ne olacagi. SAF: hem baglanti
 * (SyncLocalSink.commitLink) hem onizlemedeki sayi ([localOnlyByAuthor]) bunu
 * kullanir; ikisi ayri kural isletseydi ekran bir sey soyleyip baska bir sey
 * yapardi.
 *
 * [device]: cihazin CANLI gelirleri, hesabin satirlari UYGULANMADAN ONCEKI
 * hali. [account]: hesabin gelirleri (mezar taslari dahil). Listede olmayan
 * satir yerinde kalir.
 *
 * GELIRIN KIMLIGI KISIYI TASIR (inc_<yyyy>_<mm>_<uye>_<tur>) ve ayni uye
 * kimligi iki yanda FARKLI kisileri adlandirabilir - aktarimin var olma
 * nedeni bu (cihazda Merve ilk profil, hesapta ilk profil Burak). Bu yuzden
 * "kimlik hesapta da var" burada "hesabin kaydi" DEMEK DEGIL (UUID
 * tablolarinda oyle): Merve'nin Ekim maasi Burak'in Ekim maasiyla ayni
 * kimlikte durabilir. NEYDI: kimligi hesapta olan satir aktarilmiyordu;
 * "Birleştir"de Merve'nin maasi Burak'inkiyle ezilip sessizce kayboluyor,
 * hesapta orada yalniz mezar tasi varsa Burak'in adina hesaba gidiyordu.
 *
 * Satir yalniz hesapta AYNI kimlik ve AYNI tutarla duruyorsa hesabin kendi
 * satiri sayilir ve tasinmaz: ayni hesabin yedeginden geri yuklenmis ortak
 * gecmis (mezar tasi da olsa - hesap onu sonradan silmis). Bedeli: iki ayri
 * kisinin ayni ay ayni turden geliri kurus kurusuna esitse cihazinki
 * tasinmaz, hesabinkiyle ayni tutar olarak kalir.
 *
 * HEDEF KIMLIK DOLUYSA (orada canli bir satir var):
 *  - Hesapta canliysa hesabinki kalir, cihazinki mezar taslanir. Ayni kisinin
 *    ayni ayki ayni turden geliri TEK bir bilgidir; "Birleştir"de iki
 *    tarafta da olan satirda oldugu gibi hesabin degeri gecerli. Ikisini de
 *    tutmak ayni maasi iki profile yazip haneyi iki kez sayardi.
 *  - Yalniz bu cihazda canliysa (cihaz iki profilin gelirini de girmis)
 *    satir TASINMAZ, eski profilde kalir. Toplamak iki ayri maasi tek satira
 *    gomerdi, daha yeniyi secmek otekini sessizce silerdi. Yanlis adda duran
 *    bir tutar gorunur ve duzeltilebilir; silinen geri gelmez.
 *
 * Donemi gecersiz bir satir yeniden kimliklenemez; baglantiyi dusurmek
 * yerine oldugu yerde kalir. Tur METIN olarak tasinir (bkz. incomeIdOf).
 */
internal fun planIncomeRemap(
    device: List<LocalIncome>,
    account: List<IncomeEntryDto>,
    remap: AuthorRemap,
): List<IncomeMove> {
    val accountById = account.associateBy { it.id }
    val accountLive = account.filter { it.deletedAt == null }.mapTo(mutableSetOf()) { it.id }
    val deviceLive = device.mapTo(mutableSetOf()) { it.id }
    val moves = mutableListOf<IncomeMove>()
    for (row in device) {
        if (row.memberId != remap.from) continue
        if (accountById[row.id]?.amount == row.amount) continue
        val month = runCatching { YearMonth(row.periodYear.toInt(), row.periodMonth.toInt()) }.getOrNull() ?: continue
        val target = incomeIdOf(month, remap.to, row.kind)
        when {
            target in accountLive -> moves += IncomeMove.YieldToAccount(row.id)
            target in deviceLive -> Unit
            else -> {
                moves += IncomeMove.Move(row, target)
                deviceLive += target
                deviceLive -= row.id
            }
        }
    }
    return moves
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
    /** Profil degisirse yeni profile aktarilacak islem, harcama ve gelirler, eski profile gore. */
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

package com.kefe.app.domain.model

import kotlin.math.min

/**
 * Bir varligin bir hedefe atanmis kismi.
 *
 * [quantity] < 0 ise TUM VARLIK sayilir - eski (miktarsiz) atamalarin ve hedef
 * detayindaki "Varlik sec" listesinin anlami budur: "bu varligin tamami bu
 * hedefi karsilar". Sifir ya da uzeri ise o kadar adet/gram sayilir.
 */
data class GoalAssignment(
    val goalId: String,
    val quantity: Double = WholePosition,
) {
    val isWhole: Boolean get() = quantity < 0.0

    companion object {
        /** "Tum varlik" isareti. Negatif secildi: gecerli bir miktar olamaz. */
        const val WholePosition: Double = -1.0
    }
}

/**
 * Bu atamanin BUGUN kac birime karsilik geldigi.
 *
 * POZISYONUN MIKTARIYLA SINIRLIDIR - ama bu artik yalniz bir EMNIYET KEMERI.
 *
 * Kirpma once tek mekanizmaydi: satista atamaya dokunulmuyor, okurken
 * `min(atanan, pozisyon)` aliniyordu. O kirpma MONOTON DEGIL. 10 ceyrek Ev'e
 * atanmisken 6 tanesi satilirsa hedef 6 sayar; sonra 4 ceyrek daha alinirsa
 * pozisyon 10'a doner ve hedef yeniden 10 saymaya baslar - satilan ceyrekler
 * hedefe geri gelir. Ustelik bu, kodun kendi yazili kuraliyla da celisiyordu:
 * "miktari belli atamalarda yeni alim nasilsa sayilmiyor".
 *
 * Artik SATIS ATAMAYI KALICI OLARAK DUSURUYOR (bkz. [goalAssignmentChange]) ve
 * burasi yalniz baska yollardan (geri yukleme, esitleme, pozisyonun elle
 * duzeltilmesi) gelen tutarsizliklara karsi duruyor.
 */
fun GoalAssignment.effectiveQuantity(position: Position): Double =
    if (isWhole) position.quantity else min(quantity, position.quantity).coerceAtLeast(0.0)

/**
 * Atanan kismin bugunku TL degeri.
 *
 * Oransal: varligin degeri zaten miktar x birim fiyat, atanan kisim da o
 * degerin ayni oranidir. Birim fiyati burada yeniden carpmak, elle fiyatlanan
 * varliklarda (deger baska yerden geliyor) iki farkli rakam uretirdi.
 */
fun GoalAssignment.valueIn(position: Position): Double {
    if (position.quantity <= 0.0) return 0.0
    return position.value * (effectiveQuantity(position) / position.quantity)
}

/**
 * Bir hedefi hangi varliklarin karsiladigi = YALNIZ o hedefe atanan varliklar.
 *
 * Kati atama: atama yoksa 0 doner, hedef %0 kalir - kullanici varlik atayana
 * kadar dolmaz. (Once "atama yoksa tum birikim sayilir" fallback'i vardi; ama
 * o zaman hedefsiz eklenen bir varlik atama yapilmamis her hedefin
 * "karsilayanlar" listesinde beliriyordu ve karmasa yaratiyordu. Kullanici bu
 * yuzden kati atamayi secti.)
 *
 * Atama artik MIKTAR tasir: varligin tamami degil, atanan kadari sayilir.
 */
fun goalWealth(
    goal: Goal,
    positions: List<Position>,
    /** positionId -> atama. */
    assignments: Map<String, GoalAssignment>,
): Double = positions.sumOf { position ->
    val assignment = assignments[position.id]
    if (assignment?.goalId != goal.id) 0.0 else assignment.valueIn(position)
}

/**
 * Bir hedefi karsilayan varligin O HEDEFE DUSEN kismi.
 *
 * Ekranlarin `Position`i dogrudan gostermesi, atama miktar tasimaya baslayinca
 * YALAN soyler oldu: hedefe 15 ceyrek atanmisken liste "16 adet" ve varligin
 * tam degerini yaziyordu. Neyin sayildigini tasiyan tek tip burasi.
 */
data class GoalAsset(
    val position: Position,
    val assignment: GoalAssignment,
) {
    /** Hedefe sayilan miktar - pozisyonunkiyle sinirli. */
    val quantity: Double get() = assignment.effectiveQuantity(position)

    /** Hedefe sayilan TL degeri. */
    val value: Double get() = assignment.valueIn(position)

    /**
     * Hedefe sayilan kismin MALIYETI - [value] ile ayni oranda.
     *
     * Hedefe ozel getiri ancak bununla hesaplanabilir: 16 ceyregin 15'i bu
     * hedefteyse maliyet de 15/16'sidir. Tam maliyeti kullanmak, hedefin
     * getirisini oldugundan dusuk gosterirdi.
     *
     * Ortalama maliyet varsayimi: hangi ceyregin hedefe sayildigi bilinmiyor
     * ve bilinemez - atama miktar tutuyor, belli bir islemi degil.
     */
    val cost: Double
        get() = if (position.quantity <= 0.0) {
            0.0
        } else {
            position.cost * (quantity / position.quantity)
        }

    /** Hedefe dusen kismin kar/zarari. */
    val profit: Double get() = value - cost

    /**
     * Varligin tamami mi sayiliyor. Kismi ise ekran bunu acikca yazmali;
     * "tamami" ise miktar tekrar edilmemeli.
     */
    val coversWholePosition: Boolean
        get() = assignment.isWhole || quantity >= position.quantity
}

/** Hedefe atanmis varliklar - detay ekranindaki liste. */
fun assetsOf(
    goal: Goal,
    positions: List<Position>,
    assignments: Map<String, GoalAssignment>,
): List<GoalAsset> = positions.mapNotNull { position ->
    val assignment = assignments[position.id]?.takeIf { it.goalId == goal.id }
    assignment?.let { GoalAsset(position, it) }
}

/**
 * Bir islemin atamaya YAPTIGI degisiklik.
 *
 * [delta] islemin GERI ALINABILIR katkisidir: kayit silinince ya da
 * duzenlenince atamaya `-delta` uygulanir. Islemin uzerinde saklanir
 * ([Transaction.goalId] / [Transaction.goalDelta]) cunku silme aninda o rakam
 * baska hicbir yerden turetilemez.
 *
 * Delta secildi, "onceki degeri geri yaz" degil: mutlak geri yazma yalniz son
 * islem geri alinirsa dogru olur, arasina baska kayit girerse eski bir degeri
 * diriltir.
 *
 * AMA DELTA HER ZAMAN TOPLANABILIR DEGIL. Satis dali sonucu elde kalan miktara
 * kirpiyor, yani delta o anki duruma bagli. Bir zamanlar burada "sira onemsiz"
 * yaziyordu ve duzenleme buna guvenip once yeni kaydi yaziyordu; kirpma devreye
 * girdigi anda yanlis sonuc veriyordu (8 satip 3'e cekmek atamayi 7 yerine 8
 * birakiyordu). Duzenleme artik ONCE eskisini geri alir, sonra yenisini
 * hesaplar - hepsi tek yazmada (bkz. PortfolioRepository.replaceTransaction).
 *
 * [delta] SIFIR ise degisiklik geri alinamaz; iki durumda boyle olur ve ikisi de
 * aritmetik bir katki degil, kullanicinin ACIK bir kararidir:
 *   - "Tum varlik" atamasini miktara dondurmak (asagida),
 *   - "Tum varlik" atamasini oldugu gibi korumak.
 */
data class GoalAssignmentChange(
    val goalId: String,
    /** Atamaya yazilacak yeni miktar; -1 ise "tum varlik". */
    val quantity: Double,
    val delta: Double,
)

/**
 * Islem kaydedilirken atamanin ALACAGI yeni miktar; `null` ise atamaya
 * DOKUNULMAZ.
 *
 * Hedef secicisi YALNIZ BU ISLEMIN nereye sayilacagini soyler.
 *
 * ALIMDA [selectedGoalId] doluyken:
 *   - Ayni hedef  -> miktar bu islem kadar artar.
 *   - "Tum varlik" atamasi ayni hedefte KORUNUR: kullanici bir kez "tamami bu
 *     hedefe" demisse sonraki alimlar da oraya sayilmali.
 *   - Baska hedef (ya da ilk atama) -> miktar bu islemin miktari olur; varlik
 *     o hedefe TASINIR. Bu tasima GERI ALINDIGINDA varlik eski hedefine
 *     donmez, atamasiz kalir (miktar 0): eski hedefin adi hicbir yerde
 *     saklanmiyor ve onu saklamak butun bir geri-alma gunlugu demek olurdu.
 *
 * SATIS SECICIYE BAKMAZ. Satis ancak varligin ICINDE BULUNDUGU hedeften
 * dusebilir; baska bir hedef secmek o hedefe altin EKLEMEZ. Once bakmiyordu ve
 * "baska hedef" dali `isSell`'i hic okumadigi icin bir SATIS kaydinda baska
 * hedef secmek o hedefin ilerlemesini satilan miktar kadar ARTIRIYORDU.
 *
 * Satis, secicide "Hedefsiz" secili olsa bile atamayi ELDE KALANA kirpar -
 * satilan kadar dusurmez. Boylece kirpma kalicilasir ve monoton olur
 * (bkz. [effectiveQuantity]) ama satisin dokunmadigi birimler hedefte kalir.
 *
 * "HEDEFSIZ" (null) VE "TUM VARLIK" ATAMASI. Burasi ilk surumde eksikti ve
 * duzeltme sahada tutmadi: eski atamalarin hepsi -1 (tum varlik) oldugu icin
 * "Hedefsiz" hicbir sey ifade etmiyordu - hedef "hepsi" dedigi surece yeni
 * alinan da sayiliyordu. Kullanici 15 ceyregi Ev'deyken 1 tane hedefsiz
 * ekliyor, hedef 16 gosteriyordu.
 *
 * Cozum: hedefsiz bir ALIM, "tum varlik" atamasini o ana kadarki miktara
 * SABITLER ([quantityBefore]). "Tamami bu hedefe" sozu bugune kadar alinanlar
 * icindir; kullanici yeni alimin disarida kalmasini acikca istediginde o soz
 * dondurulur. Miktari zaten belli olan atamalara dokunulmaz - onlarda yeni alim
 * nasilsa sayilmiyor.
 */
fun goalAssignmentChange(
    current: GoalAssignment?,
    selectedGoalId: String?,
    transactionQuantity: Double,
    isSell: Boolean,
    /** Pozisyonun BU ISLEMDEN ONCEKI miktari. */
    quantityBefore: Double,
): GoalAssignmentChange? {
    val quantity = transactionQuantity.coerceAtLeast(0.0)

    if (isSell) {
        // Satis yalniz varligin bulundugu hedeften duser; secici okunmaz.
        // "Tum varlik" atamasinda dusulecek bir sayi yok: pozisyonun kendisi
        // kuculuyor ve atama onu zaten takip ediyor.
        if (current == null || current.isWhole) return null

        // OLCU, SATISTAN SONRA ELDE KALANDIR - atamanin kendisi degil.
        //
        // Once `min(satilan, atanan)` kadar dusuluyordu ve bu, satisin hic
        // dokunmadigi birimleri hedeften siliyordu: 10 ceyregin 4'u Ev'e
        // atanmisken atanmamis 6 tanesini satmak `min(6,4)=4` dusurup atamayi
        // SIFIRLIYORDU. Oysa elde tam da soz verilen 4 ceyrek duruyor.
        //
        // Dogru soru "ne kadari satildi" degil, "geriye ne kaldi": atama
        // kalandan buyuk olamaz, kucukse de dokunulmaz. Sonuc hala monoton -
        // deger yalniz asagi gidebilir - yani kirpmanin kaliciligi bozulmuyor.
        val remaining = (quantityBefore - quantity).coerceAtLeast(0.0)
        val next = min(current.quantity, remaining)
        val delta = next - current.quantity
        if (delta == 0.0) return null
        return GoalAssignmentChange(current.goalId, quantity = next, delta = delta)
    }

    return when {
        // Hedefsiz: yalniz "tum varlik" atamasini dondurmak icin mudahale edilir.
        selectedGoalId == null ->
            if (current != null && current.isWhole) {
                GoalAssignmentChange(current.goalId, quantityBefore.coerceAtLeast(0.0), delta = 0.0)
            } else {
                null
            }

        current == null || current.goalId != selectedGoalId ->
            GoalAssignmentChange(selectedGoalId, quantity, delta = quantity)

        current.isWhole ->
            GoalAssignmentChange(selectedGoalId, GoalAssignment.WholePosition, delta = 0.0)

        else ->
            GoalAssignmentChange(selectedGoalId, current.quantity + quantity, delta = quantity)
    }
}

/**
 * Silinen bir islemin atamaya yaptigi degisikligin GERI ALINMIS hali; `null`
 * ise atamaya dokunulmaz.
 *
 * NEYDI. Islem silmek atamaya hic dokunmuyordu ve duzenleme, silmeden once yeni
 * kaydi yazdigi icin katkiyi IKINCI KEZ uyguluyordu. Iki sonucu vardi:
 *   - 4 ceyreklik bir alimi silmek pozisyonu 6'ya dusuruyor ama atama 10
 *     kaliyordu; okuma aninda kirpildigi icin bugun goze batmiyor, sonraki alim
 *     hedefi yeniden 10'a cikariyordu.
 *   - 4 ceyreklik bir SATISI 3'e cekmek atamayi 10 -> 6 -> 3 yapiyordu; hedef
 *     kalici olarak eksik sayiyordu ve geri getirmenin yolu yoktu.
 *
 * Geri alma yalniz atama HALA O HEDEFTEYSE yapilir: varlik arada baska bir
 * hedefe tasinmissa eski hedefin rakamini kurcalamak yeni bir yalan olurdu.
 * "Tum varlik" atamasi da dokunulmaz - onun sayisal bir katkisi yok.
 */
fun revertedAssignmentQuantity(
    current: GoalAssignment?,
    /** Islemin atamayi oynattigi hedef; null ise oynatmamis. */
    goalId: String?,
    delta: Double,
): Double? {
    if (goalId == null || delta == 0.0) return null
    if (current == null || current.isWhole || current.goalId != goalId) return null
    return (current.quantity - delta).coerceAtLeast(0.0)
}

/**
 * Bir varligin baska hedefe atanip atanmadigi - secici bunu gostermeli.
 *
 * Atama tekildir: baska hedefe atanmis bir varligi secmek onu TASIR. Kullanici
 * bunu okumadan yapmamali, yoksa bir hedefin ilerlemesi sessizce duser.
 */
fun otherGoalOf(
    positionId: String,
    goal: Goal,
    assignments: Map<String, GoalAssignment>,
): String? = assignments[positionId]?.goalId?.takeIf { it != goal.id }

// --- Hedef bazli rakamlar ----------------------------------------------------

/**
 * Bu hedefin BUGUNKU degisimi - atanan kisimlarla.
 *
 * Portfoy geneliyle ayni mantik ([weightedPeriodTotal]): yuzdeler ortalanmaz,
 * her kalem gun basindaki degerine geri cozulur ve TL farklar toplanir. Aksi
 * halde hedefteki kucuk bir fonun %10'u, buyuk altinin %1'iyle ayni agirligi
 * tasirdi.
 *
 * Yuzde SIFIR olabilir ve bu dogrudur: hafta sonu hicbir piyasa oynamaz
 * (bkz. [Price.todayChangePercent]).
 */
fun List<GoalAsset>.todayChange(): PeriodTotal? =
    weightedPeriodTotal(map { it.value to it.position.dailyChangePercent })

/**
 * Bu hedefin TOPLAM getirisi - atanan kisimlarin kar/zarari.
 *
 * Payda MALIYETTIR, guncel deger degil: "koydugum paraya gore ne kazandim"
 * sorusu bu. Ozet ekranindaki toplam getiriyle ayni tanim, boylece iki ekran
 * birbiriyle celismez.
 *
 * Maliyeti sifir olan hedef (hepsi elle fiyatli, defteri olmayan varlik) null
 * doner - sifira bolmek yerine ekran "—" yazar.
 */
fun List<GoalAsset>.totalReturn(): PeriodTotal? {
    val cost = sumOf { it.cost }
    if (cost <= 0.0) return null
    val profit = sumOf { it.profit }
    return PeriodTotal(amount = profit, percent = profit / cost * 100.0)
}

/**
 * Hedefe atanan kisimlarin varlik sinifi dagilimi.
 *
 * [List<Position>.allocation] ile ayni cikti, farkli girdi: orada pozisyonun
 * TAMAMI sayilir, burada yalniz hedefe dusen kismi. Ikisini tek fonksiyona
 * sigdirmak, "deger" kelimesinin iki ayri sey demesine yol acardi.
 */
fun List<GoalAsset>.allocation(): List<AllocationSlice> {
    val total = sumOf { it.value }
    if (total <= 0.0) return emptyList()

    val sums = LinkedHashMap<AssetClass, Double>()
    for (asset in this) {
        val key = asset.position.assetClass
        sums[key] = (sums[key] ?: 0.0) + asset.value
    }
    return sums.entries
        .sortedByDescending { it.value }
        .map { AllocationSlice(it.key, it.value, it.value / total * 100.0) }
}

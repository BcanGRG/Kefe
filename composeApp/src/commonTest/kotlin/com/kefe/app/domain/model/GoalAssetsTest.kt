package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EPS = 1e-9

private fun position(id: String, value: Double, quantity: Double = 1.0): Position = Position(
    id = id,
    name = id,
    assetClass = AssetClass.Gold,
    quantity = quantity,
    unit = QuantityUnit.Piece,
    unitPrice = value / quantity,
    value = value,
    cost = value,
)

/** Eski (miktarsiz) atamalarin karsiligi: tum varlik. */
private infix fun String.whollyIn(goalId: String): Pair<String, GoalAssignment> =
    this to GoalAssignment(goalId)

private fun String.partlyIn(goalId: String, quantity: Double): Pair<String, GoalAssignment> =
    this to GoalAssignment(goalId, quantity)

/** Kayit anindaki atama aritmetigi - uzun imzayi testlerde tekrarlamamak icin. */
private fun next(
    current: GoalAssignment?,
    selectedGoalId: String?,
    quantity: Double,
    isSell: Boolean = false,
    before: Double = 0.0,
): GoalAssignmentChange? = goalAssignmentChange(
    current = current,
    selectedGoalId = selectedGoalId,
    transactionQuantity = quantity,
    isSell = isSell,
    quantityBefore = before,
)

private fun goal(id: String): Goal = Goal(
    id = id,
    name = id,
    iconKey = "home",
    amount = 100_000.0,
    unit = GoalUnit.Try,
    targetDate = KefeDate(2028, 12, 1),
    monthlyContribution = 5_000.0,
)

private val ev = goal("ev")
private val araba = goal("araba")
private val positions = listOf(
    position("altin", 20_000.0),
    position("euro", 5_000.0),
    position("fon", 3_000.0),
)

class GoalAssetsTest {

    @Test
    fun atamaYoksaSifir() {
        // Kati atama: atama yapilmamis hedef 0 sayar - tum birikim GELMEZ.
        assertEquals(0.0, goalWealth(ev, positions, emptyMap()), EPS)
    }

    @Test
    fun atamaVarsaYalnizOnlarSayilir() {
        val assignments = mapOf("altin" whollyIn "ev")
        assertEquals(20_000.0, goalWealth(ev, positions, assignments), EPS)
    }

    @Test
    fun birdenFazlaVarlikToplanir() {
        val assignments = mapOf("altin" whollyIn "ev", "fon" whollyIn "ev")
        assertEquals(23_000.0, goalWealth(ev, positions, assignments), EPS)
    }

    @Test
    fun herHedefYalnizKendiAtamasiniSayar() {
        // Araba'ya atama var, Ev'e yok: Ev 0 (kati atama), araba yalniz altini.
        val assignments = mapOf("altin" whollyIn "araba")
        assertEquals(0.0, goalWealth(ev, positions, assignments), EPS)
        assertEquals(20_000.0, goalWealth(araba, positions, assignments), EPS)
    }

    @Test
    fun atanmisVarlikSilinirseHedefSifirlanir() {
        // Atama duruyor ama varlik listede yok: hedef 0 gorur, tum birikime
        // geri DONMEZ. Donseydi varligi silmek ilerlemeyi yukseltirdi.
        val assignments = mapOf("silinmis" whollyIn "ev")
        assertEquals(0.0, goalWealth(ev, positions, assignments), EPS)
    }

    @Test
    fun atanmislarListelenir() {
        val assignments = mapOf("altin" whollyIn "ev", "euro" whollyIn "araba")
        val mine = assetsOf(ev, positions, assignments)

        assertEquals(listOf("altin"), mine.map { it.position.id })
    }

    @Test
    fun listeAtananKismiTasir() {
        // EKRAN YALAN SOYLEMESIN. Hedefe 15 ceyrek atanmisken liste "16 adet"
        // ve varligin tam degerini yaziyordu; gosterilen sey hedefin saydigi
        // seyden baskaydi.
        val ceyrek = position("ceyrek", value = 160_000.0, quantity = 16.0)
        val assignments = mapOf("ceyrek".partlyIn("ev", 15.0))

        val asset = assetsOf(ev, listOf(ceyrek), assignments).single()

        assertEquals(15.0, asset.quantity, EPS)
        assertEquals(150_000.0, asset.value, EPS)
        // Kismi: ekran "15 / 16" yazmali.
        assertEquals(false, asset.coversWholePosition)
    }

    @Test
    fun tumVarlikAtamasiKismiSayilmaz() {
        val ceyrek = position("ceyrek", value = 160_000.0, quantity = 16.0)
        val assignments = mapOf("ceyrek" whollyIn "ev")

        val asset = assetsOf(ev, listOf(ceyrek), assignments).single()

        assertEquals(16.0, asset.quantity, EPS)
        assertEquals(true, asset.coversWholePosition)
    }

    @Test
    fun miktarPozisyonaEsitseKismiSayilmaz() {
        // 16 atanmis, 16 var: "16 / 16" yazmak gurultu olurdu.
        val ceyrek = position("ceyrek", value = 160_000.0, quantity = 16.0)
        val assignments = mapOf("ceyrek".partlyIn("ev", 16.0))

        assertEquals(true, assetsOf(ev, listOf(ceyrek), assignments).single().coversWholePosition)
    }

    @Test
    fun atamaYoksaListeBos() {
        // Kati atama: atama yoksa hedefi olusturan varlik yok, ilerleme %0.
        assertTrue(assetsOf(ev, positions, emptyMap()).isEmpty())
    }

    @Test
    fun baskaHedefUyarisi() {
        val assignments = mapOf("altin" whollyIn "araba")

        assertEquals("araba", otherGoalOf("altin", ev, assignments))
        // Zaten bu hedefteyse uyari yok.
        assertNull(otherGoalOf("altin", araba, assignments))
        // Hic atanmamissa da yok.
        assertNull(otherGoalOf("euro", ev, assignments))
    }

    // --- Miktar tasiyan atama ------------------------------------------------

    @Test
    fun varliginYalnizAtananKismiSayilir() {
        // ASIL SENARYO: 11 ceyregin 10'u Ev'de, 1'i hedefsiz. Once atama tam
        // varliktiydi ve 1 tane hedefsiz eklemek 11'i birden Ev'den cikariyordu.
        val ceyrek = position("ceyrek", value = 110_000.0, quantity = 11.0)
        val assignments = mapOf("ceyrek".partlyIn("ev", 10.0))

        assertEquals(100_000.0, goalWealth(ev, listOf(ceyrek), assignments), EPS)
    }

    @Test
    fun atananMiktarVarligiAsamaz() {
        // EMNIYET KEMERI. Satis artik atamayi kalici dusuruyor; burasi geri
        // yukleme/esitleme gibi baska yollardan gelen tutarsizliga karsi.
        val ceyrek = position("ceyrek", value = 40_000.0, quantity = 4.0)
        val assignments = mapOf("ceyrek".partlyIn("ev", 10.0))

        assertEquals(40_000.0, goalWealth(ev, listOf(ceyrek), assignments), EPS)
    }

    @Test
    fun tumVarlikAtamasiMiktardanBagimsiz() {
        // -1 = tum varlik: sonradan alinan da hedefe sayilir. Eski atamalarin
        // ve hedef detayindaki "Varlik sec" listesinin anlami budur.
        val ceyrek = position("ceyrek", value = 110_000.0, quantity = 11.0)
        val assignments = mapOf("ceyrek" whollyIn "ev")

        assertEquals(110_000.0, goalWealth(ev, listOf(ceyrek), assignments), EPS)
    }

    @Test
    fun sifirAtamaSifirSayar() {
        val ceyrek = position("ceyrek", value = 110_000.0, quantity = 11.0)
        val assignments = mapOf("ceyrek".partlyIn("ev", 0.0))

        assertEquals(0.0, goalWealth(ev, listOf(ceyrek), assignments), EPS)
    }

    @Test
    fun miktarsizVarlikBolunemez() {
        // Tumu satilmis varlik: oran hesaplanamaz, sifira bolme olmamali.
        val bos = position("bos", value = 0.0, quantity = 0.0)
        val assignments = mapOf("bos".partlyIn("ev", 5.0))

        assertEquals(0.0, goalWealth(ev, listOf(bos), assignments), EPS)
    }

    // --- Kayit anindaki atama aritmetigi ------------------------------------

    @Test
    fun ayniHedefeEklemekMiktariArtirir() {
        // Ev'de 10 ceyrek varken 3 tane daha "Ev" ile eklendi.
        val current = GoalAssignment("ev", 10.0)
        val change = next(current, "ev", quantity = 3.0)!!

        assertEquals(13.0, change.quantity, EPS)
        // Geri alinabilir katki: kayit silinirse 3 geri verilir.
        assertEquals(3.0, change.delta, EPS)
    }

    @Test
    fun ayniHedeftenSatmakMiktariDusurur() {
        val current = GoalAssignment("ev", 10.0)
        val change = next(current, "ev", quantity = 4.0, isSell = true, before = 10.0)!!

        assertEquals(6.0, change.quantity, EPS)
        assertEquals(-4.0, change.delta, EPS)
    }

    @Test
    fun satisMiktariNegatifeInmez() {
        val current = GoalAssignment("ev", 2.0)
        val change = next(current, "ev", quantity = 5.0, isSell = true, before = 2.0)!!

        assertEquals(0.0, change.quantity, EPS)
        // Delta ATANAN kadar, satilan kadar degil: -5 yazilsa geri almada atama
        // 5'e diriltilirdi - oysa oraya hic 5 girmemisti.
        assertEquals(-2.0, change.delta, EPS)
    }

    @Test
    fun ilkAtamaIslemMiktariKadar() {
        val change = next(null, "ev", quantity = 10.0)!!

        assertEquals(10.0, change.quantity, EPS)
        assertEquals("ev", change.goalId)
    }

    @Test
    fun baskaHedefeTasimakMiktariSifirlar() {
        // Varlik Araba'daydi, bu kayit Ev'e sayiliyor: tasinir ve yalniz bu
        // islemin miktari Ev'e yazilir - eski hedefin miktari devralinmaz.
        val current = GoalAssignment("araba", 10.0)
        val change = next(current, "ev", quantity = 3.0)!!

        assertEquals("ev", change.goalId)
        assertEquals(3.0, change.quantity, EPS)
    }

    @Test
    fun tumVarlikAtamasiEklemedeKorunur() {
        // Kullanici bir kez "tamami bu hedefe" demisse, AYNI hedefe eklerken
        // soz korunur; miktara dusurmek onu bozardi.
        val current = GoalAssignment("ev")
        val change = next(current, "ev", quantity = 3.0, before = 15.0)!!

        assertEquals(GoalAssignment.WholePosition, change.quantity, EPS)
        // Sayisal katki yok: geri alinacak bir sey de yok.
        assertEquals(0.0, change.delta, EPS)
    }

    // --- SATIS SECICIYE BAKMAZ ----------------------------------------------

    @Test
    fun satistaBaskaHedefSecmekOHedefiARTIRMAZ() {
        // ASIL HATA. "Baska hedef" dali `isSell`i hic okumuyordu: bir SATIS
        // kaydinda Ev secmek Ev'in ilerlemesini satilan miktar kadar
        // ARTIRIYORDU. Satis ancak varligin icinde bulundugu hedeften duser.
        val current = GoalAssignment("araba", 10.0)
        val change = next(current, "ev", quantity = 4.0, isSell = true, before = 10.0)!!

        assertEquals("araba", change.goalId, "satis baska hedefe tasindi")
        assertEquals(6.0, change.quantity, EPS)
    }

    @Test
    fun atamasizVarliktaSatisAtamaURETMEZ() {
        // Hicbir hedefte olmayan varligi satarken hedef secmek atama yaratmaz.
        assertNull(next(null, "ev", quantity = 4.0, isSell = true, before = 10.0))
    }

    @Test
    fun tumVarlikAtamasindaSatisAtamayaDokunmaz() {
        // -1 zaten pozisyonu takip ediyor; dusulecek bir sayi yok.
        assertNull(next(GoalAssignment("ev"), "ev", quantity = 4.0, isSell = true, before = 10.0))
    }

    @Test
    fun hedefsizSatisDaAtamayiDUSURUR() {
        // MONOTONLUK. Once satis atamaya dokunmuyor, kirpma okuma aninda
        // yapiliyordu: 10 atanmisken 6 satilinca hedef 6 sayiyor, sonra 4 tane
        // daha alininca yeniden 10 saymaya basliyordu - satilanlar geri
        // geliyordu. Satis artik secici ne derse desin atamayi duser.
        val current = GoalAssignment("ev", 10.0)
        val change = next(current, null, quantity = 6.0, isSell = true, before = 10.0)!!

        assertEquals("ev", change.goalId)
        assertEquals(4.0, change.quantity, EPS)
    }

    // --- "Hedefsiz" ---------------------------------------------------------

    @Test
    fun hedefsizAlimTumVarlikAtamasiniDondurur() {
        // SAHADA CIKAN HATA. Eski atamalarin hepsi -1 (tum varlik). Kullanici
        // 15 ceyregi Ev'deyken 1 tane "Hedefsiz" ekliyor, hedef 16 gosteriyordu:
        // "hepsi" dedigi surece yeni alinan da sayiliyor. Artik atama o ana
        // kadarki miktara sabitlenir.
        val current = GoalAssignment("ev")

        assertEquals(15.0, next(current, null, quantity = 1.0, before = 15.0)!!.quantity, EPS)
    }

    @Test
    fun hedefsizAlimMiktarliAtamayaDokunmaz() {
        // Miktari zaten belli: yeni alim nasilsa sayilmiyor, yazmaya gerek yok.
        val current = GoalAssignment("ev", 10.0)

        assertNull(next(current, null, quantity = 1.0, before = 10.0))
    }

    @Test
    fun hedefsizAlimAtamasizVarligaDokunmaz() {
        assertNull(next(null, null, quantity = 1.0, before = 0.0))
    }

    @Test
    fun hedefsizSatisTumVarlikAtamasiniDONDURMEZ() {
        // "Tum varlik" atamasinda satis dondurma yapmaz: pozisyon kuculuyor ve
        // atama onu zaten takip ediyor.
        val current = GoalAssignment("ev")

        assertNull(next(current, null, quantity = 1.0, isSell = true, before = 15.0))
    }

    // --- Geri alma (silme / duzenleme) --------------------------------------

    @Test
    fun silinenAlimAtamadanDUSULUR() {
        // 3'luk alim Ev'e sayilmisti (atama 13); kayit silinince 10'a doner.
        val current = GoalAssignment("ev", 13.0)

        assertEquals(10.0, revertedAssignmentQuantity(current, "ev", delta = 3.0)!!, EPS)
    }

    @Test
    fun silinenSatisAtamayaGERIEklenir() {
        // ASIL HATA. Duzenleme once yeni kaydi yazip sonra eskisini siliyordu;
        // eski kaydin katkisi geri alinmadigi icin 4'luk bir satisi 3'e cekmek
        // atamayi 10 -> 6 -> 3 yapiyor, hedef kalici olarak eksik sayiyordu.
        val current = GoalAssignment("ev", 3.0)

        assertEquals(7.0, revertedAssignmentQuantity(current, "ev", delta = -4.0)!!, EPS)
    }

    @Test
    fun katkisizKayitGeriAlmaYAPMAZ() {
        val current = GoalAssignment("ev", 10.0)

        assertNull(revertedAssignmentQuantity(current, goalId = null, delta = 0.0))
        assertNull(revertedAssignmentQuantity(current, "ev", delta = 0.0))
    }

    @Test
    fun varlikBaskaHedefeTASINDIYSAGeriAlmaYok() {
        // Ev'e sayilan kayit siliniyor ama varlik artik Araba'da: Araba'nin
        // rakamini kurcalamak yeni bir yalan olurdu.
        val current = GoalAssignment("araba", 10.0)

        assertNull(revertedAssignmentQuantity(current, "ev", delta = 3.0))
    }

    @Test
    fun tumVarlikAtamasindaGeriAlmaYok() {
        assertNull(revertedAssignmentQuantity(GoalAssignment("ev"), "ev", delta = 3.0))
    }

    @Test
    fun geriAlmaNegatifeInmez() {
        val current = GoalAssignment("ev", 2.0)

        assertEquals(0.0, revertedAssignmentQuantity(current, "ev", delta = 5.0)!!, EPS)
    }

    /**
     * SATIS, DOKUNMADIGI BIRIMLERI HEDEFTEN SILMEZ.
     *
     * Siliyordu: kural `min(satilan, atanan)` kadar dusuruyordu ve satisin hic
     * dokunmadigi birimleri de goturuyordu. 10 ceyregin 4'u Ev'e atanmisken
     * atanmamis 6 tanesini satmak atamayi SIFIRLIYORDU - oysa elde tam da soz
     * verilen 4 ceyrek duruyor.
     */
    @Test
    fun satisATANMAMISIBirimlereDokunmaz() {
        val current = GoalAssignment("ev", 4.0)

        // 10 ceyregin atanmamis 6 tanesi satildi: geriye 4 kaliyor, hepsi Ev'de.
        assertNull(next(current, null, quantity = 6.0, isSell = true, before = 10.0))
    }

    @Test
    fun satisATANANAGirdigiKadarDusurur() {
        val current = GoalAssignment("ev", 8.0)
        // 6 satildi: 2 atanmamis + 4 atanan. Geriye 4 kaliyor.
        val change = next(current, null, quantity = 6.0, isSell = true, before = 10.0)!!

        assertEquals(4.0, change.quantity, EPS)
        assertEquals(-4.0, change.delta, EPS)
    }

    /**
     * Duzenlemenin sirasi ONEMLIDIR - bir zamanlar burada aksini iddia eden bir
     * test vardi. Satis dali elde kalana kirptigi icin katki o anki duruma
     * bagli; dogru sira "once geri al, sonra hesapla" ve bu artik depoda tek
     * yazmada yapiliyor (bkz. TransactionEditTest).
     */
}

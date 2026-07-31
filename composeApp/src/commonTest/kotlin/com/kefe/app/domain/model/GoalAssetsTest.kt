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

        assertEquals(listOf("altin"), mine.map { it.id })
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
        // 10 atanmisken 6 tanesi satildi: hedef 6 sayar. Kirpma okuma aninda -
        // satisin hangi hedeften dustugune dair ikinci bir defter tutulmuyor.
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

        assertEquals(
            13.0,
            nextAssignedQuantity(current, "ev", transactionQuantity = 3.0, isSell = false),
            EPS,
        )
    }

    @Test
    fun ayniHedeftenSatmakMiktariDusurur() {
        val current = GoalAssignment("ev", 10.0)

        assertEquals(
            6.0,
            nextAssignedQuantity(current, "ev", transactionQuantity = 4.0, isSell = true),
            EPS,
        )
    }

    @Test
    fun satisMiktariNegatifeInmez() {
        val current = GoalAssignment("ev", 2.0)

        assertEquals(
            0.0,
            nextAssignedQuantity(current, "ev", transactionQuantity = 5.0, isSell = true),
            EPS,
        )
    }

    @Test
    fun ilkAtamaIslemMiktariKadar() {
        assertEquals(
            10.0,
            nextAssignedQuantity(null, "ev", transactionQuantity = 10.0, isSell = false),
            EPS,
        )
    }

    @Test
    fun baskaHedefeTasimakMiktariSifirlar() {
        // Varlik Araba'daydi, bu kayit Ev'e sayiliyor: tasinir ve yalniz bu
        // islemin miktari Ev'e yazilir - eski hedefin miktari devralinmaz.
        val current = GoalAssignment("araba", 10.0)

        assertEquals(
            3.0,
            nextAssignedQuantity(current, "ev", transactionQuantity = 3.0, isSell = false),
            EPS,
        )
    }

    @Test
    fun tumVarlikAtamasiEklemedeKorunur() {
        // Kullanici bir kez "tamami bu hedefe" demisse sonraki alimlar da oraya
        // sayilir; miktara dusurmek o sozu bozardi.
        val current = GoalAssignment("ev")

        assertEquals(
            GoalAssignment.WholePosition,
            nextAssignedQuantity(current, "ev", transactionQuantity = 3.0, isSell = false),
            EPS,
        )
    }

    @Test
    fun kismiAtamaListedeYineDeGorunur() {
        // Varligin bir kismi hedefteyse "karsilayanlar" listesinde durur -
        // kullanici hangi varligin sayildigini gorebilmeli.
        val ceyrek = position("ceyrek", value = 110_000.0, quantity = 11.0)
        val assignments = mapOf("ceyrek".partlyIn("ev", 10.0))

        assertEquals(listOf("ceyrek"), assetsOf(ev, listOf(ceyrek), assignments).map { it.id })
    }
}

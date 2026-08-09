package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EPS = 1e-9

private fun position(
    id: String = "pos_gold_quarter",
    assetClass: AssetClass = AssetClass.Gold,
    subtype: GoldSubtype? = GoldSubtype.Quarter,
    karat: Karat? = null,
    quantity: Double = 3.0,
    unitPrice: Double = 10_000.0,
): Position = Position(
    id = id,
    name = "Çeyrek",
    assetClass = assetClass,
    subtype = subtype,
    karat = karat,
    quantity = quantity,
    unit = QuantityUnit.Piece,
    unitPrice = unitPrice,
    value = quantity * unitPrice,
    cost = quantity * unitPrice,
)

private val Today = KefeDate(2026, 7, 31)

private fun price(
    assetKey: String = "gold_quarter",
    bid: Double? = 9_850.0,
    ask: Double = 10_018.0,
    changePercent: Double = 1.5,
    weekChangePercent: Double? = null,
    monthChangePercent: Double? = null,
    quoteDate: KefeDate? = Today,
): Price = Price(
    assetKey = assetKey,
    label = "Çeyrek Altın",
    bid = bid,
    ask = ask,
    changePercent = changePercent,
    timestamp = "14:32",
    source = PriceSource.FreeMarket,
    assetClass = AssetClass.Gold,
    weekChangePercent = weekChangePercent,
    monthChangePercent = monthChangePercent,
    quoteDate = quoteDate,
)

class ValuationTest {

    // --- Hangi fiyat ---

    @Test
    fun degerlemeAlisTarafiniKullanir() {
        // "Elimdekini bugun satsam ne alirim" = kuyumcunun ALIS fiyati.
        assertEquals(9_850.0, price().sellPrice(), EPS)
    }

    @Test
    fun odenecekTutarSatisTarafi() {
        assertEquals(10_018.0, price().buyPrice(), EPS)
    }

    @Test
    fun alisKotasyonuYoksaTekFiyat() {
        // Fonda alis kotasyonu yoktur; iki taraf da ayni fiyattir.
        val fund = price(bid = null, ask = 42.0)
        assertEquals(42.0, fund.sellPrice(), EPS)
        assertEquals(42.0, fund.buyPrice(), EPS)
        assertNull(fund.spreadPercent())
    }

    @Test
    fun makasSatisFiyatininYuzdesi() {
        // 168 / 10.018 = %1,677 - payda SATIS fiyatidir, alis degil.
        assertEquals(168.0 / 10_018.0 * 100.0, price().spreadPercent()!!, EPS)
    }

    // --- Degerleme ---

    @Test
    fun degerGuncelFiyattanHesaplanir() {
        // Saklanan birim fiyat 10.000'di ve hic guncellenmiyordu.
        val valued = position().valuedAt(price(), Today)

        assertEquals(9_850.0, valued.unitPrice, EPS)
        assertEquals(29_550.0, valued.value, EPS)
        assertEquals(1.5, valued.dailyChangePercent, EPS)
    }

    @Test
    fun donemselDegisimlerTasinir() {
        val valued = position().valuedAt(
            price(weekChangePercent = 2.4, monthChangePercent = -3.1),
            Today,
        )

        assertEquals(2.4, valued.weekChangePercent!!, EPS)
        assertEquals(-3.1, valued.monthChangePercent!!, EPS)
    }

    @Test
    fun bilinmeyenDonemNullKalir() {
        // Gunlukten farkli: sifira dusurmek "degismedi" demek olurdu, oysa
        // dogrusu "bilmiyoruz" - ekran "—" yazmali.
        val valued = position().valuedAt(price(), Today)

        assertNull(valued.weekChangePercent)
        assertNull(valued.monthChangePercent)
    }

    @Test
    fun maliyetVeMiktarDokunulmaz() {
        // Ikisi de DEFTERDEN turer; fiyat onlari degistirmez.
        val original = position()
        val valued = original.valuedAt(price(), Today)

        assertEquals(original.cost, valued.cost, EPS)
        assertEquals(original.quantity, valued.quantity, EPS)
    }

    @Test
    fun fiyatYoksaSonBilinenDegerKorunur() {
        // Sifire dusurmek "varligin degeri yok" demek olurdu.
        val original = position()
        assertEquals(original, original.valuedAt(null, Today))
    }

    @Test
    fun sifirFiyatDegerlemeSayilmaz() {
        val original = position()
        assertEquals(original, original.valuedAt(price(bid = 0.0, ask = 0.0), Today))
    }

    // --- Gunluk degisim yalnizca BUGUNE aitse ---

    /**
     * ASIL HATA BUYDU. Borsa cuma kapaniyor; cumartesi okunan `changePercent`
     * cumanin hareketidir. Kullanicinin serveti cumartesi degismedi - varlik
     * cuma aksamindaki degerinde duruyor - dolayisiyla "bugunku getiri"ye
     * katkisi SIFIR olmali, cumanin yuzdesi degil.
     */
    @Test
    fun kapaliGunDegisimSIFIR() {
        val cuma = KefeDate(2026, 7, 31)
        val cumartesi = KefeDate(2026, 8, 1)

        val valued = position().valuedAt(price(quoteDate = cuma), cumartesi)

        assertEquals(0.0, valued.dailyChangePercent, EPS)
        // Deger yine de guncel fiyattan hesaplanir; sifirlanan yalniz DEGISIM.
        assertEquals(9_850.0, valued.unitPrice, EPS)
        assertEquals(29_550.0, valued.value, EPS)
    }

    @Test
    fun ayniGunDegisimAYNEN_gecer() {
        val valued = position().valuedAt(price(quoteDate = Today), Today)
        assertEquals(1.5, valued.dailyChangePercent, EPS)
    }

    /**
     * Gunu bilinmeyen kotasyon bugun SAYILMAZ: elle girilen fiyatin gunluk
     * hareketi yoktur, TCMB bulteninin hangi gune ait oldugunu bilmiyoruz.
     * Ikisinde de dogru cevap "bugun oynamadi".
     */
    @Test
    fun gunuBilinmeyenKotasyonBugunSAYILMAZ() {
        val valued = position().valuedAt(price(quoteDate = null), Today)
        assertEquals(0.0, valued.dailyChangePercent, EPS)
    }

    /**
     * Haftalik/aylik degisim bu kuraldan ETKILENMEZ: onlar gecmis serisinden
     * bir tolerans penceresiyle hesaplaniyor, yani kapali gunlere zaten
     * dayanikli. Sifirlansalardi hafta sonu butun donem rakamlari kaybolurdu.
     */
    @Test
    fun kapaliGunDonemselDegisimiBOZMAZ() {
        val valued = position().valuedAt(
            price(
                quoteDate = KefeDate(2026, 7, 31),
                weekChangePercent = 2.4,
                monthChangePercent = -3.1,
            ),
            KefeDate(2026, 8, 2),
        )

        assertEquals(0.0, valued.dailyChangePercent, EPS)
        assertEquals(2.4, valued.weekChangePercent!!, EPS)
        assertEquals(-3.1, valued.monthChangePercent!!, EPS)
    }

    /**
     * Gun DONDUGUNDE de sifirlanmali: uygulama gece boyunca acik kalirsa dunun
     * degisimi "bugun" olarak asili kalirdi.
     */
    @Test
    fun gunDonuncePLAKA_dusar() {
        val dun = KefeDate(2026, 7, 30)
        val bugun = KefeDate(2026, 7, 31)
        assertEquals(1.5, price(quoteDate = bugun).todayChangePercent(bugun), EPS)
        assertEquals(0.0, price(quoteDate = dun).todayChangePercent(bugun), EPS)
    }

    // --- Anahtar cozumleme ---

    @Test
    fun altinAltTurundenAnahtar() {
        assertEquals("gold_quarter", position().priceKey())
        assertEquals("gold_gram", position(subtype = GoldSubtype.Gram).priceKey())
        assertEquals("gold_ata", position(subtype = GoldSubtype.Ata).priceKey())
    }

    @Test
    fun bilezikAyardanAnahtar() {
        val jewelry = position(subtype = GoldSubtype.Jewelry, karat = Karat.K18)
        assertEquals("gold_k18", jewelry.priceKey())
    }

    @Test
    fun gramAyardanAnahtar() {
        // ASIL SIKAYET: 22 ayar gram altini olan biri onu ancak "Bilezik/Takı"
        // diye kaydedebiliyordu. Artik gram formunda da ayar seciliyor.
        assertEquals(
            "gold_k22",
            position(subtype = GoldSubtype.Gram, karat = Karat.K22).priceKey(),
        )
        assertEquals(
            "gold_k14",
            position(subtype = GoldSubtype.Gram, karat = Karat.K14).priceKey(),
        )
    }

    @Test
    fun ayarsizEskiGramKaydiDegismez() {
        // Eski kayitlarda ayar kolonu BOS. Varsayilan 24 ve 24 ayar zaten
        // gold_gram'a duser - mevcut pozisyonlarin fiyati zerre degismemeli.
        assertEquals("gold_gram", position(subtype = GoldSubtype.Gram, karat = null).priceKey())
        assertEquals(
            "gold_gram",
            position(subtype = GoldSubtype.Gram, karat = Karat.K24).priceKey(),
        )
    }

    @Test
    fun adetleSatilanFormlarAyarSormaz() {
        // Ceyrek/yarim/tam/ata hep 22 ayardir; ayar sormak yanlis bir secim
        // imkani acardi. Has/Kulce zaten saf altin.
        assertFalse(GoldSubtype.Quarter.usesKarat())
        assertFalse(GoldSubtype.Ata.usesKarat())
        assertFalse(GoldSubtype.Bullion.usesKarat())
        assertTrue(GoldSubtype.Gram.usesKarat())
        assertTrue(GoldSubtype.Jewelry.usesKarat())
        assertEquals(Karat.K24, GoldSubtype.Gram.defaultKarat())
        assertEquals(Karat.K22, GoldSubtype.Jewelry.defaultKarat())
    }

    @Test
    fun ayarPozisyonuAnahtardanAYIRIR() {
        // 22 ayar gram ile 24 ayar gram AYRI varliklardir: ayni anahtara
        // dusseler biri digerinin fiyatiyla degerlenirdi.
        val k22 = position(subtype = GoldSubtype.Gram, karat = Karat.K22).priceKey()
        val k24 = position(subtype = GoldSubtype.Gram, karat = Karat.K24).priceKey()
        assertTrue(k22 != k24, "22 ve 24 ayar ayni anahtara dusuyor: $k22")
    }

    /**
     * Has/Kulce KENDI kotasyonundan degerlenir.
     *
     * Once gram anahtarina dusuyordu; tahtada iki fiyat farkli (9 Agustos 2026:
     * Has ₺6.627,24, gram ₺6.660,55) ve Has tutan bir pozisyon yaklasik %0,5
     * fazla degerleniyordu. Kaynak Has satirini zaten cekiyor.
     */
    @Test
    fun hasKulceKendiKotasyonundanDEGERLENIR() {
        val bullion = position(subtype = GoldSubtype.Bullion).priceKey()
        val gram = position(subtype = GoldSubtype.Gram, karat = Karat.K24).priceKey()
        assertEquals("gold_bullion", bullion)
        assertTrue(bullion != gram, "Has/Kulce gram anahtarina dusuyor: $bullion")
    }

    /** Has/Kulce fiyati tahtadan gercekten okunabilmeli - anahtar kaynakla eslesir. */
    @Test
    fun hasKulceFiyatiTAHTADANOkunur() {
        val has = price(assetKey = "gold_bullion", bid = 6_626.39, ask = 6_627.24)
        val valued = position(subtype = GoldSubtype.Bullion, quantity = 10.0)
            .valuedAt(has, Today)
        // Satis tarafindan: 10 gr x 6.626,39
        assertEquals(66_263.9, valued.value, 1e-6)
    }

    @Test
    fun fonAnahtariKimlikten() {
        // Fon kodu baska hicbir alanda tasinmiyor.
        val fund = position(id = "pos_fund_afa", assetClass = AssetClass.Fund, subtype = null)
        assertEquals("fund_afa", fund.priceKey())
    }

    @Test
    fun nakitAnahtarsiz() {
        // Nakdin birim fiyati yoktur; girilen tutar dogrudan degerdir.
        val cash = position(id = "pos_cash", assetClass = AssetClass.Cash, subtype = null)
        assertNull(cash.priceKey())
    }

    @Test
    fun gumusSabitAnahtar() {
        val silver = position(id = "pos_silver_gram", assetClass = AssetClass.Silver, subtype = null)
        assertEquals("silver_gram", silver.priceKey())
    }
}

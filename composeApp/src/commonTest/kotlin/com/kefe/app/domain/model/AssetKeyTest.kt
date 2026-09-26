package com.kefe.app.domain.model

import com.kefe.app.data.remote.stockAssetKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pozisyonun kimlik anahtari - aylik planin alimlari satirlara eslestirdigi olcu.
 *
 * Tehlike eski kayitlarda: `pos_ceyrek`, `pos_afa`, `pos_usd`, `pos_vadeli` gibi
 * kimlikler "pos_<anahtar>" sozlesmesine uymuyor. Anahtar kimlikten okunsaydi
 * bu varliklarin alimlari hicbir plan satirina sayilmaz, hepsi "plan disi"
 * gorunurdu. Anahtar alanlardan turer; burada o turetmenin eski ve yeni
 * kayitlarda AYNI anahtari verdigi sabitleniyor.
 */
class AssetKeyTest {

    @Test
    fun eskiCeyrekKimligiYeniCeyrekleAYNIAnahtar() {
        val legacy = pos("pos_ceyrek", "Çeyrek Altın", AssetClass.Gold, GoldSubtype.Quarter)
        val modern = pos("pos_gold_quarter", "Çeyrek", AssetClass.Gold, GoldSubtype.Quarter)
        assertEquals("gold_quarter", legacy.assetKey())
        assertEquals(legacy.assetKey(), modern.assetKey())
    }

    /** Ayari bos eski gram kaydi 24 ayar sayilir - islem ekleme ekrani da oyle eslestiriyor. */
    @Test
    fun ayariBosGramAltinYIRMIDORTAyarSayilir() {
        val legacy = pos("pos_gold_gram", "Gram", AssetClass.Gold, GoldSubtype.Gram, karat = null)
        assertEquals("gold_gram", legacy.assetKey())
    }

    @Test
    fun yirmiIkiAyarGramAyriAnahtar() {
        val k22 = pos("pos_gold_k22", "Gram", AssetClass.Gold, GoldSubtype.Gram, Karat.K22)
        assertEquals("gold_k22", k22.assetKey())
    }

    /** Ayni ayardaki iki takı satiri ayni varliktir - ikisinin alimi da sayilir. */
    @Test
    fun ayniAyarIkiTakiAYNIAnahtar() {
        val a = pos("pos_bilezik22", "22 Ayar Bilezik", AssetClass.Gold, GoldSubtype.Jewelry, Karat.K22)
        val b = pos("pos_anneanne", "Anneannemin Bileziği", AssetClass.Gold, GoldSubtype.Jewelry, Karat.K22)
        assertEquals("gold_jewelry_916", a.assetKey())
        assertEquals(a.assetKey(), b.assetKey())
    }

    @Test
    fun altTuruBosAltinESLESMEZ() {
        assertNull(pos("pos_eski", "Altın", AssetClass.Gold, subtype = null).assetKey())
    }

    @Test
    fun eskiFonKimligiAdindakiKoddanCozulur() {
        val legacy = pos("pos_afa", "AFA · Ak Portföy Altın", AssetClass.Fund)
        val modern = pos("pos_fund_afa", "AFA · Ak Portföy Altın", AssetClass.Fund)
        assertEquals("fund_afa", legacy.assetKey())
        assertEquals(legacy.assetKey(), modern.assetKey())
    }

    /** Kod bicimine uymayan serbest ad anahtar URETMEZ - iki fonu birlestirebilirdi. */
    @Test
    fun kodsuzEskiFonESLESMEZ() {
        assertNull(pos("pos_x", "Benim fonum", AssetClass.Fund).assetKey())
    }

    @Test
    fun hisseAnahtariFiyatKaynagininAnahtariylaAYNI() {
        val modern = pos("pos_stock_thyao.is", "THYAO.IS · Türk Hava Yolları", AssetClass.Stock)
        val upper = pos("pos_stock_THYAO.IS", "THYAO.IS · Türk Hava Yolları", AssetClass.Stock)
        assertEquals(stockAssetKey("THYAO.IS"), modern.assetKey())
        assertEquals(stockAssetKey("THYAO.IS"), upper.assetKey())
    }

    @Test
    fun eskiDovizKimligiParaBirimineCozulur() {
        assertEquals("usd_try", pos("pos_usd", "Amerikan Doları", AssetClass.Fx).assetKey())
        assertEquals("eur_try", pos("pos_eur_try", "Euro", AssetClass.Fx).assetKey())
    }

    /** Vadeli ve vadesiz ayni "nakit"tir - islem ekleme ekrani da nakdi tek varlik sayiyor. */
    @Test
    fun butunNakitSatirlariTEKAnahtar() {
        assertEquals(CashAssetKey, pos("pos_vadeli", "Vadeli Mevduat", AssetClass.Cash).assetKey())
        assertEquals(CashAssetKey, pos("pos_vadesiz", "Vadesiz Hesap", AssetClass.Cash).assetKey())
    }

    @Test
    fun gumusTekAnahtar() {
        assertEquals(SilverAssetKey, pos("pos_gumus", "Gram Gümüş", AssetClass.Silver).assetKey())
    }

    /** Her altin (form, ayar) cifti anahtardan AYNI forma geri cozulur. */
    @Test
    fun altinAnahtariGidisDonus() {
        GoldSubtype.entries.forEach { subtype ->
            Karat.entries.forEach { karat ->
                val key = goldAssetKey(subtype, karat)
                val info = parseAssetKey(key)!!
                assertEquals(AssetClass.Gold, info.assetClass, key)
                assertEquals(subtype, info.goldSubtype, key)
                if (subtype.usesKarat()) assertEquals(karat, info.karat, key)
            }
        }
    }

    @Test
    fun birimAnahtardanCozulur() {
        assertEquals(QuantityUnit.Gram, parseAssetKey("gold_k22")!!.unit)
        assertEquals(QuantityUnit.Piece, parseAssetKey("gold_quarter")!!.unit)
        assertEquals(QuantityUnit.Gram, parseAssetKey(SilverAssetKey)!!.unit)
        assertEquals(QuantityUnit.Share, parseAssetKey("fund_afa")!!.unit)
        assertEquals(QuantityUnit.Lot, parseAssetKey("stock_thyao.is")!!.unit)
        assertEquals(QuantityUnit.Currency, parseAssetKey("usd_try")!!.unit)
        assertEquals(QuantityUnit.Currency, parseAssetKey(CashAssetKey)!!.unit)
        assertNull(parseAssetKey("bilinmeyen"))
    }

    /** Anahtarin fiyat karsiligi, ayni varligin pozisyonunun fiyat anahtariyla ayni. */
    @Test
    fun fiyatAnahtariPozisyonunkiyleTUTARLI() {
        val positions = listOf(
            pos("pos_gold_gram", "Gram", AssetClass.Gold, GoldSubtype.Gram, Karat.K24),
            pos("pos_gold_k22", "Gram", AssetClass.Gold, GoldSubtype.Gram, Karat.K22),
            pos("pos_gold_bullion", "Has", AssetClass.Gold, GoldSubtype.Bullion),
            pos("pos_gold_quarter", "Çeyrek", AssetClass.Gold, GoldSubtype.Quarter),
            pos("pos_gold_jewelry_585", "14 ayar Takı", AssetClass.Gold, GoldSubtype.Jewelry, Karat.K14),
            pos("pos_silver_gram", "Gram Gümüş", AssetClass.Silver),
            pos("pos_usd_try", "Amerikan Doları", AssetClass.Fx),
            pos("pos_fund_afa", "AFA · Ak", AssetClass.Fund),
            pos("pos_cash", "Nakit", AssetClass.Cash),
        )
        positions.forEach { p ->
            assertEquals(p.priceKey(), priceKeyOfAsset(p.assetKey()!!), p.id)
        }
    }

    @Test
    fun katalogAdlari() {
        assertEquals("Gram Altın", catalogName("gold_gram"))
        assertEquals("22 ayar Gram Altın", catalogName("gold_k22"))
        assertEquals("Çeyrek Altın", catalogName("gold_quarter"))
        assertEquals("Gram Gümüş", catalogName(SilverAssetKey))
        assertEquals("AFA", catalogName("fund_afa"))
        assertEquals("Nakit", catalogName(CashAssetKey))
    }
}

private fun pos(
    id: String,
    name: String,
    assetClass: AssetClass,
    subtype: GoldSubtype? = null,
    karat: Karat? = null,
) = Position(
    id = id,
    name = name,
    assetClass = assetClass,
    subtype = subtype,
    karat = karat,
    quantity = 1.0,
    unit = QuantityUnit.Gram,
    unitPrice = 1.0,
    value = 1.0,
    cost = 1.0,
)

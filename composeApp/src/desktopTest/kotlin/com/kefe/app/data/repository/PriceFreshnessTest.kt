package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PriceRemoteDataSource
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.SampleToday
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.RefreshOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Çevrimdışı" NE ZAMAN yazilir.
 *
 * Bu kural ekranda tek bir noktaya bakarak dogrulanamaz: uygulama acilir acilmaz
 * gecen kareler, agin gercekten kopuk olmasi ve onbellegin yasi birbirine
 * karisiyor. Kullanicinin "hep hata veriyor gibi" dedigi sey de tam buydu -
 * istekler basariyla donerken ekran cevrimdisi yaziyordu.
 *
 * Kural tek cumle: cevrimdisi ancak GERCEKTEN DENENIP BASARISIZ olunca soylenir.
 */
private class FakeRemote(
    var fail: Boolean = false,
    var prices: List<Price> = listOf(gramGold()),
) : PriceRemoteDataSource {

    var calls = 0
        private set

    override suspend fun fetchPrices(): List<Price> {
        calls++
        if (fail) throw IllegalStateException("ag yok")
        return prices
    }
}

private fun gramGold(bid: Double = 6000.0) = Price(
    assetKey = "gold_gram",
    label = "Gram Altın",
    bid = bid,
    ask = bid + 50.0,
    changePercent = 0.0,
    timestamp = "12:00",
    source = PriceSource.FreeMarket,
    assetClass = AssetClass.Gold,
)

private fun usd(bid: Double = 40.0) = Price(
    assetKey = "fx_usd",
    label = "USD/TRY",
    bid = bid,
    ask = bid + 0.1,
    changePercent = 0.0,
    timestamp = "TCMB",
    source = PriceSource.FreeMarket,
    assetClass = AssetClass.Fx,
)

/** Saat bir saniyeyle degil milisaniyeyle ilerler; kolayligi icin sarmalanir. */
private fun clockAt(seconds: Long) = FixedKefeClock(millis = seconds * 1000L)

/** Ilerletilebilir saat: kisitlama penceresinin gectigi anlar sinanabilsin. */
private class MovingClock(var seconds: Long) : KefeClock {
    override fun today(): KefeDate = SampleToday
    override fun nowEpochMillis(): Long = seconds * 1000L
}

private fun newRepository(
    remote: FakeRemote,
    nowSeconds: Long,
): Pair<SqlDelightPriceRepository, KefeDatabase> {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    KefeDatabase.Schema.create(driver)
    val database = createKefeDatabase(driver)
    return SqlDelightPriceRepository(database, remote, clockAt(nowSeconds)) to database
}

class PriceFreshnessTest {

    @Test
    fun `kismi cekim tabloyu kirpmaz`() = runTest {
        // ASIL TUZAK. Kaynaklar birbirinden bagimsiz hale gelince (serbest piyasa
        // dusse de TCMB/TEFAS gelsin) kismi bir cekim mumkun oldu. Oturum
        // sonucu onbellegin YERINE gecseydi, altin satirlari tahtadan duser ve
        // toplam birikim bir anda ucuruma inerdi - emulatorde ₺946k'dan ₺693k'ya
        // dusen rakam tam olarak buydu.
        val remote = FakeRemote(prices = listOf(gramGold(6_000.0), usd(40.0)))
        // Saat ILERLEYEBILIR olmali: sabit saatte ikinci yenileme kisitlamaya
        // takilir ve aga hic cikmaz.
        val clock = MovingClock(seconds = 10_000L)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val repo = SqlDelightPriceRepository(createKefeDatabase(driver), remote, clock)

        repo.refresh()
        assertEquals(2, repo.observePrices().first().prices.size)

        // Ikinci turda serbest piyasa dustu: yalniz doviz geldi.
        remote.prices = listOf(usd(41.0))
        clock.seconds += 600L
        repo.refresh()

        val board = repo.observePrices().first()
        // Altin HALA tahtada (son bilinen fiyatiyla), doviz tazelendi.
        assertEquals(6_000.0, board.byKey("gold_gram")?.bid)
        assertEquals(41.0, board.byKey("fx_usd")?.bid)
        assertEquals(2, board.prices.size)
    }

    @Test
    fun `tek kaynak gelse de cevrimdisi yazilmaz`() = runTest {
        val remote = FakeRemote(prices = listOf(usd(40.0)))
        val (repo, _) = newRepository(remote, nowSeconds = 10_000L)

        repo.refresh()

        // Elde fiyat var ve deneme patlamadi: cevrimdisi degiliz. Once serbest
        // piyasa tek basina yenilemeyi dusurdugu icin bu hal "Çevrimdışı" idi.
        assertEquals(PriceFreshness.Fresh, repo.observePrices().first().freshness)
    }

    @Test
    fun `hic fiyat yokken ve deneme yapilmamisken yukleniyor`() = runTest {
        val (repo, _) = newRepository(FakeRemote(), nowSeconds = 10_000L)

        // Onemli olan: Offline DEGIL. Bos onbellek "ag yok" demek degildir,
        // ilk cekme daha yolda olabilir - uygulama her acilista bir an bu
        // durumdan geciyor ve "Çevrimdışı" yaziyordu.
        assertEquals(PriceFreshness.Loading, repo.observePrices().first().freshness)
    }

    @Test
    fun `hic fiyat yok ve deneme patladiysa cevrimdisi`() = runTest {
        val remote = FakeRemote(fail = true)
        val (repo, _) = newRepository(remote, nowSeconds = 10_000L)

        repo.refresh()

        assertEquals(PriceFreshness.Offline, repo.observePrices().first().freshness)
    }

    @Test
    fun `basarili cekmeden sonra taze`() = runTest {
        val (repo, _) = newRepository(FakeRemote(), nowSeconds = 10_000L)

        repo.refresh()

        assertEquals(PriceFreshness.Fresh, repo.observePrices().first().freshness)
    }

    @Test
    fun `elde taze fiyat olsa bile deneme patladiysa cevrimdisi`() = runTest {
        val remote = FakeRemote()
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val clock = MovingClock(10_000L)
        val repo = SqlDelightPriceRepository(createKefeDatabase(driver), remote, clock)
        repo.refresh()

        // Saat kisitlama penceresinin OTESINE alinir: hemen ardindan yapilan
        // yenileme zaten aga cikmaz ve ag koptugunu bilemezdi.
        clock.seconds += 60L
        // Ag koptu; elimizdeki rakamlar duruyor ama kullanici bagli olmadigimizi
        // bilmeli - yoksa ekrandaki fiyati anlik saniyor.
        remote.fail = true
        repo.refresh()

        assertEquals(PriceFreshness.Offline, repo.observePrices().first().freshness)
    }

    @Test
    fun `kisitlama penceresi icinde ag koptugu ERKEN soylenmez`() = runTest {
        val remote = FakeRemote()
        val (repo, _) = newRepository(remote, nowSeconds = 10_000L)
        repo.refresh()

        // Saat ilerlemedi: elimizdeki fiyat saniyeler oncesine ait. Bu araligi
        // aga cikmadan geciyoruz, dolayisiyla "cevrimdisi" diyecek bilgimiz de
        // yok - ve olsa bile soylemek erken olurdu.
        remote.fail = true
        repo.refresh()

        assertEquals(1, remote.calls)
        assertEquals(PriceFreshness.Fresh, repo.observePrices().first().freshness)
    }

    @Test
    fun `iki saatten eski onbellek eski sayilir`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val database = createKefeDatabase(driver)

        // Fiyat 10.000'de cekildi, saat 20.000 - arada 10.000 sn (~2,8 saat).
        val writer = SqlDelightPriceRepository(database, FakeRemote(), clockAt(10_000L))
        writer.refresh()

        val reader = SqlDelightPriceRepository(database, FakeRemote(), clockAt(20_000L))
        assertEquals(PriceFreshness.Stale, reader.observePrices().first().freshness)
    }

    @Test
    fun `arka arkaya yenileme kaynagi tekrar tekrar cagirmaz`() = runTest {
        val remote = FakeRemote()
        val (repo, _) = newRepository(remote, nowSeconds = 10_000L)

        val first = repo.refresh()
        val second = repo.refresh()

        // Saat sabit, yani ikisi ayni saniyede: ilki cikar, ikincisi onbellekle
        // yetinir. Once her dokunus ayri istek aciyordu ve ust uste binen
        // cagrilar kaynagi susturuyordu.
        assertEquals(1, remote.calls)

        // SONUC AYIRT EDILEBILIR OLMALI. Ikisi de Result.success donuyordu ve
        // kisitlanan yenileme ekranda hicbir iz birakmiyordu: kullanicinin
        // gordugu, dokundugu ama hicbir sey olmayan bir dugmeydi.
        assertEquals(RefreshOutcome.Fetched, first.getOrNull())
        val outcome = second.getOrNull()
        assertTrue(outcome is RefreshOutcome.Throttled, "ikinci cagri kisitlanmali")
        // Kalan sure kullaniciya yazilacak; sifir olamaz.
        assertTrue(outcome.retryInSeconds in 1..30)
    }

    @Test
    fun `basarisizliktan sonra yenileme YENIDEN dener`() = runTest {
        val remote = FakeRemote(fail = true)
        val (repo, _) = newRepository(remote, nowSeconds = 10_000L)

        repo.refresh()
        remote.fail = false
        val second = repo.refresh()

        // Kisitlama yalniz BASARILI cekmeden sonra isler. Aksi halde ag geri
        // gelince yenileye basan kullanici otuz saniye sessizce reddedilir,
        // ustelik "basarili" cevabi alirdi.
        assertEquals(2, remote.calls)
        assertTrue(second.isSuccess)
        assertEquals(PriceFreshness.Fresh, repo.observePrices().first().freshness)
    }
}

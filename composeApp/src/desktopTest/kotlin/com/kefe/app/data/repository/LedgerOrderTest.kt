package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.costBasis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Varlik detayinin aldigi defter, SAKLANAN pozisyonla ayni rakami vermeli.
 *
 * Vermiyordu. Defteri okuyan iki sorgu vardi: biri ekran icin (tarih DESC,
 * UUID), biri hesap icin (kronolojik). costBasis() defteri kararli siralar,
 * yani ayni tarihli kayitlar geldikleri sirada islenir - ekran sirasinda o sira
 * UUID sirasidir. Ayni gun once alip sonra satan kullanicida, satisin UUID'si
 * alimdan kucukse costBasis satisi "elde miktar yok" diye TUMDEN atliyordu.
 *
 * Depo dogru sorguyu kullaniyordu ama varlik detayi ekrani yanlis olani
 * cagiriyordu: liste ekrani ile detay ekrani AYNI defterden farkli rakam
 * gosteriyordu. Iki sorgudan biri tuzak oldugu surece bu yeniden olurdu, o
 * yuzden ekran sorgusu kaldirildi.
 *
 * UUID'ler bilerek TERS secildi: satisin kimligi alimdan KUCUK, yani eski ekran
 * sirasinda satis basa gecerdi.
 */
class LedgerOrderTest {

    private val gun = KefeDate(2026, 8, 3)

    private fun newRepository(): SqlDelightPortfolioRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        return SqlDelightPortfolioRepository(
            createKefeDatabase(driver),
            FixedKefeClock(millis = 1_000L),
            NoPrices(),
        )
    }

    private fun quarter() = Position(
        id = "pos_gold_quarter",
        name = "Çeyrek",
        assetClass = AssetClass.Gold,
        subtype = GoldSubtype.Quarter,
        quantity = 0.0,
        unit = QuantityUnit.Piece,
        unitPrice = 10_000.0,
        value = 0.0,
        cost = 0.0,
    )

    private fun tx(id: String, side: TradeSide, quantity: Double, unitPrice: Double) = Transaction(
        id = id,
        positionId = "pos_gold_quarter",
        date = gun,
        side = side,
        quantity = quantity,
        unitPrice = unitPrice,
        addedByMemberId = "member_owner",
    )

    /** Ayni gun: once 2 adet @10.000 AL, sonra 1 adet @15.000 SAT. */
    private suspend fun SqlDelightPortfolioRepository.seedSameDayBuyThenSell() {
        upsertPosition(quarter())
        addTransaction(tx("zzz-alis", TradeSide.Buy, 2.0, 10_000.0))
        addTransaction(tx("aaa-satis", TradeSide.Sell, 1.0, 15_000.0))
    }

    /** Defter KRONOLOJIK gelir: alis once, satis sonra. */
    @Test
    fun defterKRONOLOJIKGelir() = runTest {
        val repo = newRepository()
        repo.seedSameDayBuyThenSell()

        val ledger = repo.observeTransactions("pos_gold_quarter").first()
        assertEquals(listOf("zzz-alis", "aaa-satis"), ledger.map { it.id })
    }

    /**
     * ASIL KURAL: detay ekraninin defterinden hesaplanan maliyet, saklanan
     * pozisyonla AYNI olmali. Eski sirada satis atlanip miktar 2 kaliyordu.
     */
    @Test
    fun detayDefteriPOZISYONLAAyniSonucuVerir() = runTest {
        val repo = newRepository()
        repo.seedSameDayBuyThenSell()

        val basis = repo.observeTransactions("pos_gold_quarter").first().costBasis()
        val position = repo.observePositions().first().single()

        assertEquals(position.quantity, basis.quantity, 1e-9)
        assertEquals(position.cost, basis.totalCost, 1e-9)

        // Elde 1 adet @10.000 kalir; satilan adetten 5.000 kar kesinlesir.
        assertEquals(1.0, basis.quantity, 1e-9)
        assertEquals(10_000.0, basis.totalCost, 1e-9)
        assertEquals(5_000.0, basis.realizedProfit, 1e-9)
    }

    /**
     * Ekran sirasi (en yeni ustte) hala kurulabiliyor - kaldirilan sorgu
     * gorunumu bozmadi. Ayni gunun kayitlari kendi aralarinda kronolojik kalir.
     */
    @Test
    fun ekranSirasiEKRANDAKurulur() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.addTransaction(tx("t1", TradeSide.Buy, 1.0, 10_000.0))
        repo.addTransaction(
            tx("t2", TradeSide.Buy, 1.0, 11_000.0).copy(date = KefeDate(2026, 8, 5)),
        )

        val ordered = repo.observeTransactions("pos_gold_quarter").first()
            .sortedWith(
                compareByDescending<Transaction> { it.date.year }
                    .thenByDescending { it.date.month }
                    .thenByDescending { it.date.day },
            )
        assertEquals(listOf("t2", "t1"), ordered.map { it.id })
    }
}

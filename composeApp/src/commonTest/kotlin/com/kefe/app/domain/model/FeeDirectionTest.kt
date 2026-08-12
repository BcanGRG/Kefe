package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ISCILIK YONE GORE ISLER.
 *
 * Islemiyordu. [Transaction.total] yone bakmadan isciligi HEP ekliyordu, oysa
 * uygulamanin butun defter matematigi satista isciligi hasilattan DUSER
 * (costBasis'te `sold * unitPrice - fee`, xirr'de `quantity * unitPrice - fee`).
 * 1 adet @150, 10 iscilikle satista ekran "₺160", defter 140 diyordu - ayni
 * islem iki ekranda iki farkli tutar.
 *
 * Tutar Aktivite akisina da bu alandan yaziliyor, yani rakam sadece bakilan bir
 * sey degil, saklanan bir sey.
 */
class FeeDirectionTest {

    private fun tx(side: TradeSide, fee: Double = 10.0) = Transaction(
        id = "tx",
        positionId = "pos",
        date = KefeDate(2026, 8, 12),
        side = side,
        quantity = 1.0,
        unitPrice = 150.0,
        fee = fee,
        addedByMemberId = "m1",
    )

    @Test
    fun alistaIscilikEKLENIR() {
        assertEquals(160.0, tx(TradeSide.Buy).total, EPS)
    }

    @Test
    fun satistaIscilikDUSULUR() {
        assertEquals(140.0, tx(TradeSide.Sell).total, EPS)
    }

    /** Ekranin rakami defterin rakamiyla AYNI olmali. */
    @Test
    fun satisToplamiDEFTERLEUyusur() {
        val satis = tx(TradeSide.Sell)
        val defter = satis.quantity * satis.unitPrice - satis.fee

        assertEquals(defter, satis.total, EPS)
    }

    @Test
    fun isciliksizIslemdeYonFarkETMEZ() {
        assertEquals(tx(TradeSide.Buy, fee = 0.0).total, tx(TradeSide.Sell, fee = 0.0).total, EPS)
    }

    private companion object {
        const val EPS = 1e-9
    }
}

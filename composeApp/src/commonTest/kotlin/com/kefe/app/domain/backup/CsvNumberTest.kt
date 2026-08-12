package com.kefe.app.domain.backup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CSV'ye BILIMSEL GOSTERIM yazilmaz.
 *
 * `Double.toString()` buyuk ve cok kucuk sayilarda usse geciyor: 12.500.000
 * TL'lik bir islem "1,25E7" diye yaziliyor ve tr-TR Excel'de sayi degil METIN
 * olarak aciliyordu. Ayni kusur hedef tutarinda veri kaybina yol acmisti
 * (P0.4); burada dosya disariya gittigi icin sonuc sessiz ama kalici.
 */
class CsvNumberTest {

    private fun csvOf(quantity: Double, unitPrice: Double, fee: Double = 0.0): String {
        val file = BackupFile(
            takenOn = "2026-08-12",
            portfolioName = "Birikimlerim",
            positions = listOf(
                BackupPosition(
                    id = "pos",
                    name = "Gram Altın",
                    assetClass = "Gold",
                    unit = "Gram",
                    unitPrice = unitPrice,
                    manualPrice = false,
                ),
            ),
            transactions = listOf(
                BackupTransaction(
                    id = "tx",
                    positionId = "pos",
                    year = 2026,
                    month = 8,
                    day = 12,
                    side = "Buy",
                    quantity = quantity,
                    unitPrice = unitPrice,
                    fee = fee,
                    addedByMemberId = "m1",
                ),
            ),
        )
        return file.transactionsCsv()
    }

    @Test
    fun BUYUKTutarUSSEGecmez() {
        val csv = csvOf(quantity = 1.0, unitPrice = 12_500_000.0)

        assertFalse("E7" in csv || "E+" in csv, "bilimsel gosterim yazildi:\n$csv")
        assertTrue("12500000" in csv, "tutar bulunamadi:\n$csv")
    }

    @Test
    fun COKKUCUKTutarUSSEGecmez() {
        val csv = csvOf(quantity = 0.000_001, unitPrice = 1.0)

        assertFalse("E-" in csv, "bilimsel gosterim yazildi:\n$csv")
    }

    @Test
    fun ondalikVIRGULLEyazilir() {
        val csv = csvOf(quantity = 2.5, unitPrice = 6_763.88)

        assertTrue("6763,88" in csv, "ondalik nokta ile yazildi:\n$csv")
        assertTrue("2,5" in csv)
    }

    @Test
    fun tamSayidaONDALIKYazilmaz() {
        val csv = csvOf(quantity = 3.0, unitPrice = 100.0)

        assertTrue(";3;" in csv, "tam sayi ondalikla yazildi:\n$csv")
    }

    @Test
    fun eksiIsaretKORUNUR() {
        val csv = csvOf(quantity = 1.0, unitPrice = 100.0, fee = -5.0)

        assertTrue("-5" in csv, "eksi isaret kayboldu:\n$csv")
    }
}

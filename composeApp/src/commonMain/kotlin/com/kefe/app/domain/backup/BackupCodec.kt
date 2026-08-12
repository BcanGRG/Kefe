package com.kefe.app.domain.backup

import kotlin.math.abs
import kotlin.math.round
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.Karat
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import kotlinx.serialization.json.Json

/**
 * Yedek dosyasinin okunmasi/yazilmasi.
 *
 * [prettyPrint] bilincli: yedek kullanicinin elindeki tek kopya, gerektiginde
 * bir metin duzenleyicide acilip bakilabilmeli.
 */
private val backupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun BackupFile.encode(): String = backupJson.encodeToString(BackupFile.serializer(), this)

/**
 * Yedegi cozer.
 *
 * Bicim bozuksa ya da surum ileriyse [BackupException] atar - yarim yuklenmis
 * bir portfoy, hic yuklenmemis olandan kotudur.
 */
fun decodeBackup(text: String): BackupFile {
    val file = try {
        backupJson.decodeFromString(BackupFile.serializer(), text.cleaned())
    } catch (error: Exception) {
        throw BackupException("Dosya okunamadı — Kefe yedeği olmayabilir.", error)
    }
    if (file.version > CurrentBackupVersion) {
        throw BackupException(
            "Bu yedek uygulamanın daha yeni bir sürümünden. Önce uygulamayı güncelleyin.",
        )
    }
    return file
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Ayristiriciyi takan gorunmez karakterleri temizler.
 *
 * BOM (U+FEFF) basta durursa kotlinx.serialization daha ilk karakterde
 * "Unexpected JSON token" veriyor. Yedek dosyasi kullanicinin elinde dolasiyor -
 * Windows araclari, e-posta ekleri ve bazi bulut istemcileri BOM ekliyor.
 * Gercekten yasandi: emulatore kopyalanan yedek tam bu yuzden geri
 * yuklenemedi ve dosya kullaniciya "bozuk" gibi gorundu.
 */
private fun String.cleaned(): String = trim().removePrefix("﻿").trim()

// --- CSV ---------------------------------------------------------------------

private fun Int.pad(): String = if (this < 10) "0$this" else toString()

/**
 * Ondalik ayirici VIRGUL - tr-TR Excel sayiyi ancak boyle taniyor.
 *
 * BILIMSEL GOSTERIM YOK. `toString()` buyuk ve cok kucuk sayilarda usse
 * geciyor: 12.500.000 TL'lik bir islem CSV'ye "1,25E7" diye yaziliyor ve
 * Excel'de sayi degil metin olarak aciliyordu. Ayni kusur hedef tutarinda
 * (P0.4) veri kaybina yol acmisti; burada dosya disariya gittigi icin sonuc
 * sessiz ama kalici.
 *
 * En cok alti hane: fon payi TEFAS'ta oraya kadar iniyor, otesi kayan nokta
 * artigi olur. Sondaki sifirlar yazilmaz.
 */
private fun Double.csv(): String {
    if (!isFinite()) return "0"
    val negative = this < 0.0
    val a = abs(this)
    val scaled = round(a * CsvScale)
    val whole = (scaled / CsvScale).toLong()
    val frac = (scaled - whole * CsvScale).toLong()
    val sign = if (negative) "-" else ""
    if (frac == 0L) return "$sign$whole"
    val digits = frac.toString().padStart(CsvDecimals, '0').trimEnd('0')
    return "$sign$whole,$digits"
}

private const val CsvDecimals = 6
private const val CsvScale = 1_000_000.0

private fun String.escapeCsv(): String =
    if (contains(';') || contains('"') || contains('\n')) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }

// --- Enum cozumleme ----------------------------------------------------------
//
// Yedekteki metin taninmiyorsa (elle duzenlenmis dosya, eski surum) SATIR
// ATLANMAZ, makul bir varsayilana duser: kullanicinin kaydini kaybetmektense
// eksik bir alanla geri yuklemek yeglenir.

internal fun String.toAssetClass(): AssetClass =
    AssetClass.entries.firstOrNull { it.name == this } ?: AssetClass.Gold

internal fun String?.toGoldSubtype(): GoldSubtype? =
    this?.let { value -> GoldSubtype.entries.firstOrNull { it.name == value } }

internal fun String?.toKarat(): Karat? =
    this?.let { value -> Karat.entries.firstOrNull { it.name == value } }

internal fun String.toQuantityUnit(): QuantityUnit =
    QuantityUnit.entries.firstOrNull { it.name == this } ?: QuantityUnit.Piece

internal fun String.toTradeSide(): TradeSide =
    TradeSide.entries.firstOrNull { it.name == this } ?: TradeSide.Buy

internal fun String.toGoalUnit(): GoalUnit =
    GoalUnit.entries.firstOrNull { it.name == this } ?: GoalUnit.Try

internal fun String.toGoalStatus(): GoalStatus =
    GoalStatus.entries.firstOrNull { it.name == this } ?: GoalStatus.Active

/**
 * Yedekten dogrudan CSV uretir.
 *
 * Depodan ikinci bir okuma YAPILMAZ: yedek ile CSV ayni ani anlatmali, arada
 * bir islem eklenirse ikisi ayrisirdi.
 */
fun BackupFile.transactionsCsv(): String {
    val names = positions.associate { it.id to it.name }
    val header = listOf(
        "Tarih", "Varlık", "İşlem", "Miktar", "Birim fiyat", "İşçilik", "Tutar", "Not", "Saklama",
    ).joinToString(";")

    val rows = transactions.map { tx ->
        val gross = tx.quantity * tx.unitPrice
        val buy = tx.side.toTradeSide() == TradeSide.Buy
        listOf(
            "${tx.year}-${tx.month.pad()}-${tx.day.pad()}",
            names[tx.positionId] ?: tx.positionId,
            if (buy) "Alış" else "Satış",
            tx.quantity.csv(),
            tx.unitPrice.csv(),
            tx.fee.csv(),
            (if (buy) gross + tx.fee else gross - tx.fee).csv(),
            tx.note.orEmpty(),
            tx.storage.orEmpty(),
        ).joinToString(";") { it.escapeCsv() }
    }

    return (listOf(header) + rows).joinToString("\n")
}

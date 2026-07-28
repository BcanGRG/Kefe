package com.kefe.app.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.db.KefeDatabase
import java.io.File
import java.util.Locale
import java.util.Properties

/**
 * Veritabani isletim sisteminin kalici kullanici veri klasorune yazilir:
 *   Windows : %APPDATA%\Kefe\kefe.db
 *   macOS   : ~/Library/Application Support/Kefe/kefe.db
 *   Linux   : $XDG_DATA_HOME/Kefe/kefe.db  (yoksa ~/.local/share/Kefe/kefe.db)
 */
actual class DatabaseDriverFactory {

    actual fun createDriver(): SqlDriver {
        val file = File(appDataDirectory(), DATABASE_FILE_NAME)
        // JDBC surucusu semayi kendisi kurmaz; asagidaki fabrika bicimi PRAGMA
        // user_version'a bakip ilk acilista create, surum atlamislarsa migrate calistirir.
        // Duz kurucu (url, properties) cagrilirsa tablolar hic olusmaz.
        return JdbcSqliteDriver(
            url = "jdbc:sqlite:${file.absolutePath}",
            properties = connectionProperties(),
            schema = KefeDatabase.Schema,
        )
    }
}

/**
 * Yabanci anahtar zorlamasi SQLite'ta baglanti basina ve varsayilan olarak kapali;
 * sema ON DELETE CASCADE'e dayaniyor. JDBC surucusu her is parcacigina yeni baglanti
 * actigi icin pragma'yi tek sefer elle calistirmak yetmez - baglanti ozelligi olarak
 * verilince surucu her acilan baglantida uygular.
 */
private fun connectionProperties(): Properties = Properties().apply {
    setProperty("foreign_keys", "true")
}

/**
 * Gecici klasor ya da calisma dizini secilseydi kayitlar isletim sistemi temizliginde
 * ya da uygulama baska bir dizinden baslatildiginda kaybolurdu - kullanicinin olcutu
 * "kapat, ac, duruyor" oldugu icin yol her acilista ayni olmali.
 */
private fun appDataDirectory(): File {
    val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    val userHome = File(System.getProperty("user.home").orEmpty())

    val base = when {
        osName.contains("win") ->
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(userHome, "AppData/Roaming")

        osName.contains("mac") || osName.contains("darwin") ->
            File(userHome, "Library/Application Support")

        else ->
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(userHome, ".local/share")
    }

    return File(base, "Kefe").also { directory ->
        // JDBC ust klasoru kendisi acmaz; yoksa baglanti "unable to open database file" der.
        if (!directory.isDirectory) directory.mkdirs()
    }
}

package com.kefe.app.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.kefe.app.db.KefeDatabase

/**
 * Veritabani uygulama kumesindeki kalici klasore yazilir:
 * <app sandbox>/Library/Application Support/databases/kefe.db
 *
 * Yolu surucunun altindaki SQLiter belirler ve klasoru kendisi olusturur; ad yalniz
 * dosya adi olabilir, ayirac tasiyamaz.
 *
 * Sema kurulumu ve gocler surucunun icinde: SQLiter'a verilen create/upgrade geri
 * cagrilari ilk acilista tablolari kuruyor, masaustundeki gibi elle adim yok.
 */
actual class DatabaseDriverFactory {

    actual fun createDriver(): SqlDriver = NativeSqliteDriver(
        schema = KefeDatabase.Schema,
        name = DATABASE_FILE_NAME,
        onConfiguration = { configuration ->
            // Yabanci anahtar zorlamasi SQLite'ta baglanti basina ve varsayilan olarak
            // kapali; sema ON DELETE CASCADE'e dayandigi icin acik olmali.
            configuration.copy(
                extendedConfig = configuration.extendedConfig.copy(
                    foreignKeyConstraints = true,
                ),
            )
        },
    )
}

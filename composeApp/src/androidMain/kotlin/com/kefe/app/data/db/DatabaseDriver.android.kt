package com.kefe.app.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.kefe.app.db.KefeDatabase

/**
 * Veritabani uygulamanin ozel alanina yazilir:
 * /data/data/com.kefe.app/databases/kefe.db
 *
 * Sema kurulumu ve gocler surucunun icinde: acilis yardimcisi ilk calismada
 * onCreate, surum atlamislarsa onUpgrade uzerinden semayi isletir.
 */
actual class DatabaseDriverFactory(private val context: Context) {

    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = KefeDatabase.Schema,
        // Surucu uygulama omru boyunca yasar; Activity Context'i tutulursa ekran
        // dondugunde sizinti olur, bu yuzden her zaman uygulama Context'ine cikilir.
        context = context.applicationContext,
        name = DATABASE_FILE_NAME,
        callback = ForeignKeysCallback(KefeDatabase.Schema),
    )
}

/**
 * SQLite yabanci anahtar zorlamasini her baglantida VARSAYILAN OLARAK KAPALI tutar.
 * Sema ON DELETE CASCADE ile yazildigi icin acik olmazsa pozisyon silindiginde
 * islemleri oksuz kalir. onConfigure semadan ve goclerden once, transaction disinda
 * calistigi icin dogru yer burasi.
 */
private class ForeignKeysCallback(
    schema: SqlSchema<QueryResult.Value<Unit>>,
) : AndroidSqliteDriver.Callback(schema) {

    override fun onConfigure(db: SupportSQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}

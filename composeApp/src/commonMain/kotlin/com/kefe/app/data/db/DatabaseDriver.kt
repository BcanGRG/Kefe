package com.kefe.app.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Dosya adi uc platformda ayni: kullanici ayni uygulamayi telefonda ve masaustunde
 * acinca ayni ismi arar, yedek/geri yukleme islerinde de tek isim aramak yeter.
 */
internal const val DATABASE_FILE_NAME = "kefe.db"

/**
 * SQLDelight surucusunu ureten platform fabrikasi.
 *
 * Surucu her platformda baska sekilde acilir: Android SQLite'i bir Context uzerinden
 * acar, masaustu JDBC'ye mutlak bir dosya yolu verir, iOS gomulu SQLite'i kullanir.
 * Ortak kod bu ayrimi bilmek zorunda kalmasin diye tek bir sozlesme arkasina alindi.
 *
 * Kurucu bilerek tanimlanmadi: Android karsiligi Context ister, digerleri istemez.
 * Bu yuzden nesneyi ortak kod DEGIL, platforma ozel Koin modulu yaratir.
 */
expect class DatabaseDriverFactory {

    /**
     * Semayi ilk acilista kurar, surum atlamislarsa gocleri isletir. Uygulama omru
     * boyunca tek surucu yeterli - donen nesne DI'da single olarak tutulmali.
     */
    fun createDriver(): SqlDriver
}

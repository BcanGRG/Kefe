package com.kefe.app.di

import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.db.seedSampleDataIfEmpty
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.model.KefeDate

/**
 * Platformdan gelen veritabani surucusunun tek durak yeri.
 *
 * NEDEN KOIN'IN ICINDE DEGIL: Android surucusu bir Context ister, masaustu ve iOS
 * istemez; ortak modul bu nesneyi kendisi yaratamaz. Ustelik Koin bu uygulamada
 * Compose agacinin icinde kuruluyor (App.kt), yani Activity yeniden yaratilirsa
 * grafik de yeniden kurulur. Surucu Koin'e birakilsaydi her kurulumda YENI bir
 * SQLite baglantisi acilir, eskisi kapanmazdi. Burada tutuldugu icin surec omru
 * boyunca tek veritabani vardir.
 *
 * Giris noktasi (MainActivity / Main.kt / MainViewController) ilk ekrandan once
 * [install] cagirmali.
 */
object KefePlatform {

    private var driverFactory: DatabaseDriverFactory? = null
    private var database: KefeDatabase? = null

    /**
     * Ikinci cagri YOK SAYILIR: Android'de Activity yeniden yaratilirsa bu satir
     * tekrar calisir, acilmis veritabanini degistirmemeli.
     */
    fun install(factory: DatabaseDriverFactory) {
        if (driverFactory == null) driverFactory = factory
    }

    /**
     * Veritabanini ilk istekte acar; sema kurulumu ve ilk acilis hazirligi da
     * burada, tek sefer calisir.
     */
    internal fun database(today: KefeDate, seedSampleData: Boolean): KefeDatabase {
        database?.let { return it }

        val factory = driverFactory ?: error(
            "Kefe veritabani kurulmadi: giris noktasinda KefePlatform.install(...) cagrilmali.",
        )
        val created = createKefeDatabase(factory.createDriver())
        if (seedSampleData) created.seedSampleDataIfEmpty(today) else created.bootstrapIfNeeded()
        database = created
        return created
    }
}

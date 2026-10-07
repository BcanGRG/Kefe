package com.kefe.app

import android.app.Application
import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.di.KefeKoin
import com.kefe.app.di.KefePlatform
import com.kefe.app.widget.HomeScreenSync

/**
 * Surec basi. Veritabani surucusu ve Koin burada kurulur: ana ekran widget'i
 * ve hizli giris penceresi uygulamanin ekranlari olmadan da calisir. Uygulama
 * acilinca Compose'daki KoinApplication bu grafigi bulur ve onu kullanir (bkz.
 * KefeKoin) - ikinci bir veritabani baglantisi acilmaz.
 */
class KefeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        KefePlatform.install(DatabaseDriverFactory(applicationContext))
        KefeKoin.koin()
        HomeScreenSync.start(this)
    }
}

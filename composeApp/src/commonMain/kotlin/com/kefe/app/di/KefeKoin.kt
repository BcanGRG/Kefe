package com.kefe.app.di

import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

/**
 * Koin'e Compose DISINDAN erisim: ana ekran widget'i ve hizli ayar kutucugu bir
 * Activity olmadan calisir.
 *
 * Uygulama Koin'i Compose agacinda kuruyor (App.kt, KoinApplication); o da
 * surecte zaten kurulmus bir Koin bulursa YENISINI KURMAZ, onu kullanir. Bu
 * yuzden surec basinda (Android'de Application.onCreate) [koin] cagrilirsa
 * widget, hizli giris ve uygulama ayni grafigi - ayni veritabanini - paylasir.
 */
object KefeKoin {
    fun koin(): Koin = KoinPlatform.getKoinOrNull() ?: startKoin { modules(appModule) }.koin
}

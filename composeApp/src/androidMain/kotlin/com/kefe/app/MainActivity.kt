package com.kefe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.di.KefePlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Ilk bestelemede Koin depolari cozuyor, onlar da veritabanini istiyor:
        // surucu setContent'ten ONCE kurulmali. Ikinci cagri yok sayilir, ekran
        // dondugunde Activity yeniden yaratilinca sorun cikmaz.
        KefePlatform.install(DatabaseDriverFactory(applicationContext))
        setContent { App() }
    }
}

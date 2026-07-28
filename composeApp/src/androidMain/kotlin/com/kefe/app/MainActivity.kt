package com.kefe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.kefe.app.data.backup.AndroidFileBridge
import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.di.KefePlatform

class MainActivity : ComponentActivity() {

    /**
     * Yedek dosyasi secici.
     *
     * Alan olarak kaydedilir, onCreate icinde degil: Android STARTED'a gecmis
     * bir sahipte kayit yapilmasina izin vermiyor ("LifecycleOwners must call
     * register before they are STARTED").
     */
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        AndroidFileBridge.deliver(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Ilk bestelemede Koin depolari cozuyor, onlar da veritabanini istiyor:
        // surucu setContent'ten ONCE kurulmali. Ikinci cagri yok sayilir, ekran
        // dondugunde Activity yeniden yaratilinca sorun cikmaz.
        KefePlatform.install(DatabaseDriverFactory(applicationContext))
        AndroidFileBridge.attach(this, openBackup)
        setContent { App() }
    }

    override fun onDestroy() {
        // Activity referansi birakilir; tutulursa ekran her dondugunde bir
        // oncekini sizdiririz.
        AndroidFileBridge.detach(this)
        super.onDestroy()
    }
}

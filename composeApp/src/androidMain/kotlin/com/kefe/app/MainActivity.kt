package com.kefe.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import com.kefe.app.data.backup.AndroidFileBridge
import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.di.KefePlatform

class MainActivity : FragmentActivity() {

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
        // Sistem acilis penceresi, uygulama ILK KARESINI cizmeye hazir olana
        // kadar ekranda tutulur. Onceden bu bekleme Compose tarafinda bos bir
        // yuzey cizerek yapiliyordu; sonuc, sistem penceresi ile ilk gercek
        // ekran arasinda ikinci bir bos kareydi.
        //
        // installSplashScreen() super.onCreate'ten ONCE cagrilmali.
        var ready = false
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !ready }
        // Sistemin kendi cikis animasyonu atilir: hemen ardindan Compose'un
        // marka animasyonu basliyor, ikisi ust uste binince goz iki ayri
        // hareket goruyor.
        splash.setOnExitAnimationListener { it.remove() }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Ilk bestelemede Koin depolari cozuyor, onlar da veritabanini istiyor:
        // surucu setContent'ten ONCE kurulmali. Ikinci cagri yok sayilir, ekran
        // dondugunde Activity yeniden yaratilinca sorun cikmaz.
        KefePlatform.install(DatabaseDriverFactory(applicationContext))
        AndroidFileBridge.attach(this, openBackup)
        setContent { App(onReady = { ready = true }) }
    }

    override fun onDestroy() {
        // Activity referansi birakilir; tutulursa ekran her dondugunde bir
        // oncekini sizdiririz.
        AndroidFileBridge.detach(this)
        super.onDestroy()
    }
}

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
        // Sistem penceresi KESILEREK degil SOLDURULARAK birakilir.
        //
        // Once dogrudan remove() cagriliyordu: sistemin kendi zoom-out'u Compose'un
        // marka animasyonuyla cakisiyordu, dogru. Ama sert kesme de bedava degildi -
        // altta zaten cizili duran Compose isareti bir anda beliriyor ve goz bunu
        // "ikinci bir ekran acildi" diye okuyordu. Solma bir HAREKET degil bir
        // gecistir: iki ayri hareket olusmaz, sicrama da kalmaz. Compose isareti
        // bu sirada zaten sistem ikonunun boyundan kendi boyuna iniyor
        // (bkz. KefeSplash.HandoffScale), yani devir teslim tek bir surekli
        // hareket olarak gorunur.
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SplashHandoffMillis)
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Ilk bestelemede Koin depolari cozuyor, onlar da veritabanini istiyor:
        // surucu setContent'ten ONCE kurulmali. Ikinci cagri yok sayilir, ekran
        // dondugunde Activity yeniden yaratilinca sorun cikmaz.
        KefePlatform.install(DatabaseDriverFactory(applicationContext))
        AndroidFileBridge.attach(this, openBackup)
        setContent { App(onReady = { ready = true }) }
    }

    /**
     * Sistem penceresinin solma suresi. Kisa: bu bir gosteri degil, altta zaten
     * oynayan animasyona gecis. Uzatmak soguk acilisi gozle gorulur geciktirir.
     */
    private val SplashHandoffMillis = 260L

    override fun onDestroy() {
        // Activity referansi birakilir; tutulursa ekran her dondugunde bir
        // oncekini sizdiririz.
        AndroidFileBridge.detach(this)
        super.onDestroy()
    }
}

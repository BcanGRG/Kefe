package com.kefe.app

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import com.kefe.app.data.backup.AndroidFileBridge
import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.di.KefePlatform
import com.kefe.app.navigation.KefeLaunch
import com.kefe.app.navigation.LaunchTarget

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
        // Yalniz ilk acilista: ekran donunce ayni niyet yeniden islenmesin.
        if (savedInstanceState == null) handleOpen(intent)
        setContent { App(onReady = { ready = true }) }
    }

    /** Uygulama arkada aciksa widget'in "Harcamalar"i buraya gelir (CLEAR_TOP | SINGLE_TOP). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpen(intent)
    }

    /** Ana ekrandan gelen "su sayfada ac" istegi; kabuk uygulama iceridiyken tuketir (App.kt). */
    private fun handleOpen(intent: Intent?) {
        when (intent?.getStringExtra(ExtraOpen)) {
            OpenExpenses -> KefeLaunch.request(LaunchTarget.Expenses)
        }
    }

    /**
     * Sistem penceresinin solma suresi. Kisa: bu bir gosteri degil, altta zaten
     * oynayan animasyona gecis. Uzatmak soguk acilisi gozle gorulur geciktirir.
     */
    private val SplashHandoffMillis = 260L

    companion object {
        const val ExtraOpen = "com.kefe.app.OPEN"
        const val OpenExpenses = "expenses"
    }

    override fun onDestroy() {
        // Activity referansi birakilir; tutulursa ekran her dondugunde bir
        // oncekini sizdiririz.
        AndroidFileBridge.detach(this)
        super.onDestroy()
    }
}

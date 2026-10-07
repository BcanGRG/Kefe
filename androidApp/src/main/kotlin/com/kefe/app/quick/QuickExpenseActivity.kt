package com.kefe.app.quick

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.di.KefePlatform
import com.kefe.app.ui.screens.quick.QuickExpenseApp

/**
 * Ana ekranin ustunde acilan hizli harcama penceresi: widget, uygulama
 * kisayolu ve hizli ayar kutucugu buraya gelir. Uygulamanin kendisi (ve kilidi)
 * acilmaz - kullanici karari, Ekim 2026.
 *
 * Kendi gorevinde calisir (manifest: taskAffinity bos, son kullanilanlarda
 * gorunmez): uygulama arkada aciksa bile pencere onun ustunde degil, ana
 * ekranin ustunde acilir ve kapaninca ana ekrana donulur.
 */
class QuickExpenseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Application zaten kurdu; surec baska yoldan baslamissa da zarari yok (ikinci cagri yok sayilir).
        KefePlatform.install(DatabaseDriverFactory(applicationContext))
        val category = intent.getStringExtra(ExtraCategory)
        setContent { QuickExpenseApp(initialCategory = category, onClose = ::close) }
    }

    private fun close() {
        finish()
        // Pencere kayarak gelir, solarak gider - ikinci bir kayma hareketi olmaz.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    companion object {
        private const val ExtraCategory = "com.kefe.app.quick.CATEGORY"

        /**
         * [category] kalemin adi ("Groceries", "c:Kredi Kartı Limit"); null = kalem
         * secilmeden. Veri adresi kalemi tasir: ayri kalemlerin niyetleri ayri
         * PendingIntent olur, widget'taki kutular birbirinin kalemini acmaz.
         */
        fun intent(context: Context, category: String?): Intent =
            Intent(context, QuickExpenseActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("kefe://harcama-ekle/" + Uri.encode(category.orEmpty())))
                .putExtra(ExtraCategory, category)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

package com.kefe.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.annotation.ColorRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.kefe.app.R
import com.kefe.app.di.KefeKoin
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.repository.PlanRepository
import com.kefe.app.quick.QuickExpenseActivity
import com.kefe.app.ui.screens.quick.QuickCategoryUi
import com.kefe.app.ui.screens.quick.quickCategories
import com.kefe.app.ui.screens.quick.quickCategoryUis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Ana ekrandaki Kefe yuzeyleri (widget ve uygulama kisayollari) defterle ayni kalsin.
 *
 * Harcama nereden yazilirsa yazilsin - uygulama, hizli giris penceresi ya da
 * hesaptan gelen esitleme - hepsi bu surecteki veritabanina duser; defter akisi
 * degisince widget yeniden cizilir. Gun donumunde de (bugunun toplami sifirlanir)
 * bir kez cizilir; surec o saatte yasamiyorsa widget'in yarim saatlik tazelemesi
 * yakalar (res/xml/expense_widget_info.xml).
 *
 * Surec basinda bir kez baslar (KefeApplication).
 */
object HomeScreenSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var lastShortcuts: List<QuickCategoryUi>? = null

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        // Depolar arka planda cozulur: veritabani ilk istekte acilir, ana is parcacigini tutmasin.
        scope.launch {
            val koin = KefeKoin.koin()
            val plan = koin.get<PlanRepository>()
            val clock = koin.get<KefeClock>()
            launch {
                while (true) {
                    delay(millisToNextDay(clock.nowEpochMillis()))
                    refreshWidgets(app)
                }
            }
            plan.observeAllBooks()
                // Bir kayit birkac tablo bildirimi uretir; widget bir kez cizilsin.
                .debounce(RefreshDebounceMillis)
                .collect { books ->
                    refreshWidgets(app)
                    val today = clock.today()
                    val top = quickCategoryUis(books, today, quickCategories(books, today).take(ShortcutCategoryCount))
                    if (top != lastShortcuts) {
                        lastShortcuts = top
                        publishShortcuts(app, top)
                    }
                }
        }
    }

    private suspend fun refreshWidgets(context: Context) {
        runCatching {
            if (GlanceAppWidgetManager(context).getGlanceIds(ExpenseWidget::class.java).isNotEmpty()) {
                ExpenseWidget().updateAll(context)
            }
        }
    }

    /**
     * Kefe simgesine basili tutunca: "Harcama ekle", en cok girilen iki kalem ve
     * Harcamalar sayfasi. Kalemler degisince yenilenir.
     */
    private fun publishShortcuts(context: Context, top: List<QuickCategoryUi>) {
        val add = ShortcutInfoCompat.Builder(context, "harcama-ekle")
            .setShortLabel("Harcama ekle")
            .setIcon(letterIcon(context, "+", R.color.widget_accent))
            .setIntent(QuickExpenseActivity.intent(context, null))
            .setRank(0)
            .build()
        val categories = top.mapIndexed { index, item ->
            ShortcutInfoCompat.Builder(context, "kalem:" + item.category.name)
                .setShortLabel(item.label.take(ShortLabelMax))
                .setLongLabel("${item.label} harcaması")
                .setIcon(letterIcon(context, item.initial, categoryColor(item.colorIndex)))
                .setIntent(QuickExpenseActivity.intent(context, item.category.name))
                .setRank(index + 1)
                .build()
        }
        val page = ShortcutInfoCompat.Builder(context, "harcamalar")
            .setShortLabel("Harcamalar")
            .setIcon(letterIcon(context, "≡", R.color.widget_muted))
            .setIntent(expensesIntent(context))
            .setRank(categories.size + 1)
            .build()
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, listOf(add) + categories + page) }
    }

    /** Uyarlanabilir simge: Kefe'nin koyu kutusunda kalemin harfi, kalemin renginde. */
    private fun letterIcon(context: Context, text: String, @ColorRes color: Int): IconCompat {
        val density = context.resources.displayMetrics.density
        val px = (AdaptiveIconDp * density).toInt()
        val bitmap: Bitmap = createBitmap(px, px)
        val canvas = Canvas(bitmap)
        canvas.drawColor(context.getColor(R.color.widget_sunken))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = context.getColor(color)
            textSize = LetterDp * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val baseline = px / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, px / 2f, baseline, paint)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    /** Turkiye saatiyle (UTC+3) bir sonraki gun basina kalan sure, birkac saniye payla. */
    private fun millisToNextDay(now: Long): Long {
        val local = now + IstanbulOffsetMillis
        val nextLocalMidnight = (local / DayMillis + 1) * DayMillis
        return nextLocalMidnight - local + DayRolloverSlackMillis
    }

    private const val RefreshDebounceMillis = 800L
    private const val ShortcutCategoryCount = 2
    private const val ShortLabelMax = 25
    private const val AdaptiveIconDp = 108
    private const val LetterDp = 40
    private const val IstanbulOffsetMillis = 3L * 60L * 60L * 1000L
    private const val DayMillis = 24L * 60L * 60L * 1000L
    private const val DayRolloverSlackMillis = 5_000L
}

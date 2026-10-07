package com.kefe.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kefe.app.MainActivity
import com.kefe.app.R
import com.kefe.app.di.KefeKoin
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.repository.PlanRepository
import com.kefe.app.quick.QuickExpenseActivity
import com.kefe.app.ui.screens.quick.ExpenseWidgetUi
import com.kefe.app.ui.screens.quick.QuickCategoryUi
import com.kefe.app.ui.screens.quick.QuickRecentUi
import com.kefe.app.ui.screens.quick.WidgetMonthUi
import com.kefe.app.ui.screens.quick.expenseWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Ana ekrandaki harcama widget'i. Tasarim onayli (Ekim 2026, Claude Design
 * tuvali "Kefe Harcama Widget'ı"): dort boy - 4×1 ince serit, 2×2 kucuk, 4×2
 * orta (varsayilan), 4×4 buyuk. Boy, widget'in gercek olcusunden secilir.
 *
 * Kaleme ya da + dugmesine dokununca uygulama ACILMAZ: ana ekranin ustunde
 * kucuk giris penceresi gelir ([QuickExpenseActivity]). Bugunun toplamina ya
 * da "Harcamalar"a dokununca uygulama Harcamalar sayfasinda acilir.
 *
 * Kose ve renkler cizilebilir kaynaklardan (res/drawable/widget_*): Android 11
 * widget'larda kose yuvarlatmayi desteklemiyor, tema da sistemin acik/koyu
 * secimine resource niteleyiciyle (values-night) uyar.
 */
class ExpenseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = withContext(Dispatchers.IO) {
            val koin = KefeKoin.koin()
            expenseWidget(koin.get<PlanRepository>().observeAllBooks().first(), koin.get<KefeClock>().today())
        }
        provideContent { WidgetBody(context, ui) }
    }
}

class ExpenseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpenseWidget()
}

@Composable
private fun WidgetBody(context: Context, ui: ExpenseWidgetUi) {
    val size = LocalSize.current
    when {
        size.height < SlimMaxHeight -> Slim(context, ui)
        size.width < SmallMaxWidth -> Small(context, ui)
        size.height < LargeMinHeight -> Medium(context, ui, size)
        else -> Large(context, ui, size)
    }
}

// --- Boylar -----------------------------------------------------------------

/** 4×1: tek dokunusla giris; sagda bugun. */
@Composable
private fun Slim(context: Context, ui: ExpenseWidgetUi) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .clickable(quickEntry(context, null))
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddSquare(44.dp, 22.dp, R.drawable.widget_add_small_bg, clickable = null)
        Spacer(GlanceModifier.width(12.dp))
        Text("Harcama ekle", style = text(R.color.widget_on_surface, 16.sp, FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
        Column(horizontalAlignment = Alignment.End) {
            Text("BUGÜN", style = text(R.color.widget_muted, 11.sp, FontWeight.Medium))
            Text(ui.todayTotal, style = text(R.color.widget_on_surface, 16.sp, FontWeight.Medium))
        }
    }
}

/** 2×2: bugun ve genis + Harcama dugmesi. */
@Composable
private fun Small(context: Context, ui: ExpenseWidgetUi) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(14.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(openExpenses(context))) {
            Text("BUGÜN", style = text(R.color.widget_muted, 11.sp, FontWeight.Medium))
            Text(ui.todayTotal, style = text(R.color.widget_on_surface, 26.sp, FontWeight.Medium), maxLines = 1)
            Text(ui.countText, style = text(R.color.widget_muted, 12.sp), maxLines = 1)
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(48.dp)
                .background(ImageProvider(R.drawable.widget_add_wide_bg))
                .clickable(quickEntry(context, null))
                .semantics { contentDescription = "Harcama ekle" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_plus), contentDescription = null, modifier = GlanceModifier.size(20.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text("Harcama", style = text(R.color.widget_on_accent, 15.sp, FontWeight.Medium))
        }
    }
}

/** 4×2 (varsayilan): bugun, ayin gidisi, dort kalem ve + dugmesi. */
@Composable
private fun Medium(context: Context, ui: ExpenseWidgetUi, size: DpSize) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(horizontal = Pad, vertical = 12.dp),
    ) {
        Header(context, ui)
        // Kisa widget'ta (bazi baslatıcılarda 4×2 ~150dp) ay satiri cikar, kalemler kalir.
        val month = ui.month
        if (month != null && size.height >= MonthMinHeight) {
            Spacer(GlanceModifier.height(8.dp))
            MonthBar(month, size.width - Pad * 2)
        }
        Spacer(GlanceModifier.defaultWeight())
        Tiles(context, ui.tiles)
    }
}

/** 4×4: ortanin ustune son girilen uc harcama ve Harcamalar baglantisi. */
@Composable
private fun Large(context: Context, ui: ExpenseWidgetUi, size: DpSize) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(start = Pad, end = Pad, top = 12.dp, bottom = 6.dp),
    ) {
        Header(context, ui)
        ui.month?.let {
            Spacer(GlanceModifier.height(8.dp))
            MonthBar(it, size.width - Pad * 2)
        }
        Spacer(GlanceModifier.height(10.dp))
        Tiles(context, ui.tiles)
        if (ui.recent.isNotEmpty()) {
            Spacer(GlanceModifier.height(12.dp))
            Text("SON GİRİLENLER", style = text(R.color.widget_muted, 11.sp, FontWeight.Medium))
            ui.recent.forEach { RecentRow(context, it) }
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(36.dp).clickable(openExpenses(context)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(ui.pageLink, style = text(R.color.widget_accent, 14.sp, FontWeight.Medium))
            Spacer(GlanceModifier.width(4.dp))
            Image(ImageProvider(R.drawable.ic_widget_chevron), contentDescription = null, modifier = GlanceModifier.size(16.dp))
        }
    }
}

// --- Parcalar ---------------------------------------------------------------

@Composable
private fun Header(context: Context, ui: ExpenseWidgetUi) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = GlanceModifier.defaultWeight().clickable(openExpenses(context))) {
            Text(ui.todayLabel, style = text(R.color.widget_muted, 11.sp, FontWeight.Medium), maxLines = 1)
            Text(ui.todayTotal, style = text(R.color.widget_on_surface, 26.sp, FontWeight.Medium), maxLines = 1)
            Text(ui.todayLine, style = text(R.color.widget_muted, 12.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.width(12.dp))
        AddSquare(56.dp, 26.dp, R.drawable.widget_add_bg, clickable = quickEntry(context, null))
    }
}

@Composable
private fun AddSquare(box: Dp, icon: Dp, background: Int, clickable: Action?) {
    val base = GlanceModifier.size(box).background(ImageProvider(background))
    Box(
        modifier = (if (clickable != null) base.clickable(clickable) else base)
            .semantics { contentDescription = "Harcama ekle" },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(R.drawable.ic_widget_plus), contentDescription = null, modifier = GlanceModifier.size(icon))
    }
}

/**
 * Ayin cubugu: dolu kisim aylik giderlerin harcanan payi, beyaz cizgi ayin
 * bugunu. Glance'ta oransal genislik yok; cubuk widget'in gercek eninden olculur.
 */
@Composable
private fun MonthBar(month: WidgetMonthUi, width: Dp) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Box(modifier = GlanceModifier.width(width).height(12.dp), contentAlignment = Alignment.CenterStart) {
            Box(GlanceModifier.width(width).height(6.dp).background(ImageProvider(R.drawable.widget_bar_track))) {}
            val fill = width * month.ratio
            if (fill >= 2.dp) {
                Box(
                    GlanceModifier.width(fill).height(6.dp)
                        .background(ImageProvider(if (month.over) R.drawable.widget_bar_over else R.drawable.widget_bar_fill)),
                ) {}
            }
            Row(modifier = GlanceModifier.width(width).height(12.dp)) {
                Spacer(GlanceModifier.width((width * month.todayRatio - 1.dp).coerceIn(0.dp, width - 2.dp)))
                Box(GlanceModifier.width(2.dp).height(12.dp).background(ImageProvider(R.drawable.widget_tick))) {}
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(month.text, style = text(R.color.widget_muted, 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Text(month.ratioText, style = text(R.color.widget_muted, 12.sp, align = TextAlign.End), maxLines = 1)
        }
    }
}

@Composable
private fun Tiles(context: Context, tiles: List<QuickCategoryUi>) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        tiles.forEachIndexed { index, tile ->
            if (index > 0) Spacer(GlanceModifier.width(6.dp))
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(TileHeight)
                    .background(ImageProvider(R.drawable.widget_tile_bg))
                    .clickable(quickEntry(context, tile.category))
                    .padding(horizontal = 6.dp)
                    .semantics { contentDescription = "${tile.label} harcaması ekle" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Initial(tile.initial, tile.colorIndex, 20.dp, R.drawable.widget_badge_bg, 11.sp)
                Spacer(GlanceModifier.height(4.dp))
                Text(tile.label, style = text(R.color.widget_on_surface, 11.sp, align = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RecentRow(context: Context, row: QuickRecentUi) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(40.dp).clickable(openExpenses(context)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initial(row.initial, row.colorIndex, 28.dp, R.drawable.widget_badge_large_bg, 12.sp)
        Spacer(GlanceModifier.width(10.dp))
        Text(row.title, style = text(R.color.widget_on_surface, 14.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(8.dp))
        Text(row.whenText, style = text(R.color.widget_muted, 12.sp), maxLines = 1)
        Text(row.amount, style = text(R.color.widget_on_surface, 14.sp, align = TextAlign.End), maxLines = 1, modifier = GlanceModifier.width(76.dp))
    }
}

@Composable
private fun Initial(initial: String, colorIndex: Int, box: Dp, background: Int, fontSize: TextUnit) {
    Box(
        modifier = GlanceModifier.size(box).background(ImageProvider(background)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = text(categoryColor(colorIndex), fontSize, FontWeight.Bold))
    }
}

// --- Yardimci ---------------------------------------------------------------

private fun text(color: Int, size: TextUnit, weight: FontWeight = FontWeight.Normal, align: TextAlign? = null) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight, textAlign = align)

/** Harcamalar sayfasindaki kalem paleti; -1 plan disi, soluk. Kisayol simgeleri de bunu kullanir. */
internal fun categoryColor(index: Int): Int =
    if (index < 0) R.color.widget_muted else CategoryColors[index % CategoryColors.size]

private val CategoryColors = listOf(
    R.color.widget_cat_gold,
    R.color.widget_cat_fx,
    R.color.widget_cat_fund,
    R.color.widget_cat_stock,
    R.color.widget_cat_cash,
    R.color.widget_cat_silver,
)

/**
 * Hizli giris penceresi. Her kalemin niyeti AYRI olsun diye veri adresi kalemin
 * adini tasir - ayni niyet tek PendingIntent'e cokerse butun kutular ayni kalemi acar.
 */
private fun quickEntry(context: Context, category: ExpenseCategory?): Action =
    actionStartActivity(QuickExpenseActivity.intent(context, category?.name))

/** Uygulama Harcamalar sayfasinda acilir (kilit aciksa once kilit). */
private fun openExpenses(context: Context): Action = actionStartActivity(expensesIntent(context))

/**
 * Uygulamayi Harcamalar sayfasinda acan niyet. Uygulama arkada aciksa yeni bir
 * kopya acilmaz: ustteki MainActivity niyeti onNewIntent'te alir.
 */
internal fun expensesIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse("kefe://harcamalar"))
        .putExtra(MainActivity.ExtraOpen, MainActivity.OpenExpenses)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

private val Pad = 14.dp
private val TileHeight = 52.dp
private val SlimMaxHeight = 100.dp
private val SmallMaxWidth = 220.dp
private val LargeMinHeight = 300.dp
private val MonthMinHeight = 170.dp

package com.kefe.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.color
import com.kefe.app.domain.model.label
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.data.sample.SampleData
import com.kefe.app.data.sample.SampleSeries
import com.kefe.app.ui.brand.KefeAnimatedMark
import com.kefe.app.ui.brand.KefeLogoHorizontal
import com.kefe.app.ui.brand.KefeLogoVertical
import com.kefe.app.ui.brand.KefeMark
import com.kefe.app.ui.brand.KefeMarkStaticProgress
import com.kefe.app.ui.charts.BarSegment
import com.kefe.app.ui.charts.DonutSlice
import com.kefe.app.ui.charts.KefeDonutChart
import com.kefe.app.ui.charts.KefeDonutLegend
import com.kefe.app.ui.charts.KefeGoalRing
import com.kefe.app.ui.charts.KefeNetWorthChart
import com.kefe.app.ui.charts.KefePriceChart
import com.kefe.app.ui.charts.KefeProjectionChart
import com.kefe.app.ui.charts.KefeStackedBarChart
import com.kefe.app.ui.charts.MonthBar
import com.kefe.app.ui.charts.Point
import com.kefe.app.ui.charts.TradeMarker
import com.kefe.app.ui.components.KefeAmountField
import com.kefe.app.ui.components.KefeAvatar
import com.kefe.app.ui.components.KefeAvatarStack
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.KefeBuyBadge
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeDeltaText
import com.kefe.app.ui.components.KefeDestructiveTextButton
import com.kefe.app.ui.components.KefeEmptyState
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefeInfoBanner
import com.kefe.app.ui.components.KefeListRow
import com.kefe.app.ui.components.KefeMainGoalBadge
import com.kefe.app.ui.components.KefeManualBadge
import com.kefe.app.ui.components.KefeMemberBadge
import com.kefe.app.ui.components.KefeMilestoneBar
import com.kefe.app.ui.components.KefeOfflineBanner
import com.kefe.app.ui.components.KefeOwnerBadge
import com.kefe.app.ui.components.KefePendingBadge
import com.kefe.app.ui.components.KefePeriodChips
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeProgressBar
import com.kefe.app.ui.components.KefeProgressBarThin
import com.kefe.app.ui.components.KefeRadioRow
import com.kefe.app.ui.components.KefeSearchField
import com.kefe.app.ui.components.KefeSecondaryButton
import com.kefe.app.ui.components.KefeSectionHeader
import com.kefe.app.ui.components.KefeSegmentedControl
import com.kefe.app.ui.components.KefeSellBadge
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.components.KefeSkeletonCircle
import com.kefe.app.ui.components.KefeSlider
import com.kefe.app.ui.components.KefeStaleBanner
import com.kefe.app.ui.components.KefeStepDots
import com.kefe.app.ui.components.KefeSwipeableRow
import com.kefe.app.ui.components.KefeSwitch
import com.kefe.app.ui.components.KefeSwitchRow
import com.kefe.app.ui.components.KefeSyncChip
import com.kefe.app.ui.components.KefeTextButton
import com.kefe.app.ui.components.KefeTextField
import com.kefe.app.ui.components.SyncStatus
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Tasarim sistemi galerisi: uretilmis tum jetonlarin ve bilesenlerin tek
 * ekranda gozle dogrulandigi yuzey. Uretim ekrani DEGILDIR - burada amac
 * "donut kapali ring mi", "hedef halkasi kefe gibi mi duruyor", "marka isareti
 * swoosh'a benziyor mu" sorularini gorerek yanitlamak.
 *
 * Tum durum bu fonksiyonda tutulur: LazyColumn ogeleri ekrandan cikinca
 * ic durum sifirlanmasin diye state ogenin icine birakilmaz.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSystemGallery(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KefeTheme(darkTheme = darkTheme) {
        val colors = KefeTheme.colors
        val type = KefeTheme.type

        // --- Etkilesim durumu ---
        var markTrigger by remember { mutableStateOf(0) }
        var chipSelected by remember { mutableStateOf(true) }
        var segmentIndex by remember { mutableStateOf(0) }
        var periodIndex by remember { mutableStateOf(2) }
        var nameValue by remember { mutableStateOf("Çeyrek Altın") }
        var emptyValue by remember { mutableStateOf("") }
        var errorValue by remember { mutableStateOf("12,5,0") }
        var amountValue by remember { mutableStateOf("8") }
        var searchValue by remember { mutableStateOf("") }
        var switchOn by remember { mutableStateOf(true) }
        var switchRowOn by remember { mutableStateOf(false) }
        var radioIndex by remember { mutableStateOf(0) }
        var sliderValue by remember { mutableStateOf(50_000f) }
        var goldExpanded by remember { mutableStateOf(true) }
        var sheetVisible by remember { mutableStateOf(false) }
        var sheetStep by remember { mutableStateOf(1) }
        var sheetName by remember { mutableStateOf("") }
        var sheetAmount by remember { mutableStateOf("2") }

        // --- Grafik verileri: SampleData / SampleSeries'ten, bir kez hazirlanir ---
        val netWorthTotal = remember {
            SampleSeries.netWorthTotal.mapIndexed { i, v -> Point(i.toFloat(), v.toFloat()) }
        }
        val netWorthPrincipal = remember {
            SampleSeries.netWorthPrincipal.mapIndexed { i, v -> Point(i.toFloat(), v.toFloat()) }
        }
        val projectionActual = remember {
            SampleSeries.projectionActual.mapIndexed { i, v -> Point(i.toFloat(), v.toFloat()) }
        }
        val projectionForecast = remember {
            SampleSeries.projectionForecast.mapIndexed { i, v -> Point(i.toFloat(), v.toFloat()) }
        }
        val contributionMonths = remember {
            SampleSeries.galleryContributions.map { month ->
                MonthBar(
                    label = month.date.monthLabel(),
                    segments = month.slices.map { BarSegment(it.assetClass.color(), it.amount) },
                )
            }
        }
        // Ceyrek altin islem fiyatlari + guncel piyasa fiyati (SampleData.prices)
        val pricePoints = remember {
            val series = SampleData.quarterGoldTransactions.map { it.unitPrice } + 26_500.0
            series.mapIndexed { i, v -> Point(i.toFloat(), v.toFloat()) }
        }
        val priceTrades = remember {
            val buys = SampleData.quarterGoldTransactions.mapIndexed { i, tx ->
                TradeMarker(index = i, isBuy = tx.side == TradeSide.Buy)
            }
            // Son isaret ornek veride yok; ici bos elmasin da dogrulanabilmesi icin eklendi.
            buys + TradeMarker(index = SampleData.quarterGoldTransactions.size, isBuy = false)
        }

        val donutSlices = remember(colors) {
            SampleData.allocation.map { slice ->
                DonutSlice(
                    label = slice.assetClass.label(),
                    value = slice.value,
                    color = colors.assetClass(slice.assetClass.color()),
                )
            }
        }

        val galleryPositions = remember {
            listOf(
                "pos_bilezik22",
                "pos_anneanne",
                "pos_tte",
                "pos_gumus",
            ).mapNotNull { id -> SampleData.positions.firstOrNull { it.id == id } }
        }

        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                GalleryTopBar(darkTheme = darkTheme, onToggleTheme = onToggleTheme)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.x16,
                        end = Space.x16,
                        top = Space.x16,
                        bottom = Space.x40,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.x24),
                ) {

                    // --- 1. MARKA ---
                    item {
                        GallerySection("01", "Marka") {
                            Caption("Sarkma derinligi = hedef ilerlemesi. Hat %0'da bile gergin durur.")
                            ScrollRow {
                                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { p ->
                                    Labeled(Money.ratioOf(p.toDouble())) {
                                        KefeMark(progress = p, modifier = Modifier.size(80.dp))
                                    }
                                }
                            }

                            Caption("Sadelestirme esikleri: 32dp alti tavan basligi kalkar, 16dp'de yalniz yuk kalir.")
                            ScrollRow(alignment = Alignment.Bottom) {
                                listOf(64.dp, 48.dp, 32.dp, 24.dp, 16.dp).forEach { s ->
                                    Labeled("${s.value.toInt()}dp") {
                                        KefeMark(
                                            progress = KefeMarkStaticProgress,
                                            modifier = Modifier.size(s),
                                        )
                                    }
                                }
                            }

                            Caption("Logo kilitleri")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.x24),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KefeLogoHorizontal(
                                    modifier = Modifier.width(200.dp).height(56.dp),
                                    markSize = 44.dp,
                                )
                                KefeLogoVertical(
                                    modifier = Modifier.width(110.dp).height(96.dp),
                                    markSize = 44.dp,
                                )
                            }

                            Caption("Imza jesti: hedefi asar, iki salinimla oturur.")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.x16),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KefeAnimatedMark(
                                    progress = 0.72f,
                                    trigger = markTrigger,
                                    modifier = Modifier.size(80.dp),
                                )
                                KefeSecondaryButton(
                                    text = "Jesti oynat",
                                    onClick = { markTrigger += 1 },
                                )
                            }
                        }
                    }

                    // --- 2. RENK JETONLARI ---
                    item {
                        GallerySection("02", "Renk jetonları") {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.x12),
                                verticalArrangement = Arrangement.spacedBy(Space.x12),
                            ) {
                                colorTokens().forEach { (name, value) ->
                                    Swatch(name = name, color = value)
                                }
                            }

                            Caption("Varlık sınıfı paleti - donut, legend ve bar renkleri buradan gelir.")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.x12),
                                verticalArrangement = Arrangement.spacedBy(Space.x12),
                            ) {
                                AssetClass.entries.forEach { assetClass ->
                                    Swatch(
                                        name = assetClass.label(),
                                        color = colors.assetClass(assetClass.color()),
                                    )
                                }
                            }
                        }
                    }

                    // --- 3. TIPOGRAFI ---
                    item {
                        GallerySection("03", "Tipografi") {
                            TypeSpecimen("display", "40 / 48", type.display, "Kefe")
                            TypeSpecimen("h1", "28 / 34", type.h1, "Toplam birikim")
                            TypeSpecimen("h2", "22 / 28", type.h2, "Hedefe kalan")
                            TypeSpecimen("body", "15 / 21", type.body, "Çeyrek Altın · 8 adet")
                            TypeSpecimen("bodyStrong", "15 / 21", type.bodyStrong, "Çeyrek Altın")
                            TypeSpecimen("caption", "13 / 18", type.caption, "Fiyatlar 14:32'de güncellendi")
                            TypeSpecimen("micro", "11 / 15", type.micro, "Evdeki kasa")
                            TypeSpecimen(
                                "microLabel",
                                "11 / 14",
                                type.microLabel,
                                "toplam birikim".trUpper(),
                            )

                            KefeHairline()
                            Caption("tabular kanıtı: rakamlar sütun gibi hizalanmalı (tnum).")
                            Column(
                                modifier = Modifier.width(200.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(Space.x4),
                            ) {
                                listOf(3_180_400.0, 212_000.0, 58_000.0).forEach { amount ->
                                    Text(
                                        text = Money.tl(amount),
                                        style = type.h2.tabular(),
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    // --- 4. BUTONLAR ---
                    item {
                        GallerySection("04", "Butonlar") {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.x12),
                                verticalArrangement = Arrangement.spacedBy(Space.x12),
                            ) {
                                KefePrimaryButton("Varlık ekle", {}, leadingIcon = KefeIcons.Plus)
                                KefePrimaryButton("Varlık ekle", {}, enabled = false, leadingIcon = KefeIcons.Plus)
                                KefeSecondaryButton("Vazgeç", {})
                                KefeSecondaryButton("Vazgeç", {}, enabled = false)
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.x12),
                                verticalArrangement = Arrangement.spacedBy(Space.x12),
                            ) {
                                KefeTextButton("Tümünü gör", {})
                                KefeTextButton("Tümünü gör", {}, enabled = false)
                                KefeDestructiveTextButton("Sil", {}, leadingIcon = KefeIcons.Trash)
                                KefeDestructiveTextButton("Sil", {}, enabled = false, leadingIcon = KefeIcons.Trash)
                                KefeIconButton(KefeIcons.Refresh, "Yenile", {})
                                KefeIconButton(KefeIcons.Refresh, "Yenile", {}, enabled = false)
                            }
                        }
                    }

                    // --- 5. CIPLER ---
                    item {
                        GallerySection("05", "Çipler") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KefeChip(
                                    text = "Altın",
                                    selected = chipSelected,
                                    onClick = { chipSelected = !chipSelected },
                                    leadingIcon = KefeIcons.Gold,
                                )
                                KefeChip(
                                    text = "Gümüş",
                                    selected = !chipSelected,
                                    onClick = { chipSelected = !chipSelected },
                                    leadingIcon = KefeIcons.Silver,
                                )
                                KefeChip(text = "Devre dışı", selected = false, onClick = {}, enabled = false)
                            }

                            KefeSegmentedControl(
                                options = listOf("Değer", "Miktar", "Getiri"),
                                selectedIndex = segmentIndex,
                                onSelect = { segmentIndex = it },
                            )

                            KefePeriodChips(
                                selectedIndex = periodIndex,
                                onSelect = { periodIndex = it },
                            )
                        }
                    }

                    // --- 6. ROZETLER ---
                    item {
                        GallerySection("06", "Rozetler") {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                                verticalArrangement = Arrangement.spacedBy(Space.x8),
                            ) {
                                KefeManualBadge(icon = KefeIcons.Pencil)
                                KefePendingBadge()
                                KefeMainGoalBadge()
                                KefeBuyBadge()
                                KefeSellBadge()
                                KefeOwnerBadge()
                                KefeMemberBadge()
                            }

                            Caption("Değişim metni: renk asla tek sinyal değil, işaret ve ok her zaman var.")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.x20),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KefeDeltaText(
                                    percent = 14.9,
                                    decimals = 1,
                                    upIcon = KefeIcons.ArrowUpRight,
                                    downIcon = KefeIcons.ArrowDownRight,
                                )
                                KefeDeltaText(
                                    percent = -2.40,
                                    upIcon = KefeIcons.ArrowUpRight,
                                    downIcon = KefeIcons.ArrowDownRight,
                                )
                                KefeDeltaText(
                                    percent = 0.0,
                                    upIcon = KefeIcons.ArrowUpRight,
                                    downIcon = KefeIcons.ArrowDownRight,
                                )
                            }
                        }
                    }

                    // --- 7. ALANLAR ---
                    item {
                        GallerySection("07", "Alanlar") {
                            KefeTextField(
                                value = emptyValue,
                                onValueChange = { emptyValue = it },
                                label = "Varlık adı",
                                placeholder = "Örn. 22 Ayar Bilezik",
                                helper = "Listede bu adla görünür",
                            )
                            KefeTextField(
                                value = nameValue,
                                onValueChange = { nameValue = it },
                                label = "Varlık adı",
                            )
                            KefeTextField(
                                value = errorValue,
                                onValueChange = { errorValue = it },
                                label = "Birim fiyat",
                                error = "Geçerli bir tutar girin",
                            )
                            KefeAmountField(
                                value = amountValue,
                                onValueChange = { amountValue = it },
                                unitLabel = "adet",
                                onIncrement = { amountValue = stepAmount(amountValue, 1) },
                                onDecrement = { amountValue = stepAmount(amountValue, -1) },
                                minusIcon = KefeIcons.MinusSmall,
                                plusIcon = KefeIcons.PlusSmall,
                            )
                            KefeSearchField(
                                value = searchValue,
                                onValueChange = { searchValue = it },
                                placeholder = "Varlık ara",
                                searchIcon = KefeIcons.Search,
                                clearIcon = KefeIcons.Close,
                            )
                        }
                    }

                    // --- 8. ANAHTAR / RADYO / KAYDIRICI ---
                    item {
                        GallerySection("08", "Anahtar · radyo · kaydırıcı") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.x16),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KefeSwitch(checked = switchOn, onCheckedChange = { switchOn = it })
                                KefeSwitch(checked = !switchOn, onCheckedChange = { switchOn = !it })
                                KefeSwitch(checked = true, onCheckedChange = {}, enabled = false)
                            }

                            KefeSwitchRow(
                                title = "Hedeflere dahil et",
                                subtitle = "Kapalıyken bu varlık hedef ilerlemesine sayılmaz",
                                checked = switchRowOn,
                                onCheckedChange = { switchRowOn = it },
                            )

                            KefeHairline()

                            listOf(
                                "Tüm birikim" to "Hedef, toplam servetten beslenir",
                                "Sabit pay" to "Her ay belirli bir tutar ayrılır",
                            ).forEachIndexed { index, (title, subtitle) ->
                                KefeRadioRow(
                                    title = title,
                                    subtitle = subtitle,
                                    selected = radioIndex == index,
                                    onClick = { radioIndex = index },
                                )
                            }

                            KefeHairline()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "aylık katkı".trUpper(),
                                    style = type.microLabel,
                                    color = colors.onSurfaceMuted,
                                )
                                Text(
                                    text = Money.tl(sliderValue.toDouble()),
                                    style = type.bodyStrong.tabular(),
                                    color = colors.onSurface,
                                )
                            }
                            // 30.000-120.000 arasi 5.000 adim -> 18 aralik
                            KefeSlider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                valueRange = 30_000f..120_000f,
                                steps = 18,
                            )
                        }
                    }

                    // --- 9. ILERLEME ---
                    item {
                        GallerySection("09", "İlerleme") {
                            LabeledBar("KefeProgressBar · " + Money.ratio(41.0)) {
                                KefeProgressBar(progress = 0.41f)
                            }
                            LabeledBar("KefeProgressBarThin · " + Money.ratio(90.0)) {
                                KefeProgressBarThin(progress = 0.90f)
                            }
                            LabeledBar("KefeMilestoneBar · " + Money.ratio(41.0)) {
                                KefeMilestoneBar(progress = 0.41f)
                            }
                        }
                    }

                    // --- 10. LISTE ---
                    item {
                        GallerySection("10", "Liste") {
                            KefeSectionHeader(
                                dotColor = colors.gold,
                                title = "Altın",
                                total = Money.tl(1_845_000.0),
                                percent = Money.ratio(58.1, 1),
                                expanded = goldExpanded,
                                onToggle = { goldExpanded = !goldExpanded },
                                chevronIcon = KefeIcons.ChevronRight,
                            )

                            if (goldExpanded) {
                                galleryPositions.forEachIndexed { index, position ->
                                    val row: @Composable () -> Unit = {
                                        KefeListRow(
                                            title = position.name,
                                            subtitle = positionSubtitle(position),
                                            value = Money.tl(position.value),
                                            delta = position.dailyChangePercent,
                                            leadingIcon = assetIcon(position.assetClass),
                                            leadingTint = colors.assetClass(position.assetClass.color()),
                                            onClick = {},
                                            badges = {
                                                if (position.manualPrice) KefeManualBadge()
                                            },
                                        )
                                    }
                                    // Ucuncu satir kaydirilabilir sarmalayici icinde gosterilir
                                    if (index == 2) {
                                        KefeSwipeableRow(
                                            onEdit = {},
                                            onDelete = {},
                                            editIcon = KefeIcons.Pencil,
                                            deleteIcon = KefeIcons.Trash,
                                            content = row,
                                        )
                                    } else {
                                        row()
                                    }
                                }
                                Caption("Üçüncü satır KefeSwipeableRow içinde - sola kaydırınca aksiyonlar açılır.")
                            }
                        }
                    }

                    // --- 11. SERITLER ---
                    item {
                        GallerySection("11", "Şeritler") {
                            KefeStaleBanner(
                                text = "Fiyatlar 14 dakikadır güncellenmedi",
                                actionText = "Yenile",
                                onAction = {},
                                clockIcon = KefeIcons.Clock,
                            )
                            KefeOfflineBanner(
                                line1 = "Çevrimdışısın",
                                line2 = "Kayıtların bağlantı gelince eşitlenecek",
                                cloudOffIcon = KefeIcons.CloudOff,
                            )
                            KefeInfoBanner(
                                text = "Hariç tutulan varlıklar hedef ilerlemesine sayılmaz.",
                                icon = KefeIcons.Info,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KefeSyncChip(SyncStatus.Synced)
                                KefeSyncChip(SyncStatus.Pending)
                                KefeSyncChip(SyncStatus.Offline)
                            }
                        }
                    }

                    // --- 12. AVATAR / ISKELET / BOS DURUM ---
                    item {
                        GallerySection("12", "Avatar · iskelet · boş durum") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.x16),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KefeAvatar("V", 0, Sizes.avatarLarge)
                                KefeAvatar("A", 1, Sizes.avatarMedium)
                                KefeAvatar("V", 0, Sizes.avatarSmall)
                                KefeAvatarStack(
                                    members = listOf("V" to 0, "A" to 1),
                                    size = Sizes.avatarMedium,
                                )
                            }

                            KefeHairline()
                            Caption("İskelet: shimmer yok, nabız yok - hareket yalnız anlamlı geçişlere ayrılır.")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.x12),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KefeSkeletonCircle(size = 32.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(Space.x8)) {
                                    KefeSkeletonBlock(width = 160.dp, height = 14.dp)
                                    KefeSkeletonBlock(width = 96.dp, height = 11.dp)
                                }
                            }
                            KefeSkeletonBlock(height = 20.dp)

                            KefeHairline()
                            KefeEmptyState(
                                icon = KefeIcons.Wallet,
                                title = "Henüz varlık yok",
                                body = "İlk varlığını eklediğinde kefe dolmaya başlar.",
                                action = {
                                    KefePrimaryButton(
                                        text = "Varlık ekle",
                                        onClick = {},
                                        leadingIcon = KefeIcons.Plus,
                                    )
                                },
                            )
                        }
                    }

                    // --- 13. GRAFIKLER ---
                    item {
                        GallerySection("13", "Grafikler") {
                            Caption("Donut kapalı ring, hedef halkası açık yay + mil + kiriş: yan yana ayrışmalı.")
                            ScrollRow(alignment = Alignment.CenterVertically) {
                                KefeDonutChart(
                                    slices = donutSlices,
                                    centerLabel = "Altın",
                                    centerValue = Money.ratio(58.1),
                                )
                                KefeGoalRing(
                                    progress = 0.41f,
                                    centerPercent = Money.ratio(41.0),
                                    centerAmount = Money.tl(2_860_400.0) + " / " + Money.tl(7_800_000.0),
                                )
                            }

                            KefeDonutLegend(
                                slices = donutSlices,
                                total = SampleData.totals.totalValue,
                            )

                            KefeHairline()
                            Caption("Net değer: toplam + anapara, aradaki alan getiri; hedef çizgisinde eksen kırılır.")
                            KefeNetWorthChart(
                                total = netWorthTotal,
                                principal = netWorthPrincipal,
                                goalLine = 7_800_000.0,
                                goalLabel = "hedef",
                                selectedIndex = netWorthTotal.lastIndex,
                                selectedLabel = SampleSeries.netWorthLabels.last(),
                                selectedValue = Money.tl(SampleSeries.netWorthTotal.last()),
                            )

                            KefeHairline()
                            Caption("Projeksiyon: gerçekleşen dolu, tahmin kesikli.")
                            KefeProjectionChart(
                                actual = projectionActual,
                                forecast = projectionForecast,
                                goal = SampleSeries.projectionTarget,
                                goalLabel = "hedef",
                            )

                            KefeHairline()
                            Caption("Aylık katkı: Kasım boş - katkısız ay nötr taban çizgisiyle gösterilir.")
                            KefeStackedBarChart(months = contributionMonths)

                            KefeHairline()
                            Caption("Fiyat: alım dolu elmas, satım içi boş elmas - ayrım renkten bağımsız.")
                            KefePriceChart(
                                points = pricePoints,
                                trades = priceTrades,
                            )
                        }
                    }

                    // --- 14. ALT SAYFA ---
                    item {
                        GallerySection("14", "Alt sayfa") {
                            KefePrimaryButton(
                                text = "Alt sayfayı aç",
                                onClick = { sheetVisible = true },
                            )
                        }
                    }
                }
            }

            // Alt sayfa en ustte durur: kendi scrim'ini cizer.
            KefeBottomSheet(
                visible = sheetVisible,
                onDismiss = { sheetVisible = false },
                title = "Varlık ekle",
                subtitle = "Adım $sheetStep / 3",
                closeIcon = KefeIcons.Close,
                header = {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x20),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        KefeStepDots(count = 3, activeIndex = sheetStep - 1)
                    }
                },
                footer = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.x20),
                        horizontalArrangement = Arrangement.spacedBy(Space.x12),
                    ) {
                        KefeSecondaryButton(
                            text = "Geri",
                            onClick = { if (sheetStep > 1) sheetStep -= 1 },
                            enabled = sheetStep > 1,
                            modifier = Modifier.width(120.dp),
                        )
                        KefePrimaryButton(
                            text = if (sheetStep < 3) "Devam" else "Kaydet",
                            onClick = {
                                if (sheetStep < 3) sheetStep += 1 else sheetVisible = false
                            },
                            modifier = Modifier.width(160.dp),
                        )
                    }
                },
            ) {
                KefeTextField(
                    value = sheetName,
                    onValueChange = { sheetName = it },
                    label = "Varlık adı",
                    placeholder = "Örn. Çeyrek Altın",
                )
                Spacer(Modifier.height(Space.x16))
                KefeAmountField(
                    value = sheetAmount,
                    onValueChange = { sheetAmount = it },
                    unitLabel = "adet",
                    onIncrement = { sheetAmount = stepAmount(sheetAmount, 1) },
                    onDecrement = { sheetAmount = stepAmount(sheetAmount, -1) },
                    minusIcon = KefeIcons.MinusSmall,
                    plusIcon = KefeIcons.PlusSmall,
                )
                Spacer(Modifier.height(Space.x16))
                KefeInfoBanner(
                    text = "Fiyat serbest piyasadan otomatik çekilir.",
                    icon = KefeIcons.Info,
                )
            }
        }
    }
}

// --- Galeri iskeleti ---------------------------------------------------------

/** Sabit ust satir: marka adi + tema anahtari. Kaydirma ile birlikte gitmez. */
@Composable
private fun GalleryTopBar(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val colors = KefeTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.x16, vertical = Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KefeMark(
                progress = KefeMarkStaticProgress,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(Space.x10))
            Text(
                text = "Kefe · Tasarım Sistemi",
                style = KefeTheme.type.bodyStrong,
                color = colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            KefeSecondaryButton(
                text = if (darkTheme) "Açık tema" else "Koyu tema",
                onClick = onToggleTheme,
            )
        }
        KefeHairline()
    }
}

/** Bolum kabugu: microLabel baslik (trUpper) + ince ayirici + icerik. */
@Composable
private fun GallerySection(
    index: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KefeTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.x12),
    ) {
        Text(
            text = "$index · ${title.trUpper()}",
            style = KefeTheme.type.microLabel,
            color = colors.accent,
        )
        KefeHairline()
        content()
    }
}

/** Bolum ici aciklama satiri. */
@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = KefeTheme.type.micro,
        color = KefeTheme.colors.onSurfaceMuted,
    )
}

/** Dar ekranlarda kirpilmasin diye yatay kaydirilabilir siralama. */
@Composable
private fun ScrollRow(
    alignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.x16),
        verticalAlignment = alignment,
    ) {
        content()
    }
}

/** Ornegin altina micro etiket koyar. */
@Composable
private fun Labeled(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        content()
        Text(
            text = label,
            style = KefeTheme.type.micro.tabular(),
            color = KefeTheme.colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun LabeledBar(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        Text(
            text = label,
            style = KefeTheme.type.micro.tabular(),
            color = KefeTheme.colors.onSurfaceMuted,
        )
        content()
    }
}

/** Renk jetonu karesi + adi. Saydam jetonlar da gorunsun diye kenarlikli. */
@Composable
private fun Swatch(
    name: String,
    color: Color,
    size: Dp = 44.dp,
) {
    Column(
        modifier = Modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(KefeShapes.boxSmall)
                .background(color)
                .border(Sizes.hairline, KefeTheme.colors.outline, KefeShapes.boxSmall),
        )
        Text(
            text = name,
            style = KefeTheme.type.micro,
            color = KefeTheme.colors.onSurfaceMuted,
        )
    }
}

/** Tipografi ornegi: sol tarafta ad + olcu, sagda ornek metin. */
@Composable
private fun TypeSpecimen(
    name: String,
    metrics: String,
    style: TextStyle,
    sample: String,
) {
    val colors = KefeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(96.dp)) {
            Text(text = name, style = KefeTheme.type.micro, color = colors.onSurface)
            Text(
                text = metrics,
                style = KefeTheme.type.micro.tabular(),
                color = colors.onSurfaceMuted,
            )
        }
        Spacer(Modifier.width(Space.x12))
        Text(text = sample, style = style, color = colors.onSurface)
    }
}

// --- Yardimcilar -------------------------------------------------------------

/** Tema jetonlarinin adli listesi - degerler o an aktif paletten okunur. */
@Composable
private fun colorTokens(): List<Pair<String, Color>> {
    val c = KefeTheme.colors
    return listOf(
        "surface" to c.surface,
        "surfaceElevated" to c.surfaceElevated,
        "surfaceSunken" to c.surfaceSunken,
        "onSurface" to c.onSurface,
        "onSurfaceMuted" to c.onSurfaceMuted,
        "accent" to c.accent,
        "accentMuted" to c.accentMuted,
        "onAccent" to c.onAccent,
        "positive" to c.positive,
        "negative" to c.negative,
        "warning" to c.warning,
        "outline" to c.outline,
        "syncOk" to c.syncOk,
        "syncPending" to c.syncPending,
        "syncOffline" to c.syncOffline,
        "avatarA" to c.avatarA,
        "avatarB" to c.avatarB,
        "staleBannerBg" to c.staleBannerBg,
        "staleBannerBorder" to c.staleBannerBorder,
        "buyBadgeBg" to c.buyBadgeBg,
        "sellBadgeBg" to c.sellBadgeBg,
        "pendingBadgeBg" to c.pendingBadgeBg,
        "scrim" to c.scrim,
    )
}

private fun assetIcon(assetClass: AssetClass): ImageVector = when (assetClass) {
    AssetClass.Gold -> KefeIcons.Gold
    AssetClass.Silver -> KefeIcons.Silver
    AssetClass.Fx -> KefeIcons.Fx
    AssetClass.Fund -> KefeIcons.Fund
    AssetClass.Cash -> KefeIcons.Cash
}

/** Satir alt metni: miktar + birim. Nakit/dovizde birim yazilmaz. */
private fun positionSubtitle(position: Position): String {
    val unitLabel = position.unit.label()
    val decimals = if (position.unit == QuantityUnit.Gram) 1 else 0
    return if (unitLabel.isEmpty()) {
        Money.number(position.quantity, decimals)
    } else {
        Money.quantity(position.quantity, unitLabel, decimals)
    }
}

/** Miktar alanindaki +/- adimi. Sayiya cevrilemezse degisiklik yapilmaz. */
private fun stepAmount(current: String, delta: Int): String {
    val parsed = current.trim().toIntOrNull() ?: return current
    return (parsed + delta).coerceAtLeast(0).toString()
}

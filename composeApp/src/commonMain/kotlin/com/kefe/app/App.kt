package com.kefe.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.kefe.app.di.appModule
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.navigation.ActivityKey
import com.kefe.app.navigation.AssetDetailKey
import com.kefe.app.navigation.AssetsKey
import com.kefe.app.navigation.GalleryKey
import com.kefe.app.navigation.GoalDetailKey
import com.kefe.app.navigation.GoalsKey
import com.kefe.app.navigation.KefeKey
import com.kefe.app.navigation.LoginKey
import com.kefe.app.navigation.MarketKey
import com.kefe.app.navigation.OnboardingKey
import com.kefe.app.navigation.SettingsKey
import com.kefe.app.navigation.ShareKey
import com.kefe.app.navigation.SummaryKey
import com.kefe.app.navigation.desktopDestinations
import com.kefe.app.navigation.topLevelDestinations
import com.kefe.app.ui.brand.KefeSplash
import com.kefe.app.ui.components.KefeBottomNav
import com.kefe.app.ui.components.SyncStatus
import com.kefe.app.ui.gallery.DesignSystemGallery
import com.kefe.app.ui.layout.KefeNavItem
import com.kefe.app.ui.layout.KefeNavigationRail
import com.kefe.app.ui.layout.KefeSideNavigation
import com.kefe.app.ui.layout.ProvideWindowSize
import com.kefe.app.ui.layout.WindowSize
import com.kefe.app.ui.mvi.CollectEffects
import com.kefe.app.ui.screens.account.ActivityScreen
import com.kefe.app.ui.screens.account.ActivityViewModel
import com.kefe.app.ui.screens.account.LoginIntent
import com.kefe.app.ui.screens.account.LoginScreen
import com.kefe.app.ui.screens.account.LoginViewModel
import com.kefe.app.ui.screens.account.OnboardingPageCount
import com.kefe.app.ui.screens.account.OnboardingScreen
import com.kefe.app.ui.screens.account.SettingsEffect
import com.kefe.app.ui.screens.account.SettingsIntent
import com.kefe.app.ui.screens.account.SettingsScreen
import com.kefe.app.ui.screens.account.SettingsUiState
import com.kefe.app.ui.screens.account.SettingsViewModel
import com.kefe.app.ui.screens.account.ShareIntent
import com.kefe.app.ui.screens.account.ShareScreen
import com.kefe.app.ui.screens.account.ShareViewModel
import com.kefe.app.ui.screens.account.ThemeMode
import com.kefe.app.ui.screens.assets.AssetDetailEffect
import com.kefe.app.ui.screens.assets.AssetDetailScreen
import com.kefe.app.ui.screens.assets.AssetDetailViewModel
import com.kefe.app.ui.screens.assets.AssetsScreen
import com.kefe.app.ui.screens.assets.AssetsViewModel
import com.kefe.app.ui.screens.goals.GoalDetailScreen
import com.kefe.app.ui.screens.goals.GoalDetailViewModel
import com.kefe.app.ui.screens.goals.GoalEditSheet
import com.kefe.app.ui.screens.goals.GoalsIntent
import com.kefe.app.ui.screens.goals.GoalsScreen
import com.kefe.app.ui.screens.goals.GoalsViewModel
import com.kefe.app.ui.screens.market.MarketScreen
import com.kefe.app.ui.screens.market.MarketViewModel
import com.kefe.app.ui.screens.summary.SummaryIntent
import com.kefe.app.ui.screens.summary.SummaryScreenAdaptive
import com.kefe.app.ui.screens.summary.SummaryViewModel
import com.kefe.app.ui.screens.transaction.AddTransactionEffect
import com.kefe.app.ui.screens.transaction.AddTransactionIntent
import com.kefe.app.ui.screens.transaction.AddTransactionSheet
import com.kefe.app.ui.screens.transaction.AddTransactionViewModel
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import kotlinx.coroutines.delay
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinConfiguration

/**
 * Karsiligi henuz olmayan satirlarin ortak yaniti.
 *
 * Dokununca hicbir sey olmamasi hata gibi gorunuyordu; tek cumle "burasi
 * calismiyor" ile "burasi henuz yok" arasindaki farki soyluyor.
 */
private const val NotReadyMessage = "Bu bölüm henüz hazır değil."

/**
 * [onReady] uygulamanin ilk gercek karesini cizmeye hazir oldugunu bildirir.
 * Android'de sistemin acilis penceresi bu ana kadar ekranda tutulur; masaustu ve
 * iOS varsayilanla gecer, oralarda karsiligi yok.
 */
@Composable
fun App(onReady: () -> Unit = {}) {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(appModule) }),
    ) {
        // Tema Ayarlar'dan yonetilir. SettingsViewModel kabukta tutulur ki
        // secim hem temayi cevirsin hem de Ayarlar ekranina geri yansisin -
        // onceden secim ekranin icinde kalip hicbir seyi degistirmiyordu.
        val settingsVm = koinViewModel<SettingsViewModel>()
        val settings by settingsVm.state.collectAsState()

        val systemDark = isSystemInDarkTheme()
        val dark = when (settings.themeMode) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.System -> systemDark
        }

        KefeTheme(darkTheme = dark) {
            ProvideWindowSize { windowSize ->
                KefeApp(
                    windowSize = windowSize,
                    settingsVm = settingsVm,
                    settings = settings,
                    darkTheme = dark,
                    onReady = onReady,
                )
            }
        }
    }
}

/**
 * Uygulama kabugu. Navigasyon kromu pencere genisligine gore degisir:
 *   Compact  - altta 4 sekme + one cikan Ekle
 *   Medium   - solda 92dp ray
 *   Expanded - solda 240dp genisletilmis nav + ust cubuk + sagda 320dp piyasa paneli
 * Ekranlarin kendisi bu kromu cizmez; yalniz icerigi verir.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun KefeApp(
    windowSize: WindowSize,
    settingsVm: SettingsViewModel,
    settings: SettingsUiState,
    darkTheme: Boolean,
    onReady: () -> Unit,
) {
    // Acilis ekrani KARAR VERILMEDEN cizilmez.
    //
    // Yigin once LoginKey ile kuruluyor, "acilis akisi gecilmis" bilgisi diskten
    // gelince duzeltiliyordu. Arada kalan karelerde giris ekrani goruntuye
    // giriyordu - kullanicinin gordugu "splash'ten sonra login parliyor" buydu.
    // Duzeltmeyi hizlandirmak yetmez; dogru olan, karar gelene kadar hicbir sey
    // cizmemek ve yigini DOGRU kokle kurmaktir.
    //
    // null = diske henuz bakilmadi.
    val onboardingVm = koinViewModel<SummaryViewModel>()
    val onboarded by onboardingVm.onboarded.collectAsState()

    // Marka animasyonu YALNIZ SOGUK ACILISTA oynar. Bayrak surec omurludur:
    // arka plandan geri donuste uygulama hemen gorunur, cunku her gecis icin iki
    // saniye beklemek gunde onlarca kez acilan bir uygulamada bedel olur.
    var splashDone by remember { mutableStateOf(SplashAlreadyPlayed) }

    // Tercihler de beklenir: kilit acik mi bilmeden ekran cizilirse ya kilitli
    // olmayan bir uygulama bir kare kilitli goruntu verir ya da tersi.
    if (onboarded == null || !settings.prefsLoaded || !splashDone) {
        // Diskten cevap gelene kadar cizecek bir sey yok; animasyon o beklemeyi
        // zaten dolduruyor, ikisi ARDISIK degil PARALEL yurur.
        KefeSplash(
            onFinished = {
                splashDone = true
                SplashAlreadyPlayed = true
            },
        )
        // Sistemin acilis penceresi ancak Compose bir sey cizebildiginde birakilir.
        LaunchedEffect(Unit) { onReady() }
        return
    }

    // Cihaz kilidi. Oturum ya da veri kapisi DEGIL - yalniz bu acilista bakiyeyi
    // gorunmez tutar; kullanici bir kez actiktan sonra uygulama kapanana kadar
    // tekrar sorulmaz.
    var unlockedThisLaunch by remember { mutableStateOf(false) }
    val locked = settings.biometricLock && !unlockedThisLaunch

    // Kilitliyken de kok LoginKey'dir: kilit ekrani o ekranin bir asamasi.
    val backStack = remember {
        NavBackStack<NavKey>(if (onboarded == true && !locked) SummaryKey else LoginKey)
    }
    var onboardingPage by remember { mutableStateOf(0) }
    var addSheetVisible by remember { mutableStateOf(false) }

    // Yazma hatasi kullaniciya SOYLENMELI: sessizce yutulursa girdigi islem
    // kaybolur ve kaydettigini sanir.
    var saveError by remember { mutableStateOf<String?>(null) }
    var addSheetSide by remember { mutableStateOf(TradeSide.Buy) }

    // Duzenlenen islemin kimligi; null ise sheet yeni kayit icin acilir.
    var addSheetEditId by remember { mutableStateOf<String?>(null) }

    fun openAddSheet(side: TradeSide = TradeSide.Buy) {
        addSheetSide = side
        addSheetEditId = null
        addSheetVisible = true
    }

    fun openEditSheet(transactionId: String) {
        addSheetEditId = transactionId
        addSheetVisible = true
    }

    var searchQuery by remember { mutableStateOf("") }

    fun goTo(key: KefeKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    fun goBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Sekme degisimi yigin buyutmez: koke doner, sonra sekmeyi acar. */
    fun selectTab(key: KefeKey) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (backStack.firstOrNull() != key) backStack[0] = key
    }

    // Kabuk (nav, ust cubuk, piyasa paneli) portfoy ozetinden beslenir.
    val summaryVm = koinViewModel<SummaryViewModel>()
    val summary by summaryVm.state.collectAsState()

    /** Giris/onboarding bitti: yigin sifirlanir, geri tusu girise donmez. */
    fun enterApp() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack[0] = SummaryKey
        summaryVm.markOnboarded()
    }

    // Ayarlar etkileri kabukta karsilanir: silme sonrasi yigini sifirlamak ve
    // seride mesaj gostermek ekranin isi degil.
    CollectEffects(settingsVm.effects) { effect ->
        when (effect) {
            SettingsEffect.AllDataDeleted -> {
                saveError = "Tüm veriler silindi."
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                backStack[0] = LoginKey
            }
            is SettingsEffect.DeleteFailed -> saveError = effect.message
            SettingsEffect.NotReady -> saveError = NotReadyMessage

            // Cikis yigini da sifirlar: geri tusuyla ayarlara donebilmek,
            // oturumu kapatmis birine hala hesap ekranini gostermek olurdu.
            SettingsEffect.SignedOut -> {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                backStack[0] = LoginKey
            }

            // Paylasim sayfasi acildi; dosyanin nereye gittigine kullanici karar
            // verir, biz "kaydedildi" diyemeyiz.
            SettingsEffect.BackupReady -> saveError = "Yedek hazır — kaydetmek için bir yer seçin."
            SettingsEffect.Restored -> saveError = "Yedek geri yüklendi."
            is SettingsEffect.BackupFailed -> saveError = effect.message
        }
    }

    // Bir kez girildiyse giris ekrani ATLANIR.
    //
    // Giris zorunlu degildir: Kefe tek kisilik ve cevrimdisi de tam calisir,
    // hesap yalniz senkron ve paylasim icin gerekir. Bu yuzden kapiyi oturum
    // degil, "acilis akisi gecildi mi" bayragi tutar.
    //
    // Yigin dogru kokle kuruldugu icin burada duzeltilecek bir sey kalmaz; bu
    // etki yalniz oturum ACILDIKTAN sonra (kod dogrulanip bayrak yazilinca)
    // devreye girer.
    // KILIT BU ETKIYI DURDURUR. Aksi halde kilitli acilista bu satir kullaniciyi
    // dogruca Ozet'e aliyordu: kilit ekrani hic gorunmuyor, sistem istemi zaten
    // acilmis uygulamanin ustune biniyor ve istemden vazgecen kullanici arkada
    // bekleyen bakiyeyi buluyordu. Kilit, kilit olmaktan cikiyordu.
    LaunchedEffect(onboarded, locked) {
        if (onboarded == true && !locked && backStack.firstOrNull() == LoginKey) enterApp()
    }

    // Hedef duzenleme sheet'i KABUKTA yasar: hem Hedefler listesinden hem de
    // Hedef Detayi'ndan acilabilmesi gerekiyor. Ekranin icine gomulu oldugunda
    // detaydan acmak yapisal olarak mumkun degildi.
    val goalsVm = koinViewModel<GoalsViewModel>()
    val goalsState by goalsVm.state.collectAsState()

    val current = backStack.firstOrNull()
    // Giris ve onboarding'da navigasyon kromu cizilmez.
    val inShell = current != LoginKey && current != OnboardingKey
    val navItems = if (windowSize.isExpanded) desktopDestinations else topLevelDestinations
    val navIndex = navItems.indexOfFirst { it.key == current }.coerceAtLeast(0)
    val members = summary.members.mapIndexed { index, m -> m.initials to index }
    // Devam eden bir istek her seyi yener. Once Offline ilk sirada bakiliyordu ve
    // istek YOLDAYKEN bile "Çevrimdışı" yaziyordu - kullanicinin gordugu ilk sey
    // buydu, hem de tam calisan bir agda.
    val syncStatus = when {
        summary.refreshing -> SyncStatus.Pending
        summary.freshness == PriceFreshness.Loading -> SyncStatus.Pending
        summary.freshness == PriceFreshness.Offline -> SyncStatus.Offline
        else -> SyncStatus.Synced
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {

            if (inShell && windowSize.isExpanded) {
                KefeSideNavigation(
                    brandTitle = "Kefe",
                    brandSubtitle = summary.portfolioName,
                    onBrandClick = {},
                    items = navItems.map { destination ->
                        KefeNavItem(
                            label = destination.label,
                            icon = destination.icon,
                            badgeCount = when (destination.key) {
                                AssetsKey -> summary.positionCount.takeIf { it > 0 }
                                GoalsKey -> summary.openGoalCount.takeIf { it > 0 }
                                else -> null
                            },
                        )
                    },
                    selectedIndex = navIndex,
                    onSelect = { selectTab(navItems[it].key) },
                    onAdd = { openAddSheet() },
                    members = members,
                    memberNames = summary.members.joinToString(", ") { it.name },
                    syncStatus = syncStatus,
                    syncLine = summary.syncLine,
                    modifier = Modifier.fillMaxHeight(),
                )
            } else if (inShell && windowSize.isMedium) {
                KefeNavigationRail(
                    items = navItems.map { KefeNavItem(it.label, it.icon) },
                    selectedIndex = navIndex,
                    onSelect = { selectTab(navItems[it].key) },
                    onAdd = { openAddSheet() },
                    members = members,
                    syncStatus = syncStatus,
                    modifier = Modifier.fillMaxHeight(),
                )
            }

            // Ust cubuk ve sag piyasa paneli EKRANIN kendi parcasidir (bkz.
            // SummaryScreenDesktop) - kabuk yalniz sol navigasyonu cizer.
            // Ikisini de burada cizmek ayni bileseni iki kez basiyordu.
            Column(Modifier.weight(1f)) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.weight(1f),
                    onBack = { goBack() },
                    // SEKME GECISI ANINDA OLUR.
                    //
                    // Varsayilan capraz solmada cikan ve giren ekran bir sure
                    // AYNI ANDA cizilir; ekranlarin zemini saydam oldugu icin
                    // ikisi ust uste binip okunuyordu - Ozet'in uzerinde
                    // Hedefler'in "Hazir oneriler" cipleri hayalet gibi
                    // gorunuyordu. Koyu temada daha belirgin, cunku karisan
                    // metin dusuk kontrastli griye duser. Kullanicinin
                    // "titreme" dedigi sey buydu; tekrar-besteleme degil.
                    //
                    // Alt navigasyonda sekmeler arasi animasyon zaten beklenen
                    // bir sey degil - Android'in kendi davranisi da anidir.
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    predictivePopTransitionSpec = {
                        EnterTransition.None togetherWith ExitTransition.None
                    },
                    entryProvider = entryProvider {
                        entry<LoginKey> {
                            val vm = koinViewModel<LoginViewModel>()
                            val state by vm.state.collectAsState()
                            // Kod dogrulanir dogrulanmaz iceri gireriz; ekranda
                            // ayrica "devam et" dedirtmek bos bir adim olurdu.
                            LaunchedEffect(state.signedIn) {
                                if (state.signedIn) enterApp()
                            }
                            // Kilitliyse giris ekrani KILIT asamasiyla acilir.
                            // Ayri bir gezinme girdisi degil: kilit, hesap girisi
                            // gibi bir kapi degil, ayni ekranin bir hali.
                            LaunchedEffect(locked) {
                                if (locked) vm.onIntent(LoginIntent.Lock)
                            }
                            // Kilit acilinca uygulama gorunur. unlockedThisLaunch
                            // kabukta yasar: ekran dolasirken tekrar sorulmaz.
                            LaunchedEffect(state.unlocked) {
                                if (state.unlocked) {
                                    unlockedThisLaunch = true
                                    enterApp()
                                }
                            }
                            ScreenSurface {
                                LoginScreen(
                                    state = state,
                                    onIntent = vm::onIntent,
                                    onStartOnboarding = {
                                        onboardingPage = 0
                                        goTo(OnboardingKey)
                                    },
                                    onEnterApp = { enterApp() },
                                )
                            }
                        }

                        entry<OnboardingKey> {
                            // Tanitim sayfalari tek gezinme girdisidir; geri tusu
                            // bunu bilmedigi icin ucuncu sayfadan basilinca uc
                            // sayfayi birden atlayip giris ekranina donuyordu.
                            // Ilk sayfada devre disi kalir - orada geri gitmek
                            // gercekten giris ekranina donmek demektir.
                            BackHandler(enabled = onboardingPage > 0) { onboardingPage-- }
                            ScreenSurface {
                                OnboardingScreen(
                                    pageIndex = onboardingPage,
                                    onNext = {
                                        if (onboardingPage < OnboardingPageCount - 1) {
                                            onboardingPage++
                                        } else {
                                            enterApp()
                                        }
                                    },
                                    onSkip = { enterApp() },
                                )
                            }
                        }

                        entry<SummaryKey> {
                            // Ozet ContentWidth'ten gecmez (kendi masaustu
                            // duzenini cizer) ama opak zemine yine ihtiyaci var.
                            ScreenSurface {
                                SummaryScreenAdaptive(
                                    state = summary,
                                    onIntent = summaryVm::onIntent,
                                    onOpenGoal = { goTo(GoalDetailKey(it)) },
                                    onOpenGoals = { selectTab(GoalsKey) },
                                    onOpenActivity = { goTo(ActivityKey) },
                                    onOpenMarket = { goTo(MarketKey) },
                                    onAddAsset = { openAddSheet() },
                                    marketRows = summary.marketRows,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    onOpenMarketRow = { goTo(MarketKey) },
                                )
                            }
                        }

                        entry<AssetsKey> {
                            val vm = koinViewModel<AssetsViewModel>()
                            val state by vm.state.collectAsState()
                            ContentWidth {
                                AssetsScreen(
                                    state = state,
                                    onIntent = vm::onIntent,
                                    onOpenPosition = { goTo(AssetDetailKey(it)) },
                                )
                            }
                        }

                        entry<AssetDetailKey> { key ->
                            val vm = koinViewModel<AssetDetailViewModel> {
                                parametersOf(key.positionId)
                            }
                            val state by vm.state.collectAsState()

                            // Son islem silinince varlik listeden duser; ekranda
                            // kalmak kullaniciyi "Varlik bulunamadi" bos
                            // durumunda birakiyordu.
                            CollectEffects(vm.effects) { effect ->
                                when (effect) {
                                    AssetDetailEffect.PositionGone -> goBack()
                                }
                            }

                            ContentWidth {
                                AssetDetailScreen(
                                    state = state,
                                    onIntent = vm::onIntent,
                                    onBack = { goBack() },
                                    onEditTransaction = { openEditSheet(it) },
                                    onAddBuy = { openAddSheet(TradeSide.Buy) },
                                    onAddSell = { openAddSheet(TradeSide.Sell) },
                                )
                            }
                        }

                        entry<GoalsKey> {
                            ContentWidth {
                                GoalsScreen(
                                    state = goalsState,
                                    onIntent = goalsVm::onIntent,
                                    onOpenGoal = { goTo(GoalDetailKey(it)) },
                                )
                            }
                        }

                        entry<GoalDetailKey> { key ->
                            val vm = koinViewModel<GoalDetailViewModel> { parametersOf(key.goalId) }
                            val state by vm.state.collectAsState()
                            ContentWidth {
                                GoalDetailScreen(
                                    state = state,
                                    onIntent = vm::onIntent,
                                    onBack = { goBack() },
                                    onEdit = { goalsVm.onIntent(GoalsIntent.EditGoal(key.goalId)) },
                                )
                            }
                        }

                        entry<MarketKey> {
                            val vm = koinViewModel<MarketViewModel>()
                            val state by vm.state.collectAsState()
                            ContentWidth {
                                MarketScreen(
                                    state = state,
                                    onIntent = vm::onIntent,
                                    onBack = { goBack() },
                                )
                            }
                        }

                        entry<ActivityKey> {
                            val vm = koinViewModel<ActivityViewModel>()
                            val state by vm.state.collectAsState()
                            ContentWidth {
                                ActivityScreen(
                                    state = state,
                                    onIntent = vm::onIntent,
                                    onBack = { goBack() },
                                    onAddTransaction = { openAddSheet() },
                                )
                            }
                        }

                        entry<ShareKey> {
                            val vm = koinViewModel<ShareViewModel>()
                            val state by vm.state.collectAsState()
                            ContentWidth {
                                ShareScreen(
                                    state = state,
                                    // Sistem paylasim sayfasi platforma ozgu ve
                                    // paylasilacak gercek bir davet baglantisi
                                    // henuz yok - sessiz kalmak yerine soyler.
                                    onIntent = { intent ->
                                        if (intent == ShareIntent.ShareLink) {
                                            saveError = NotReadyMessage
                                        } else {
                                            vm.onIntent(intent)
                                        }
                                    },
                                    onBack = { goBack() },
                                )
                            }
                        }

                        entry<SettingsKey> {
                            ContentWidth {
                                SettingsScreen(
                                    state = settings,
                                    onIntent = settingsVm::onIntent,
                                    onOpenShare = { goTo(ShareKey) },
                                    onOpenGallery = { goTo(GalleryKey) },
                                )
                            }
                        }

                        entry<GalleryKey> {
                            ContentWidth {
                                DesignSystemGallery(
                                    darkTheme = darkTheme,
                                    onToggleTheme = {
                                        settingsVm.onIntent(
                                            SettingsIntent.SelectTheme(
                                                if (darkTheme) ThemeMode.Light else ThemeMode.Dark,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    },
                )

                if (inShell && windowSize.isCompact) {
                    KefeBottomNav(
                        selected = navIndex,
                        onSelect = { selectTab(navItems[it].key) },
                        onAdd = { openAddSheet() },
                    )
                }
            }
        }

        // Hedef duzenleme sheet'i her ekranin ustunde cizilir.
        GoalEditSheet(state = goalsState.editor, onIntent = goalsVm::onIntent)

        if (addSheetVisible) {
            val addVm = koinViewModel<AddTransactionViewModel>()
            val addState by addVm.state.collectAsState()

            // Duzenlemede alan degerleri kayittan gelir - alis/satis dahil.
            // Yeni kayitta form sifirlanir: ViewModel sheet kapaninca olmedigi
            // icin bir onceki acilisin alanlari duruyordu.
            LaunchedEffect(addSheetSide, addSheetEditId) {
                val editId = addSheetEditId
                if (editId != null) {
                    addVm.onIntent(AddTransactionIntent.EditTransaction(editId))
                } else {
                    addVm.onIntent(AddTransactionIntent.StartNew(addSheetSide))
                }
            }

            // Kaydetme sonucu bir OLAY, durum degil: bayrak olarak tutulsaydi
            // tuketildikten sonra elle temizlenmesi gerekirdi.
            CollectEffects(addVm.effects) { effect ->
                when (effect) {
                    AddTransactionEffect.Saved -> addSheetVisible = false
                    is AddTransactionEffect.SaveFailed -> saveError = effect.message
                }
            }

            // Sheet masaustunde icerik genisligiyle sinirlanir ve ortalanir:
            // 1440px'e yayilinca varlik turu kartlari 470px'e cikiyordu. Scrim
            // sheet'in KENDI icinde oldugu ve onunla birlikte daraldigi icin
            // yanlarda kalan bosluk burada karartilir.
            Row(Modifier.fillMaxSize()) {
                SheetSideScrim { addSheetVisible = false }
                AddTransactionSheet(
                    state = addState,
                    onIntent = addVm::onIntent,
                    onDismiss = { addSheetVisible = false },
                    modifier = Modifier.widthIn(max = Sizes.contentMaxWidth),
                )
                SheetSideScrim { addSheetVisible = false }
            }
        }

        // Seritler KENDILIGINDEN kapanir.
        //
        // Once yalniz "Kapat" ile gidiyorlardi ve ekranin altinda kalici olarak
        // duruyorlardi: acilan her sheet ve onay kutusunun uzerine biniyor,
        // uygulama bozuk gorunuyordu. Bilgi bir kez okunur; okunmadiysa da
        // kullaniciyi kilitlemez.
        saveError?.let { message ->
            AutoDismissBanner(message = message, onDismiss = { saveError = null })
        }

        // Basarisiz fiyat yenilemesi. Sessiz kalinca basarili yenilemeden ayirt
        // edilemiyordu ve "yenileme calismiyor" gibi gorunuyordu.
        summary.refreshError?.let { message ->
            AutoDismissBanner(
                message = message,
                onDismiss = { summaryVm.onIntent(SummaryIntent.DismissRefreshError) },
            )
        }

        // Kisitlanan yenileme. Hata DEGIL - vurgu renginde cizilir; kullanici
        // dogru bir sey yapti, elindeki fiyat zaten taze.
        summary.refreshNotice?.let { message ->
            AutoDismissBanner(
                message = message,
                onDismiss = { summaryVm.onIntent(SummaryIntent.DismissRefreshNotice) },
                tone = KefeTheme.colors.accent,
            )
        }
    }
}

/**
 * Yazma hatasi seridi - her seyin ustunde, altta.
 *
 * Kendiliginden KAYBOLMAZ: kullanicinin girdigi islem kaydedilemedi, bunu
 * kacirmamali. Kapatmayi kendisi secer.
 */
@Composable
private fun BoxScope.ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    // Ton PARAMETRE: her serit hata degil. Kisitlanan yenileme bir bilgidir ve
    // kirmizi cizilirse kullanici yanlis bir sey yaptigini sanir.
    tone: Color? = null,
) {
    val c = KefeTheme.colors
    val accentTone = tone ?: c.negative
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(Space.x16)
            .widthIn(max = Sizes.formMaxWidth)
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, accentTone, KefeShapes.card)
            .padding(horizontal = Space.x16, vertical = Space.x12),
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = KefeTheme.type.caption,
            color = c.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Kapat",
            style = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = accentTone,
            modifier = Modifier
                .clip(KefeShapes.button)
                .clickable(onClick = onDismiss)
                .padding(horizontal = Space.x8, vertical = Space.x4),
        )
    }
}

/**
 * Ekran icerigini [Sizes.contentMaxWidth] ile sinirlar. Ozet DISINDAKI her
 * ekran bundan gecer: sinirsiz kalinca satirin etiketi solda, tutari sagda
 * kalip arada ~900px bosluk olusuyor. Ozet kendi masaustu duzenini (nav +
 * icerik + piyasa paneli) kendi cizdigi icin disarida birakildi.
 *
 * Kutu BASA hizalidir; ortalansa sekme degistirince baslik yatay olarak ziplardi.
 */
@Composable
private fun ContentWidth(content: @Composable () -> Unit) {
    ScreenSurface {
        Box(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxSize()) {
            content()
        }
    }
}

/**
 * Ekranin OPAK zemini.
 *
 * Gecis sirasinda cikan ve giren ekran bir sure ayni anda cizilir. Ekranlarin
 * kendi zemini yoktu, ikisi de saydamdi; ust uste binip birbirinin icinden
 * okunuyorlardi - Ozet'in uzerinde Hedefler'in cipleri hayalet gibi
 * gorunuyordu. Koyu temada daha belirgindi, cunku karisan metin dusuk
 * kontrastli griye dusuyor. Kullanicinin "titreme" dedigi seyin sebebi buydu.
 *
 * Zemin opak olunca ustteki ekran alttakini tamamen ortuyor.
 */
@Composable
private fun ScreenSurface(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(KefeTheme.colors.surface)) {
        content()
    }
}

/** Sheet daraldiginda yanda kalan bosluk: karartir ve dokununca sheet'i kapatir. */
@Composable
private fun RowScope.SheetSideScrim(onDismiss: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(KefeTheme.colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}

/** Serit ekranda kaldigi sure. Okunacak kadar uzun, engel olmayacak kadar kisa. */
private const val BannerVisibleMillis = 4_000L

/**
 * Kendiliginden kapanan bilgi seridi.
 *
 * [message] her degistiginde sayac bastan baslar; art arda gelen iki bildirim
 * birbirinin suresini yemez.
 */
@Composable
private fun BoxScope.AutoDismissBanner(
    message: String,
    onDismiss: () -> Unit,
    tone: Color? = null,
) {
    // rememberUpdatedState olmadan, kapatma islevi her bestelemede degisirse
    // bekleme yeniden baslar ve serit hic kapanmayabilir.
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        delay(BannerVisibleMillis)
        dismiss()
    }
    ErrorBanner(message = message, onDismiss = onDismiss, tone = tone)
}

/**
 * Marka animasyonu bu surecte oynadi mi.
 *
 * Compose durumu degil, SUREC durumu: Activity yeniden yaratilsa da (ekran
 * donmesi, tema degisimi) animasyon tekrar oynamamali. Yalniz uygulama gercekten
 * kapanip acildiginda sifirlanir.
 */
private var SplashAlreadyPlayed: Boolean = false

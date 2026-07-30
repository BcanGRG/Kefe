package com.kefe.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.kefe.app.data.sync.SyncCoordinator
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
import com.kefe.app.navigation.ProfileSetupKey
import com.kefe.app.navigation.SettingsKey
import com.kefe.app.navigation.ProfilesKey
import com.kefe.app.navigation.SummaryKey
import com.kefe.app.navigation.desktopDestinations
import com.kefe.app.navigation.topLevelDestinations
import com.kefe.app.ui.brand.KefeSplash
import com.kefe.app.ui.components.KefeBottomNav
import com.kefe.app.ui.components.KefeAutoDismissBanner
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
import com.kefe.app.ui.screens.account.LoginStage
import com.kefe.app.ui.screens.account.LoginViewModel
import com.kefe.app.ui.screens.account.OnboardingPageCount
import com.kefe.app.ui.screens.account.OnboardingScreen
import com.kefe.app.ui.screens.account.ProfileSetupScreen
import com.kefe.app.ui.screens.account.ProfileSetupViewModel
import com.kefe.app.ui.screens.account.SettingsEffect
import com.kefe.app.ui.screens.account.SettingsIntent
import com.kefe.app.ui.screens.account.SettingsScreen
import com.kefe.app.ui.screens.account.SettingsUiState
import com.kefe.app.ui.screens.account.SettingsViewModel
import com.kefe.app.ui.screens.account.ProfilesScreen
import com.kefe.app.ui.screens.account.ProfilesViewModel
import com.kefe.app.ui.screens.account.ThemeMode
import com.kefe.app.ui.screens.assets.AssetDetailEffect
import com.kefe.app.ui.screens.assets.AssetDetailScreen
import com.kefe.app.ui.screens.assets.AssetDetailViewModel
import com.kefe.app.ui.screens.assets.AssetsScreen
import com.kefe.app.ui.screens.assets.AssetsViewModel
import com.kefe.app.ui.screens.goals.GoalDetailScreen
import com.kefe.app.ui.screens.goals.GoalDetailStage
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
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
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

        KefeTheme(darkTheme = dark, showCents = settings.showCents) {
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

    // Senkron/push kordinatoru. Girisliyken yerel degisimleri Supabase'e iter;
    // kendi surec-omurlu scope'unda calisir, start() idempotent (bir kez baslar).
    val syncCoordinator = koinInject<SyncCoordinator>()
    LaunchedEffect(Unit) { syncCoordinator.start() }

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

    // Acilistaki kok: giris yapilmamis ya da kilitliyse Login; profil secilmemisse
    // "bu telefon kimin"; aksi halde Ozet. Kilit ekrani Login'in bir asamasidir.
    val backStack = remember {
        val root: NavKey = when {
            onboarded != true || locked -> LoginKey
            settings.activeMemberId == null -> ProfileSetupKey
            else -> SummaryKey
        }
        NavBackStack<NavKey>(root)
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

    /**
     * Giris/onboarding bitti: yigin sifirlanir, geri tusu girise donmez.
     *
     * "Bu telefon kimin" adimi henuz gecilmediyse ONA gideriz: kayitlar bir
     * profile yazilacak, cihazin hangi profil oldugu bilinmeden ana ekrana
     * girmek erken olur.
     */
    fun enterApp() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack[0] = if (settings.activeMemberId == null) ProfileSetupKey else SummaryKey
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

            // Cikis artik giris ekranina ATMAZ: giris istege bagli, uygulama
            // cevrimdisi tam calisir. Kullanici Ayarlar'da kalir; Bulut bolumu
            // signedIn=false ile yeniden "Giriş yap" satirina doner.
            SettingsEffect.SignedOut -> {
                saveError = "Çıkış yapıldı — senkron kapatıldı."
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
    val inShell = current != LoginKey && current != OnboardingKey && current != ProfileSetupKey
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
                            val vmState by vm.state.collectAsState()

                            // LoginKey CIFT GOREVLI: acilis KILIDI (yigin koku iken)
                            // ve bulut GIRISI (Ayarlar'dan itilince). AYNI VM iki
                            // baglama da hizmet ettigi icin kilitten arta kalan durum
                            // (stage=Locked, unlocked=true) itilmis girise siziyordu:
                            // "Giriş yap" bir an kilit ekranini -dolayisiyla parmak izi
                            // istemini- acip, unlocked etkisiyle enterApp cagirip Ozet'e
                            // geri atiyordu. Cozum: itilmis LoginKey HER ZAMAN temiz
                            // SignIn gosterir; kilit kalintisini (stage/unlocked) yok
                            // sayar. Boylece ne kilit ekrani cizilir ne de enterApp
                            // tetiklenir - dogrudan e-posta/kod asamasi gelir.
                            val asRoot = backStack.firstOrNull() == LoginKey
                            val state = if (asRoot) vmState else vmState.copy(
                                stage = LoginStage.SignIn,
                                unlocked = false,
                                unlockError = null,
                            )

                            // Kod dogrulanir dogrulanmaz iceri gireriz; ekranda
                            // ayrica "devam et" dedirtmek bos bir adim olurdu.
                            LaunchedEffect(state.signedIn) {
                                if (state.signedIn) enterApp()
                            }
                            // Kilit YALNIZ kok iken: itilmis (Ayarlar'dan giris)
                            // LoginKey kilit istemez.
                            LaunchedEffect(locked, asRoot) {
                                if (locked && asRoot) vm.onIntent(LoginIntent.Lock)
                            }
                            // Kilit acilinca uygulama gorunur. unlockedThisLaunch
                            // kabukta yasar: ekran dolasirken tekrar sorulmaz. Itilmis
                            // giriste state.unlocked yukarida false'a zorlandigi icin
                            // burasi yalniz gercek acilis kilidinde calisir.
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

                        entry<ProfileSetupKey> {
                            val vm = koinViewModel<ProfileSetupViewModel>()
                            val profileState by vm.state.collectAsState()
                            ScreenSurface {
                                ProfileSetupScreen(
                                    state = profileState,
                                    onIntent = vm::onIntent,
                                    // Kaydedilince Ozet'e. activeMemberId yazildigi
                                    // icin enterApp artik ProfileSetup'a donmez.
                                    onDone = {
                                        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                        backStack[0] = SummaryKey
                                    },
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
                            // key = positionId: her varlik AYRI VM alir. Aksi halde
                            // Koin ayni tur icin ILK olusan VM'i (ilk positionId ile)
                            // tum AssetDetailKey girislerinde geri veriyordu - neye
                            // basilirsa ayni varlik (ilk acilan) aciliyordu.
                            val vm = koinViewModel<AssetDetailViewModel>(key = key.positionId) {
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
                            // key = goalId: her hedef AYRI VM alir. Aksi halde Koin
                            // ayni tur icin ILK olusan VM'i (ilk goalId ile) tum
                            // GoalDetailKey girislerinde geri veriyordu - hangi hedefe
                            // basilirsa ayni hedef aciliyordu.
                            val vm = koinViewModel<GoalDetailViewModel>(key = key.goalId) {
                                parametersOf(key.goalId)
                            }
                            val state by vm.state.collectAsState()
                            // Hedef silinince (detay Missing'e duser) elle geri
                            // donmek gerekmesin: kendiliginden listeye doner. AMA
                            // yalniz bir kez YUKLENDIYSE (Ready gorduyse) - aksi
                            // halde acilistaki gecici Missing "Hedef bulunamadı"yi
                            // parlatip geri atardi.
                            var wasReady by remember { mutableStateOf(false) }
                            LaunchedEffect(state.stage) {
                                if (state.stage == GoalDetailStage.Ready) wasReady = true
                                if (state.stage == GoalDetailStage.Missing && wasReady) goBack()
                            }
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

                        entry<ProfilesKey> {
                            val vm = koinViewModel<ProfilesViewModel>()
                            val state by vm.state.collectAsState()
                            ContentWidth {
                                ProfilesScreen(
                                    state = state,
                                    onIntent = vm::onIntent,
                                    onBack = { goBack() },
                                )
                            }
                        }

                        entry<SettingsKey> {
                            ContentWidth {
                                SettingsScreen(
                                    state = settings,
                                    onIntent = settingsVm::onIntent,
                                    onOpenShare = { goTo(ProfilesKey) },
                                    onOpenLogin = { goTo(LoginKey) },
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
            KefeAutoDismissBanner(message = message, onDismiss = { saveError = null })
        }

        // Basarisiz fiyat yenilemesi. Sessiz kalinca basarili yenilemeden ayirt
        // edilemiyordu ve "yenileme calismiyor" gibi gorunuyordu.
        summary.refreshError?.let { message ->
            KefeAutoDismissBanner(
                message = message,
                onDismiss = { summaryVm.onIntent(SummaryIntent.DismissRefreshError) },
            )
        }

        // Kisitlanan yenileme. Hata DEGIL - vurgu renginde cizilir; kullanici
        // dogru bir sey yapti, elindeki fiyat zaten taze.
        summary.refreshNotice?.let { message ->
            KefeAutoDismissBanner(
                message = message,
                onDismiss = { summaryVm.onIntent(SummaryIntent.DismissRefreshNotice) },
                tone = KefeTheme.colors.accent,
            )
        }
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

/**
 * Marka animasyonu bu surecte oynadi mi.
 *
 * Compose durumu degil, SUREC durumu: Activity yeniden yaratilsa da (ekran
 * donmesi, tema degisimi) animasyon tekrar oynamamali. Yalniz uygulama gercekten
 * kapanip acildiginda sifirlanir.
 */
private var SplashAlreadyPlayed: Boolean = false

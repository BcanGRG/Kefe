package com.kefe.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.kefe.app.navigation.GoalDetailKey
import com.kefe.app.navigation.GalleryKey
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
import com.kefe.app.ui.components.KefeBottomNav
import com.kefe.app.ui.components.SyncStatus
import com.kefe.app.ui.gallery.DesignSystemGallery
import com.kefe.app.ui.layout.KefeNavItem
import com.kefe.app.ui.layout.KefeNavigationRail
import com.kefe.app.ui.layout.KefeSideNavigation
import com.kefe.app.ui.layout.ProvideWindowSize
import com.kefe.app.ui.layout.WindowSize
import com.kefe.app.ui.screens.account.ActivityScreen
import com.kefe.app.ui.screens.account.ActivityViewModel
import com.kefe.app.ui.screens.account.LoginScreen
import com.kefe.app.ui.screens.account.LoginViewModel
import com.kefe.app.ui.screens.account.OnboardingPageCount
import com.kefe.app.ui.screens.account.OnboardingScreen
import com.kefe.app.ui.screens.account.SettingsIntent
import com.kefe.app.ui.screens.account.SettingsScreen
import com.kefe.app.ui.screens.account.SettingsUiState
import com.kefe.app.ui.screens.account.SettingsViewModel
import com.kefe.app.ui.screens.account.ShareScreen
import com.kefe.app.ui.screens.account.ThemeMode
import com.kefe.app.ui.screens.account.ShareViewModel
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
import com.kefe.app.ui.screens.transaction.AddTransactionIntent
import com.kefe.app.ui.screens.transaction.AddTransactionSheet
import com.kefe.app.ui.screens.transaction.AddTransactionViewModel
import com.kefe.app.ui.theme.KefeTheme
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinConfiguration

@Composable
fun App(darkTheme: Boolean = true) {
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
@Composable
private fun KefeApp(
    windowSize: WindowSize,
    settingsVm: SettingsViewModel,
    settings: SettingsUiState,
    darkTheme: Boolean,
) {
    // Uygulama girisle acilir. Oturum durumu simdilik BELLEKTE: kimlik dogrulama
    // katmani (Supabase) gelene kadar uygulama her acilista girise doner.
    val backStack = remember { NavBackStack<NavKey>(LoginKey) }
    var onboardingPage by remember { mutableStateOf(0) }
    var addSheetVisible by remember { mutableStateOf(false) }
    var addSheetSide by remember { mutableStateOf(TradeSide.Buy) }

    fun openAddSheet(side: TradeSide = TradeSide.Buy) {
        addSheetSide = side
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

    /** Giris/onboarding bitti: yigin sifirlanir, geri tusu girise donmez. */
    fun enterApp() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack[0] = SummaryKey
    }

    // Kabuk (nav, ust cubuk, piyasa paneli) portfoy ozetinden beslenir.
    val summaryVm = koinViewModel<SummaryViewModel>()
    val summary by summaryVm.state.collectAsState()

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
    val syncStatus = when (summary.freshness) {
        PriceFreshness.Offline -> SyncStatus.Offline
        else -> if (summary.refreshing) SyncStatus.Pending else SyncStatus.Synced
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
                    entryProvider = entryProvider {
                        entry<LoginKey> {
                            val vm = koinViewModel<LoginViewModel>()
                            val state by vm.state.collectAsState()
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

                        entry<OnboardingKey> {
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

                        entry<SummaryKey> {
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

                        entry<AssetsKey> {
                            val vm = koinViewModel<AssetsViewModel>()
                            val state by vm.state.collectAsState()
                            AssetsScreen(
                                state = state,
                                onIntent = vm::onIntent,
                                onOpenPosition = { goTo(AssetDetailKey(it)) },
                            )
                        }

                        entry<AssetDetailKey> { key ->
                            val vm = koinViewModel<AssetDetailViewModel> {
                                parametersOf(key.positionId)
                            }
                            val state by vm.state.collectAsState()
                            AssetDetailScreen(
                                state = state,
                                onIntent = vm::onIntent,
                                onBack = { goBack() },
                                onEditTransaction = {},
                                onAddBuy = { openAddSheet(TradeSide.Buy) },
                                onAddSell = { openAddSheet(TradeSide.Sell) },
                                onOpenMenu = {},
                            )
                        }

                        entry<GoalsKey> {
                            GoalsScreen(
                                state = goalsState,
                                onIntent = goalsVm::onIntent,
                                onOpenGoal = { goTo(GoalDetailKey(it)) },
                            )
                        }

                        entry<GoalDetailKey> { key ->
                            val vm = koinViewModel<GoalDetailViewModel> { parametersOf(key.goalId) }
                            val state by vm.state.collectAsState()
                            GoalDetailScreen(
                                state = state,
                                onIntent = vm::onIntent,
                                onBack = { goBack() },
                                onEdit = { goalsVm.onIntent(GoalsIntent.EditGoal(key.goalId)) },
                            )
                        }

                        entry<MarketKey> {
                            val vm = koinViewModel<MarketViewModel>()
                            val state by vm.state.collectAsState()
                            MarketScreen(
                                state = state,
                                onIntent = vm::onIntent,
                                onBack = { goBack() },
                            )
                        }

                        entry<ActivityKey> {
                            val vm = koinViewModel<ActivityViewModel>()
                            val state by vm.state.collectAsState()
                            ActivityScreen(
                                state = state,
                                onIntent = vm::onIntent,
                                onBack = { goBack() },
                                onAddTransaction = { openAddSheet() },
                            )
                        }

                        entry<ShareKey> {
                            val vm = koinViewModel<ShareViewModel>()
                            val state by vm.state.collectAsState()
                            ShareScreen(state = state, onIntent = vm::onIntent, onBack = { goBack() })
                        }

                        entry<SettingsKey> {
                            SettingsScreen(
                                state = settings,
                                onIntent = settingsVm::onIntent,
                                onBack = { goBack() },
                                onOpenShare = { goTo(ShareKey) },
                                onOpenGallery = { goTo(GalleryKey) },
                            )
                        }

                        entry<GalleryKey> {
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

            // Alis/Satis on-secimi: sheet hangi butondan acildiysa o sekmeyle gelir.
            LaunchedEffect(addSheetSide) {
                addVm.onIntent(AddTransactionIntent.SelectSide(addSheetSide))
            }

            // Kaydetme basarili olunca sheet kendiliginden kapanir; bayrak tuketilir
            // ki bir sonraki acilista sheet aninda kapanmasin.
            LaunchedEffect(addState.saved) {
                if (addState.saved) {
                    addSheetVisible = false
                    addVm.onIntent(AddTransactionIntent.ConsumeSaved)
                }
            }

            AddTransactionSheet(
                state = addState,
                onIntent = addVm::onIntent,
                onDismiss = { addSheetVisible = false },
            )
        }
    }
}

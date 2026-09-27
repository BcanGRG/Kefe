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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.kefe.app.data.sync.CloudMode
import com.kefe.app.data.sync.SyncCoordinator
import com.kefe.app.di.appModule
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.navigation.ActivityKey
import com.kefe.app.navigation.AssetDetailKey
import com.kefe.app.navigation.AssetsKey
import com.kefe.app.navigation.GalleryKey
import com.kefe.app.navigation.GoalDetailKey
import com.kefe.app.navigation.GoalsKey
import com.kefe.app.navigation.KefeKey
import com.kefe.app.navigation.LockKey
import com.kefe.app.navigation.MarketKey
import com.kefe.app.navigation.OnboardingKey
import com.kefe.app.navigation.OpenSettingsStep
import com.kefe.app.navigation.PlanKey
import com.kefe.app.navigation.ProfileSetupKey
import com.kefe.app.navigation.SettingsKey
import com.kefe.app.navigation.ProfilesKey
import com.kefe.app.navigation.SignInKey
import com.kefe.app.navigation.SummaryKey
import com.kefe.app.navigation.WelcomeKey
import com.kefe.app.navigation.desktopDestinations
import com.kefe.app.navigation.isAccountFlow
import com.kefe.app.navigation.navSelection
import com.kefe.app.navigation.openSettingsStep
import com.kefe.app.navigation.restartSignIn
import com.kefe.app.navigation.rootFor
import com.kefe.app.navigation.topLevelDestinations
import com.kefe.app.security.BiometricGate
import com.kefe.app.ui.brand.KefeSplash
import com.kefe.app.ui.components.KefeBackHandler
import com.kefe.app.ui.components.KefeAutoDismissBanner
import com.kefe.app.ui.gallery.DesignSystemGallery
import com.kefe.app.ui.layout.KefeBottomNav
import com.kefe.app.ui.layout.KefeNavItem
import com.kefe.app.ui.layout.KefeNavigationRail
import com.kefe.app.ui.layout.KefeSideNavigation
import com.kefe.app.ui.layout.ProvideWindowSize
import com.kefe.app.ui.layout.WindowSize
import com.kefe.app.ui.mvi.CollectEffects
import com.kefe.app.ui.screens.account.ActivityScreen
import com.kefe.app.ui.screens.account.ActivityViewModel
import com.kefe.app.ui.screens.account.LoginIntent
import com.kefe.app.ui.screens.account.LockScreen
import com.kefe.app.ui.screens.account.LockViewModel
import com.kefe.app.ui.screens.account.LoginViewModel
import com.kefe.app.ui.screens.account.OnboardingPageCount
import com.kefe.app.ui.screens.account.OnboardingScreen
import com.kefe.app.ui.screens.account.ProfileSetupIntent
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
import com.kefe.app.ui.screens.account.isLaunchLocked
import com.kefe.app.ui.screens.account.launchSetupDone
import com.kefe.app.ui.screens.account.lockCanApply
import com.kefe.app.ui.screens.account.AfterSignIn
import com.kefe.app.ui.screens.account.SignInPurpose
import com.kefe.app.ui.screens.account.SignInScreen
import com.kefe.app.ui.screens.account.WelcomeChoice
import com.kefe.app.ui.screens.account.WelcomeScreen
import com.kefe.app.ui.screens.account.afterSignIn
import com.kefe.app.ui.screens.account.unlockedAtLaunchStart
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
import com.kefe.app.ui.screens.plan.PlanEffect
import com.kefe.app.ui.screens.plan.PlanIntent
import com.kefe.app.ui.screens.plan.PlanScreen
import com.kefe.app.ui.screens.plan.PlanSheets
import com.kefe.app.ui.screens.plan.PlanViewModel
import com.kefe.app.ui.screens.summary.SummaryIntent
import com.kefe.app.ui.screens.summary.SummaryScreenAdaptive
import com.kefe.app.ui.screens.summary.SummaryViewModel
import com.kefe.app.ui.screens.transaction.AddTransactionEffect
import com.kefe.app.ui.screens.transaction.AddTransactionIntent
import com.kefe.app.ui.screens.transaction.AddTransactionPrefill
import com.kefe.app.ui.screens.transaction.AddTransactionSheet
import com.kefe.app.ui.screens.transaction.AddTransactionViewModel
import com.kefe.app.ui.screens.transaction.isFirstStep
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinConfiguration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Karsiligi henuz olmayan satirlarin ortak yaniti.
 *
 * Dokununca hicbir sey olmamasi hata gibi gorunuyordu; tek cumle "burasi
 * calismiyor" ile "burasi henuz yok" arasindaki farki soyluyor.
 */
private const val NotReadyMessage = "Bu bölüm henüz hazır değil."

/**
 * Baglanti birakilinca (Ayarlar'dan ya da Ozet'teki "Vazgeç"ten) gosterilen
 * TEK cumle. Iki yol ayni isi yapar; biri sessiz kalirsa kullanici tek
 * dokunusla neyin degistigini (kayitlar duruyor mu?) bilemezdi.
 */
private const val DroppedLinkMessage = "Hesaptan çıkıldı. Kayıtlarınız bu cihazda duruyor."

/** Profil adimi cihazi bir hesaba YENI bagladiginda. */
private const val LinkedMessage = "Hesaba bağlandı — eşitleme açık."

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
    // Yigin once giris ekraniyla kuruluyor, "acilis akisi gecilmis" bilgisi
    // diskten gelince duzeltiliyordu. Arada kalan karelerde giris ekrani
    // goruntuye giriyordu - kullanicinin gordugu "splash'ten sonra login
    // parliyor" buydu. Duzeltmeyi hizlandirmak yetmez; dogru olan, karar gelene
    // kadar hicbir sey cizmemek ve yigini DOGRU kokle kurmaktir.
    //
    // null = diske henuz bakilmadi.
    val onboardingVm = koinViewModel<SummaryViewModel>()
    val onboarded by onboardingVm.onboarded.collectAsState()

    // Oturum da beklenir: kok "girisli mi"ye de bakar (bkz. rootFor). Kodu
    // dogrulayip profil secmeden kapatilan uygulama, oturum okunmadan karar
    // verilseydi hosgeldin ekraniyla acilir ve kullaniciya bastan e-posta
    // sorardi.
    val authRepository = koinInject<AuthRepository>()
    val authState by remember(authRepository) { authRepository.observeAuthState() }
        .collectAsState(AuthState.Unknown)

    // Senkron/push kordinatoru. Girisliyken yerel degisimleri Supabase'e iter;
    // kendi surec-omurlu scope'unda calisir, start() idempotent (bir kez baslar).
    val syncCoordinator = koinInject<SyncCoordinator>()
    LaunchedEffect(Unit) { syncCoordinator.start() }

    // Gercek zamanli soket YALNIZ on planda acik kalir - arka planda Phoenix
    // heartbeat'i kullanicinin bakmadigi bir ekran icin pil yakardi. Geri
    // donuste kordinator kacirilanlari tek pull ile toparlar.
    LifecycleResumeEffect(Unit) {
        syncCoordinator.setForeground(true)
        onPauseOrDispose { syncCoordinator.setForeground(false) }
    }

    // Marka animasyonu YALNIZ SOGUK ACILISTA oynar. Bayrak surec omurludur:
    // arka plandan geri donuste uygulama hemen gorunur, cunku her gecis icin iki
    // saniye beklemek gunde onlarca kez acilan bir uygulamada bedel olur.
    var splashDone by remember { mutableStateOf(SplashAlreadyPlayed) }

    // Tercihler de beklenir: kilit acik mi bilmeden ekran cizilirse ya kilitli
    // olmayan bir uygulama bir kare kilitli goruntu verir ya da tersi.
    if (onboarded == null || !settings.prefsLoaded || authState == AuthState.Unknown || !splashDone) {
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
    //
    // Kimlik sorulabiliyor mu, acilista BIR KEZ bakilir: karar bu acilisa ait.
    // Masaustunde ve kimligi tanimsiz telefonda kilit hic devreye girmez; once
    // kilit ekrani bir an cizilip hemen aciliyordu.
    val biometric = koinInject<BiometricGate>()
    val gateAvailable = remember { lockCanApply(biometric.availability()) }
    // Kurulum BITMIS sayilmasi icin profil de secilmis olmali (bkz.
    // launchSetupDone): yeni kurulum, henuz tek kaydi yokken kilitleniyordu.
    val setupDone = launchSetupDone(onboarded, settings.activeMemberId)
    // KILIT YALNIZ ACILISTA: kilitsiz baslayan acilis "acilmis" sayilir, surecin
    // ortasinda acilan kilit bir sonraki acilista gecerli (bkz.
    // unlockedAtLaunchStart).
    //
    // Kilit VM'i kabukta alinir (Activity'ye bagli, gezinme girdisine degil):
    // Activity yeniden yaratilinca (katlama, bolunmus ekran, dil) `remember`
    // sifirlanir ama VM kalir. Kilit bu Activity'de acildiysa yeniden
    // yaratilma bir ACILIS degildir - kok kilit olmaz.
    val lockVm = koinViewModel<LockViewModel>()
    var unlockedThisLaunch by remember {
        mutableStateOf(
            unlockedAtLaunchStart(
                lockEnabled = settings.biometricLock,
                setupDone = setupDone,
                gateAvailable = gateAvailable,
                unlockedBefore = lockVm.state.value.unlocked,
            ),
        )
    }
    val locked = isLaunchLocked(settings.biometricLock, setupDone, gateAvailable, unlockedThisLaunch)

    // Acilistaki kok: kilit, Ozet, "bu telefon kimin" ya da hosgeldin - kurallar
    // rootFor'da. Kilit ve giris ARTIK AYRI ekranlar: kilit yalniz kok olur,
    // giris hep itilir.
    val backStack = remember {
        val root: NavKey = rootFor(
            locked = locked,
            onboarded = onboarded == true,
            activeMemberId = settings.activeMemberId,
            signedIn = authState is AuthState.SignedIn,
        )
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

    // Sheet HANGI VARLIK icin aciliyor; null ise varlik secimiyle baslar.
    var addSheetPositionId by remember { mutableStateOf<String?>(null) }

    // Plandan "Al": varlik anahtari ve kalan miktar (varlik elde olmayabilir).
    var addSheetPrefill by remember { mutableStateOf<AddTransactionPrefill?>(null) }

    fun openAddSheet(
        side: TradeSide = TradeSide.Buy,
        positionId: String? = null,
        prefill: AddTransactionPrefill? = null,
    ) {
        addSheetSide = side
        addSheetPositionId = positionId
        addSheetPrefill = prefill
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

    /**
     * Ayarlar: masaustunde yan menunun ust duzey satiri (kok olur); telefonda ve
     * tablette ITILEN ikincil ekran - alt barda yerini Plan aldi, geri oku acildigi
     * yere doner. Yiginda zaten varsa ustundekiler kapanir, ikinci kopya itilmez;
     * yarim bir giris ya da profil adiminin ustune de itilmez (tablette ray onlarin
     * yaninda da gorunur). Kural SAF ve testli: openSettingsStep (ShellNavigation.kt).
     */
    fun openSettings() {
        fun popAbove(index: Int) {
            while (backStack.lastIndex > index) backStack.removeAt(backStack.lastIndex)
        }
        when (val step = openSettingsStep(backStack, windowSize.isExpanded)) {
            OpenSettingsStep.AsRoot -> selectTab(SettingsKey)
            is OpenSettingsStep.PopTo -> popAbove(step.index)
            is OpenSettingsStep.Push -> {
                popAbove(step.popTo)
                goTo(SettingsKey)
            }
        }
    }

    // Kabuk (nav, ust cubuk, piyasa paneli) portfoy ozetinden beslenir.
    val summaryVm = koinViewModel<SummaryViewModel>()
    val summary by summaryVm.state.collectAsState()

    /**
     * Giris/onboarding bitti: yigin sifirlanir, geri tusu girise donmez.
     *
     * "Bu telefon kimin" adimi henuz gecilmediyse ONA gideriz: kayitlar bir
     * profile yazilacak, cihazin hangi profil oldugu bilinmeden ana ekrana
     * girmek erken olur. [forceProfileSetup]: profil secili olsa bile (bkz.
     * profileSetupAfterSignIn) - hesaba yeni baglanan cihaz hesabin adlariyla
     * yeniden sorulur.
     */
    fun enterApp(forceProfileSetup: Boolean = false) {
        // Iceri giren bu acilista bir daha kilitlenmez. Kilitsiz baslayan acilis
        // zaten "acilmis" sayiliyor; bu satir kilit ekranindan gelen yolu da
        // ayni yere baglar - iceri yeni giren birine hemen kimlik sormak anlamsiz.
        unlockedThisLaunch = true
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack[0] = if (forceProfileSetup || settings.activeMemberId == null) ProfileSetupKey else SummaryKey
        summaryVm.markOnboarded()
    }

    // Giris ve profil adimi VM'leri surec boyunca yasar (gezinme girdisine
    // bagli degil); kabuk onlari ekran acilmadan once sifirlar.
    val loginVm = koinViewModel<LoginViewModel>()
    val profileSetupVm = koinViewModel<ProfileSetupViewModel>()
    val scope = rememberCoroutineScope()
    var switchingEmail by remember { mutableStateOf(false) }

    /**
     * Giris ekranini [purpose] icin iter. Sifirlama ITMEDEN ONCE yapilir,
     * bestelemede degil: ekran ilk karesinden temiz durumla cizilir ve yarida
     * birakilmis bir denemenin bayragi (signedIn, codeSent) yeni ekrana tasinmaz.
     * Yeniden giriste alan, cihazin bagli oldugu e-postayla dolu gelir.
     */
    fun openSignIn(purpose: SignInPurpose) {
        val prefill = if (purpose == SignInPurpose.Relogin) {
            (settings.cloudMode as? CloudMode.SessionLost)?.email?.ifBlank { null }
        } else {
            null
        }
        loginVm.onIntent(LoginIntent.Begin(prefill))
        goTo(SignInKey(purpose))
    }

    /**
     * Profil adiminda "Farklı e-postayla gir": oturum kapanir, giris ekrani
     * yeniden acilir; altinda donulecek bir yer kalir (bkz. restartSignIn).
     *
     * YALNIZ OTURUM kapanir, baglanti anahtarlarina dokunulmaz. Profil adimi
     * baglantiyi ancak "Devam"la yazar; burada bir baglanti varsa BASKA bir
     * hesaba aittir (orn. "Yeniden giriş yap"ta farkli e-posta) ve korunmali -
     * mod "Oturum kapandı"ya doner, dogru hesapla yeniden girilebilir.
     * dropLink onu da silerdi.
     */
    fun useAnotherEmail() {
        // Cift dokunus ikinci kez calismasin: ilki yigini degistirdikten sonra
        // ikincisi giris ekranini "uygulamanin ustunde" sanip kapatirdi.
        if (switchingEmail) return
        switchingEmail = true
        scope.launch {
            try {
                authRepository.signOut()
                profileSetupVm.onIntent(ProfileSetupIntent.Reset)
                val restart = restartSignIn(
                    pushedOverApp = backStack.size > 1,
                    activeMemberId = settings.activeMemberId,
                )
                val root = restart.root
                if (root == null) {
                    goBack()
                } else {
                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    backStack[0] = root
                }
                openSignIn(restart.purpose)
            } finally {
                switchingEmail = false
            }
        }
    }

    /**
     * Profil adiminda (cakismada) "Vazgeç": YALNIZ bu cihazin oturumu kapanir,
     * akisin basladigi yere donulur. Hicbir sey yazilmamistir - hesaba bakildi,
     * o kadar.
     *
     * Baglanti anahtarlarina dokunulmaz (bkz. useAnotherEmail): burada bir
     * baglanti varsa baska bir hesaba aittir ve korunmali.
     *
     * Profil adimi uygulamanin ustune itildiyse o kapanir. Kokse (ilk kurulum
     * yarida) kok acilistaki kurala gore yeniden secilir; profil adimi kok
     * kalirsa girdisi ayni oldugu icin yeniden yuklenmez - elle yuklenir.
     */
    fun cancelLink() {
        if (switchingEmail) return
        switchingEmail = true
        scope.launch {
            try {
                authRepository.signOut()
                profileSetupVm.onIntent(ProfileSetupIntent.Reset)
                if (backStack.size > 1) {
                    goBack()
                } else {
                    val root = rootFor(
                        locked = false,
                        onboarded = onboarded == true,
                        activeMemberId = settings.activeMemberId,
                        signedIn = false,
                    )
                    backStack[0] = root
                    if (root == ProfileSetupKey) profileSetupVm.onIntent(ProfileSetupIntent.Load)
                }
            } finally {
                switchingEmail = false
            }
        }
    }

    // Plan VM'i de KABUKTA: sheet'leri ekranin degil kabugun ustunde, TEK yerde cizilir
    // (GoalsScreen'deki cift sheet tekrarlanmasin) ve yan menu rozeti de ondan beslenir.
    // Ayarlar etkilerinden ONCE tanimli: "Tüm verileri sil" secili ayi da sifirlar.
    val planVm = koinViewModel<PlanViewModel>()
    val planState by planVm.state.collectAsState()
    // Ozet ve hedef detayindaki plan girisleri BU AYI gosterir: sekmede baska bir
    // ay secili kaldiysa "Eylül planı %34" dokunusu Ağustos'u acmasin.
    fun openPlanThisMonth() {
        planVm.onIntent(PlanIntent.ThisMonth)
        selectTab(PlanKey)
    }

    CollectEffects(planVm.effects) { effect ->
        when (effect) {
            is PlanEffect.OpenAddTransaction -> openAddSheet(TradeSide.Buy, prefill = effect.prefill)
            is PlanEffect.Message -> saveError = effect.text
        }
    }

    // Ayarlar etkileri kabukta karsilanir: silme sonrasi yigini sifirlamak ve
    // seride mesaj gostermek ekranin isi degil.
    CollectEffects(settingsVm.effects) { effect ->
        when (effect) {
            // Silinen cihaz yeni kurulum gibidir ve koku SOGUK ACILISLA AYNI
            // kural secer (rootFor): girissizse hosgeldin, iki yol yeniden
            // sorulur. NEYDI: kok giris formuydu; hesapsiz kullanan biri
            // sildikten sonra bir e-posta formuyla karsilaniyordu.
            //
            // Oturum silmeden sag kaliyorsa (silme yalniz ayarlari ve baglantiyi
            // temizler) kok profil adimidir: hesap indirilir, "bu telefon kimin"
            // sorulur; "Farklı e-postayla gir" oturumu da kapatir. Kok sabit
            // hosgeldin oldugunda "Bu cihazda kullan" ("Hesap gerekmez") secen
            // biri profil adiminda hesabi yeniden indirip ona baglaniyordu; ayni
            // durumda soguk acilis ise profil adimina aciliyordu.
            //
            // Profil adiminin eski yazilari da silinir - sonraki kurulum temiz
            // baslar.
            //
            // Oturum ANLIK okunur: "Bu cihazı sıfırla" silmeden once hesaptan
            // cikar ve bestelenmis `authState` henuz eski (girisli) olabilir -
            // o zaman kok profil adimi olur ve hesap yeniden indirilirdi.
            SettingsEffect.AllDataDeleted -> {
                saveError = "Tüm veriler silindi."
                profileSetupVm.onIntent(ProfileSetupIntent.Reset)
                // Plan VM'i surec boyunca yasar ve secili ayi tutar. Gecen ay veri
                // olmadan da sinir icinde kaldigi icin kirpma onu bosaltmaz; yeni
                // kurulum Plan'i gecmis bir ayda acmasin.
                planVm.onIntent(PlanIntent.ThisMonth)
                scope.launch {
                    val auth = authRepository.observeAuthState().first { it !is AuthState.Unknown }
                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    backStack[0] = rootFor(
                        locked = false,
                        onboarded = false,
                        activeMemberId = null,
                        signedIn = auth is AuthState.SignedIn,
                    )
                }
            }
            is SettingsEffect.DeleteFailed -> saveError = effect.message
            SettingsEffect.NotReady -> saveError = NotReadyMessage

            // Cikis artik giris ekranina ATMAZ: giris istege bagli, uygulama
            // hesapsiz tam calisir. Kullanici Ayarlar'da kalir; hesap bolumu
            // "Yalnız bu cihazda"ya doner. Mesaj kayitlarin akibetini soyler.
            SettingsEffect.SignedOut -> {
                saveError = DroppedLinkMessage
            }

            // Paylasim sayfasi acildi; dosyanin nereye gittigine kullanici karar
            // verir, biz "kaydedildi" diyemeyiz.
            SettingsEffect.BackupReady -> saveError = "Yedek hazır — kaydetmek için bir yer seçin."
            SettingsEffect.Restored -> saveError = "Yedek geri yüklendi."
            is SettingsEffect.BackupFailed -> saveError = effect.message
            // Orn. acilis kilidi acildi / acilamadi: anahtarin neden oldugu gibi
            // kaldigini sessiz birakmak "bozuk" gibi gorunuyordu.
            is SettingsEffect.Notice -> saveError = effect.message
        }
    }

    // Giris zorunlu degildir: Kefe hesapsiz da tam calisir, hesap yalniz iki
    // telefonda esitleme icin gerekir. Kapiyi oturum degil "acilis akisi
    // gecildi mi" bayragi tutar ve kok ACILISTA bir kez secilir (rootFor).
    // Eskiden burada "onboarded olunca giris kokunden iceri al" diyen bir etki
    // vardi; giris artik kok olmadigi (hep itildigi) icin karsiligi kalmadi -
    // giristen sonraki gezinmeyi giris ekraninin kendi etkisi yapar.

    // Hedef duzenleme sheet'i KABUKTA yasar: hem Hedefler listesinden hem de
    // Hedef Detayi'ndan acilabilmesi gerekiyor. Ekranin icine gomulu oldugunda
    // detaydan acmak yapisal olarak mumkun degildi.
    val goalsVm = koinViewModel<GoalsViewModel>()
    val goalsState by goalsVm.state.collectAsState()

    val current = backStack.firstOrNull()
    // Hesap akisinda (hosgeldin, kilit, tanitim, profil adimi) navigasyon
    // kromu cizilmez.
    val inShell = (current as? KefeKey)?.isAccountFlow() == false
    val navItems = if (windowSize.isExpanded) desktopDestinations else topLevelDestinations
    // Ayarlar yiginin HERHANGI bir yerindeyse (ustunde Profiller, giris, profil adimi ya da
    // katalog olsa da) secim Ayarlar'dir: masaustunde "Ayarlar" satiri, telefonda ve
    // tablette hicbir sekme (-1; raydaki disli secili). NEYDI: coerceAtLeast(0) koke
    // bakiyordu; kok listede yokken Ozet'i secili gosteriyordu.
    val settingsOpen = SettingsKey in backStack
    val navIndex = navSelection(backStack, navItems)
    val members = summary.members.mapIndexed { index, m -> m.initials to index }
    // Ray ve yan navigasyonun durumu HESAP modudur (summary.cloudMode), fiyat
    // tazeligi DEGIL. NEYDI: burada fiyattan turetiliyordu - fiyat ucu
    // tokezleyince "Çevrimdışı", istek yoldayken "Bekliyor" yaziyordu; hesapsiz
    // bir masaustu bile "Eşit" gorunuyordu. Fiyat artik yan navigasyonun ikinci
    // satirinda, kendi adiyla.

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
                                // Bu ayin tamamlanmamis kalemleri - Plan'da gecmis bir aya
                                // bakmak rozeti degistirmez.
                                PlanKey -> planState.content.currentMonthOpenCount.takeIf { it > 0 }
                                else -> null
                            },
                        )
                    },
                    selectedIndex = navIndex,
                    onSelect = { selectTab(navItems[it].key) },
                    onAdd = { openAddSheet() },
                    members = members,
                    memberNames = summary.members.joinToString(", ") { it.name },
                    cloudMode = summary.cloudMode,
                    modeLine = summary.navModeLine,
                    priceLine = summary.navPriceLine,
                    modifier = Modifier.fillMaxHeight(),
                    // Hesap satirlari Ayarlar'in ilk bolumu: her platformda tek hedef.
                    onStatusClick = { openSettings() },
                )
            } else if (inShell && windowSize.isMedium) {
                KefeNavigationRail(
                    items = navItems.map { KefeNavItem(it.label, it.icon) },
                    selectedIndex = navIndex,
                    onSelect = { selectTab(navItems[it].key) },
                    onAdd = { openAddSheet() },
                    members = members,
                    cloudMode = summary.cloudMode,
                    modifier = Modifier.fillMaxHeight(),
                    onStatusClick = { openSettings() },
                    // Tablette Ayarlar sekme degil, alt kumedeki disli.
                    onOpenSettings = { openSettings() },
                    settingsSelected = settingsOpen,
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
                        entry<WelcomeKey> {
                            ScreenSurface {
                                WelcomeScreen(
                                    cloudConfigured = syncCoordinator.cloudConfigured,
                                    onChoose = { choice ->
                                        when (choice) {
                                            // Hesapsiz: tanitim, sonra profil
                                            // olusturma (enterApp tanitimin sonunda).
                                            WelcomeChoice.OnDevice -> {
                                                onboardingPage = 0
                                                goTo(OnboardingKey)
                                            }
                                            // Hesapla: tanitim atlanir - hesabi olan
                                            // icin tanitim bir engel; profil adimi
                                            // hesabi indirip "bu telefon kimin"i sorar.
                                            WelcomeChoice.WithAccount -> openSignIn(SignInPurpose.FirstRun)
                                        }
                                    },
                                )
                            }
                        }

                        entry<LockKey> {
                            val state by lockVm.state.collectAsState()
                            // Kilit acilinca uygulama gorunur. unlockedThisLaunch
                            // (enterApp yazar) kabukta yasar: ekran dolasirken
                            // tekrar sorulmaz. Kilit yalniz kok olur ve yalniz
                            // acilista gelir. VM'in `unlocked`i KALICIDIR: Activity
                            // yeniden yaratilinca kok onu gorup kilide hic donmez
                            // (bkz. unlockedAtLaunchStart), acilmis kilit de
                            // ikinci bir istem acmaz (bkz. canStartUnlock).
                            LaunchedEffect(state.unlocked) {
                                if (state.unlocked) enterApp()
                            }
                            ScreenSurface {
                                LockScreen(state = state, onIntent = lockVm::onIntent)
                            }
                        }

                        entry<SignInKey> { key ->
                            val state by loginVm.state.collectAsState()

                            // Kod dogrulanir dogrulanmaz bir sonraki adima gecilir;
                            // ekranda ayrica "devam et" dedirtmek bos bir adim olurdu.
                            //
                            // NEREYE (bkz. afterSignIn): cihaz bu hesaba zaten
                            // bagliysa (ayni hesaba yeniden giris) ve profili
                            // seciliyse giris ekrani yalniz kapanir, geldigi sekmeye
                            // donulur; aksi her durumda "bu telefon kimin". NEYDI:
                            // Ayarlar'dan giren kurulu cihaz dogrudan Ozet'e
                            // gidiyordu; kordinator hesabin adlarini devraliyor ama
                            // cihazin eski secimi (member_owner) kaliyordu - telefon
                            // sessizce diger kisi oluyordu. Profil adimi hesabi
                            // indirir, secimi sorar ve baglantiyi ancak o zaman yazar.
                            LaunchedEffect(state.signedIn) {
                                if (state.signedIn) {
                                    // Oturum yazildi ama mod akisi onu bir an sonra
                                    // gorur; eski modla ("Bu cihazda") karar
                                    // verilmesin. Gelmezse guvenli yol: profil adimi.
                                    val mode = withTimeoutOrNull(SignInModeWaitMillis) {
                                        syncCoordinator.mode().first {
                                            it is CloudMode.Cloud || it is CloudMode.LinkPending
                                        }
                                    }
                                    // VM surec boyunca yasiyor; bayrak kalirsa bir
                                    // sonraki giris e-postayi sormadan gecerdi.
                                    // Beklemeden SONRA: bayrak once duserse bu etki
                                    // yeniden baslar ve bekleme iptal olurdu.
                                    loginVm.onIntent(LoginIntent.SignInHandled)
                                    val caller = backStack.firstOrNull() as? KefeKey
                                    val callerInShell = backStack.size > 1 && caller?.isAccountFlow() == false
                                    when (afterSignIn(mode, settings.activeMemberId, callerInShell)) {
                                        // Uygulamanin icinden (Ayarlar, Ozet) gelindiyse profil
                                        // adimi giris ekraninin YERINE itilir: bitince ya da
                                        // "Vazgeç"te akisin basladigi yere donulur. NEYDI: yigin
                                        // sifirlanip profil adimi kok oluyordu; Ayarlar'dan
                                        // baglanan kullanici is bitince Ozet'e atiliyordu.
                                        AfterSignIn.ProfileSetup ->
                                            if (callerInShell) {
                                                backStack[backStack.lastIndex] = ProfileSetupKey
                                            } else {
                                                enterApp(forceProfileSetup = true)
                                            }
                                        AfterSignIn.ReturnToCaller -> goBack()
                                        AfterSignIn.EnterApp -> enterApp()
                                    }
                                }
                            }
                            ScreenSurface {
                                SignInScreen(
                                    state = state,
                                    purpose = key.purpose,
                                    onIntent = loginVm::onIntent,
                                    onLeave = { goBack() },
                                )
                            }
                        }

                        entry<OnboardingKey> {
                            // Tanitim sayfalari tek gezinme girdisidir; geri tusu
                            // bunu bilmedigi icin ucuncu sayfadan basilinca uc
                            // sayfayi birden atlayip bir onceki ekrana donuyordu.
                            // Ilk sayfada devre disi kalir - orada geri gitmek
                            // gercekten hosgeldin ekranina donmek demektir.
                            KefeBackHandler(enabled = onboardingPage > 0) { onboardingPage-- }
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
                            val vm = profileSetupVm
                            val profileState by vm.state.collectAsState()
                            // Her gorunuste: "Hesaba bağla" ile giristen
                            // donuldugunde oturum artik acik, hesap indirilmeli.
                            // Olusturma modunda yazilanlar yeniden yuklemede
                            // korunur (bkz. ProfileSetupViewModel.showMembers).
                            LaunchedEffect(Unit) { vm.onIntent(ProfileSetupIntent.Load) }
                            ScreenSurface {
                                ProfileSetupScreen(
                                    state = profileState,
                                    onIntent = vm::onIntent,
                                    // Giris itilir; kod dogrulaninca afterSignIn
                                    // bu ekrana doner ve hesap indirilir.
                                    onLink = { openSignIn(SignInPurpose.Link) },
                                    onUseAnotherEmail = { useAnotherEmail() },
                                    onCancelLink = { cancelLink() },
                                    // "Önce yedek al": Ayarlar'daki yedekle ayni is;
                                    // sonucu kabugun seridi soyler.
                                    onBackup = { settingsVm.onIntent(SettingsIntent.Backup) },
                                    // Kaydedilince akisin basladigi yere: uygulamanin
                                    // ustune itildiyse (Ayarlar, Ozet'teki "Tamamla")
                                    // oraya doner, kokse Ozet'e gecer. Yeni baglanti
                                    // tek cumleyle soylenir.
                                    onDone = {
                                        val linked = profileState.linkedNow
                                        vm.onIntent(ProfileSetupIntent.Reset)
                                        if (backStack.size > 1) {
                                            goBack()
                                        } else {
                                            backStack[0] = SummaryKey
                                        }
                                        if (linked) saveError = LinkedMessage
                                    },
                                )
                            }
                        }

                        entry<SummaryKey> {
                            // Ozet ContentWidth'ten gecmez (kendi masaustu
                            // duzenini cizer) ama opak zemine yine ihtiyaci var.
                            ScreenSurface {
                                SummaryScreenAdaptive(
                                    // Ayin plani Plan VM'inden: Ozet ayni defteri ikinci kez turetmez.
                                    state = summary.copy(monthPlan = planState.content.currentMonth),
                                    onIntent = { intent ->
                                        summaryVm.onIntent(intent)
                                        // "Vazgeç" Ayarlar'daki cikisla ayni sonucu soyler.
                                        if (intent == SummaryIntent.DropLink) saveError = DroppedLinkMessage
                                    },
                                    onOpenGoal = { goTo(GoalDetailKey(it)) },
                                    onOpenGoals = { selectTab(GoalsKey) },
                                    onOpenActivity = { goTo(ActivityKey) },
                                    onOpenMarket = { goTo(MarketKey) },
                                    onAddAsset = { openAddSheet() },
                                    marketRows = summary.marketRows,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    onOpenMarketRow = { goTo(MarketKey) },
                                    // Cip: Ayarlar'in ilk bolumu hesap.
                                    onOpenAccount = { openSettings() },
                                    // Disli: telefonda Ayarlar'in kapisi (alt barda
                                    // yerini Plan aldi); tablet ve masaustu cizmez.
                                    onOpenSettings = { openSettings() },
                                    // "Tamamla": hesabi indirip "bu telefon kimin"i
                                    // soran adim; baglantiyi o yazar.
                                    onCompleteLink = { goTo(ProfileSetupKey) },
                                    onRelogin = { openSignIn(SignInPurpose.Relogin) },
                                    onOpenPlan = { openPlanThisMonth() },
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
                                    // Sheet BU VARLIKLA acilir: kullanici hangi
                                    // varlikta oldugunu zaten soyledi, bir kez
                                    // daha secmesi gereksiz bir adimdi.
                                    onAddBuy = { openAddSheet(TradeSide.Buy, key.positionId) },
                                    onAddSell = { openAddSheet(TradeSide.Sell, key.positionId) },
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
                                    monthPlan = planState.content.currentMonth?.goals
                                        ?.firstOrNull { it.goalId == key.goalId },
                                    monthPlanTitle = planState.content.currentMonth?.title.orEmpty(),
                                    onPlanIntent = planVm::onIntent,
                                    onOpenPlan = { openPlanThisMonth() },
                                )
                            }
                        }

                        entry<PlanKey> {
                            ContentWidth {
                                PlanScreen(
                                    state = planState,
                                    onIntent = planVm::onIntent,
                                    onOpenGoal = { goTo(GoalDetailKey(it)) },
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
                                    // Itilmisse (telefon/tablet) geri oku; kokse (masaustu
                                    // ya da pencere daraldiktan sonra) ok yok.
                                    onBack = if (backStack.size > 1) ({ goBack() }) else null,
                                    onOpenShare = { goTo(ProfilesKey) },
                                    onLink = { openSignIn(SignInPurpose.Link) },
                                    onRelogin = { openSignIn(SignInPurpose.Relogin) },
                                    onCompleteLink = { goTo(ProfileSetupKey) },
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
                        items = navItems.map { KefeNavItem(it.label, it.icon) },
                        selected = navIndex,
                        onSelect = { selectTab(navItems[it].key) },
                        onAdd = { openAddSheet() },
                    )
                }
            }
        }

        // Hedef duzenleme sheet'i her ekranin ustunde cizilir.
        GoalEditSheet(state = goalsState.editor, onIntent = goalsVm::onIntent)

        // Plan sheet'leri: tek yer, her ekranin ustunde. Masaustunde icerik genisligiyle
        // sinirli; scrim sheet'in kendi tam ekran kutusunda, panel daralip ortalanir.
        PlanSheets(
            sheet = planState.sheet,
            onIntent = planVm::onIntent,
            modifier = Modifier.widthIn(max = Sizes.contentMaxWidth),
        )

        // Ekleme sheet'inin ViewModel'i de KABUKTA yasar - GoalsViewModel ile ayni
        // gerekce, bir tane daha eklenerek: sheet'in geri isleyicisi KOSULSUZ
        // bestelenmek zorunda (bkz. KefeBackHandler) ve isleyici sheet'in
        // hangi adimda oldugunu bilmeli. Ikisi de `if` icinde kalamaz.
        val addVm = koinViewModel<AddTransactionViewModel>()
        val addState by addVm.state.collectAsState()

        // Geri tusu ADIMLARI da tanir. Iki adim ayri bir gezinme girdisi degil,
        // tek sayfanin durumu; sistem bunu bilmedigi icin geri tusu sayfayi
        // acik birakip ARKADAKI ekrani geri atiyordu. Ust bardaki geri okuyla
        // ayni davranis: 2. adimda bir adim geri, 1. adimda sayfa kapanir.
        KefeBackHandler(enabled = addSheetVisible) {
            if (addState.isFirstStep) {
                addSheetVisible = false
            } else {
                addVm.onIntent(AddTransactionIntent.Back)
            }
        }

        if (addSheetVisible) {
            // Duzenlemede alan degerleri kayittan gelir - alis/satis dahil.
            // Yeni kayitta form sifirlanir: ViewModel sheet kapaninca olmedigi
            // icin bir onceki acilisin alanlari duruyordu.
            LaunchedEffect(addSheetSide, addSheetEditId, addSheetPositionId, addSheetPrefill) {
                val editId = addSheetEditId
                if (editId != null) {
                    addVm.onIntent(AddTransactionIntent.EditTransaction(editId))
                } else {
                    addVm.onIntent(
                        AddTransactionIntent.StartNew(addSheetSide, addSheetPositionId, addSheetPrefill),
                    )
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

/**
 * Giristen sonra modun oturumu gormesi icin en cok bu kadar beklenir. Yerel
 * veritabani okumasi; normalde milisaniyeler. Asilirsa profil adimina gidilir.
 */
private const val SignInModeWaitMillis = 3_000L

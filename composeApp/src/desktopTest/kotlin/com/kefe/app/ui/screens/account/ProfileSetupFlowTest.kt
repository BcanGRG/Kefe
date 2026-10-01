package com.kefe.app.ui.screens.account

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.data.sync.AccountLinker
import com.kefe.app.data.sync.MemberDto
import com.kefe.app.data.sync.PullEngine
import com.kefe.app.data.sync.SyncLocalSink
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.testing.TestMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Profiller / Bu telefon kimin?" akisi.
 *
 * NEYDI. Hesabi olan kullanici yeni telefonda "İki profil oluşturun" goruyordu:
 * ekran pull'u beklemiyor, bos alanlara yazilan adlar da hesaptaki gercek
 * adlarin ustune (daha yeni damgayla) yaziliyordu. Burada sabitlenen:
 *   - girisliyse once hesap indirilir, profil varsa YALNIZ secim yapilir;
 *   - secim modunda kaydetmek adlara DOKUNMAZ (damga degismez);
 *   - hesaba ulasilamazsa ekran bunu soyler, sessizce "olustur"a dusmez.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSetupFlowTest {

    /** Kurulan VM'ler ([Env.vm]) testten sonra durdurulur (bkz. [TestMain]). */
    private val main = TestMain()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.release()

    private class FlowAuth(signedIn: Boolean) : AuthRepository {
        val state = MutableStateFlow<AuthState>(
            if (signedIn) SignedInU1 else AuthState.SignedOut,
        )

        /** Sunucu yenileme jetonunu reddeder: oturum silinir, jeton yok. */
        var rejectRefresh = false

        /** Ag yok: jeton alinamaz ama oturum cihazda kalir. */
        var tokenUnavailable = false

        override fun observeAuthState(): Flow<AuthState> = state
        override val isCloudConfigured: Boolean = true
        override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
        override suspend fun validAccessToken(): String? {
            if (rejectRefresh) {
                state.value = AuthState.SignedOut
                return null
            }
            if (tokenUnavailable) return null
            return (state.value as? AuthState.SignedIn)?.session?.accessToken
        }
        override suspend fun signOut() = Unit
    }

    private class FlowApi : PostgrestApi {
        var calls = 0
        var failWith: String? = null
        val tables = mutableMapOf<String, String>()
        override suspend fun upsert(table: String, rowsJson: String, accessToken: String) = Unit
        override suspend fun selectAll(table: String, accessToken: String): String {
            calls++
            failWith?.let { error(it) }
            return tables[table] ?: "[]"
        }
    }

    private inner class Env(signedIn: Boolean) {
        val database: KefeDatabase
        val auth = FlowAuth(signedIn)
        val api = FlowApi()
        val repo: SqlDelightPortfolioRepository
        val prefs: SqlDelightPreferencesRepository
        val vm: ProfileSetupViewModel

        init {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            KefeDatabase.Schema.create(driver)
            database = createKefeDatabase(driver)
            database.bootstrapIfNeeded()
            repo = SqlDelightPortfolioRepository(database, FixedKefeClock(millis = 9_000L), NoPrices())
            prefs = SqlDelightPreferencesRepository(database)
            val sink = SyncLocalSink(database)
            val pull = PullEngine(auth, api, sink)
            vm = main.track(
                ProfileSetupViewModel(
                    repo, prefs, auth, pull,
                    AccountLinker(pull, sink, prefs, FixedKefeClock(millis = 9_000L)),
                ),
            )
        }

        fun cloudMembers(owner: String = "Burak Can", partner: String = "Merve") {
            api.tables["members"] = Json.encodeToString(
                listOf(
                    MemberDto(LocalOwnerMemberId, "u1", owner, owner.take(1), 0, 5_000L),
                    MemberDto(LocalPartnerMemberId, "u1", partner, partner.take(1), 1, 5_000L),
                ),
            )
        }

        suspend fun awaitPhase(phase: ProfileSetupPhase): ProfileSetupUiState =
            realTime { vm.state.first { it.phase == phase } }

        suspend fun awaitDone() = realTime { vm.state.first { it.done } }

        suspend fun stamps(): List<Long> = repo.observeMembers().first().map { it.updatedAt }
        suspend fun names(): List<String> = repo.observeMembers().first().map { it.name }
    }

    @Test
    fun `hesapta profil varsa yalniz secim ve adlara dokunulmaz`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertFalse(s.editingNames)
        assertTrue(s.accountHasProfiles)
        assertEquals("Burak Can", s.ownerName)
        assertEquals("Merve", s.partnerName)
        assertEquals("Hesabınızda iki profil var: Burak Can ve Merve. Bu cihaz hangisinin?", s.readyBody())
        // Bilerek secili gelmez: iki telefon ayni profili secmesin.
        assertNull(s.thisDeviceIsOwner)
        assertFalse(s.canSave)

        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(false))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(LocalPartnerMemberId, e.prefs.get(PreferenceKeys.ActiveMemberId))
        // Damga hesabinki kaldi: push bu satirlari sunucuya yeniden itemez.
        assertEquals(listOf(5_000L, 5_000L), e.stamps())
    }

    @Test
    fun `girissiz ilk telefon profil olusturur ve sunucuya gitmez`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertTrue(s.editingNames)
        assertFalse(s.signedIn)
        assertEquals("", s.ownerName)
        assertEquals(0, e.api.calls)

        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Volkan"))
        e.vm.onIntent(ProfileSetupIntent.ChangePartnerName("Ayşe"))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(listOf("Volkan", "Ayşe"), e.names())
        assertEquals(LocalOwnerMemberId, e.prefs.get(PreferenceKeys.ActiveMemberId))
    }

    @Test
    fun `hesaba ulasilamazsa soylenir ve tekrar denenebilir`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.api.failWith = "Unable to resolve host"
        e.vm.onIntent(ProfileSetupIntent.Load)
        val failed = e.awaitPhase(ProfileSetupPhase.Failed)
        assertEquals("Unable to resolve host", failed.failureDetail)

        e.api.failWith = null
        e.vm.onIntent(ProfileSetupIntent.Retry)
        val ready = e.awaitPhase(ProfileSetupPhase.Ready)
        assertFalse(ready.editingNames)
        assertEquals("Burak Can", ready.ownerName)
    }

    /** Baglanmadan devam: yalniz secim, adlar yazilmaz - hesap gelince onunkiler gecer. */
    @Test
    fun `baglanmadan devam adlari yazmaz`() = runTest {
        val e = Env(signedIn = true)
        e.api.failWith = "offline"
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Failed)

        e.vm.onIntent(ProfileSetupIntent.ContinueOffline)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)
        assertFalse(s.editingNames)
        // Kurulumun "Ben"/"Eşim"i secenek olarak gosterilmez: esin telefonu
        // dogal olarak "Ben"i (sahibin profilini) seciyordu. Satirlar
        // "1. profil"/"2. profil" okunur.
        assertEquals("", s.ownerName)
        assertEquals("", s.partnerName)
        assertFalse(s.canEditNames, "adsiz secimde adlar duzenlenmez")
        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(listOf(0L, 0L), e.stamps(), "kurulum adlari damgasiz kalmali")
        assertEquals(LocalOwnerMemberId, e.prefs.get(PreferenceKeys.ActiveMemberId))
        // Hesap indirilmeden baglanti YOK: mod "Bağlantı yarım", hicbir sey gitmez.
        assertNull(e.prefs.get(PreferenceKeys.CloudLinkUserId))
    }

    /**
     * Baglanti YALNIZ hesap basariyla indirilip profil secilince, secimle ayni
     * islemde yazilir. NEYDI: "hic push'lamadi mi" tahmini; ilk pull patlayinca
     * push yine gidiyor, yerelde yazilmis adlar hesabin ustune itiliyordu.
     */
    @Test
    fun `hesap indirilip secilince baglanti yazilir`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.prefs.put(PreferenceKeys.LocalRestoredAt, "700")
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)
        assertNull(e.prefs.get(PreferenceKeys.CloudLinkUserId), "secimden once baglanti yok")

        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(false))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals("u1", e.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals("e@k.app", e.prefs.get(PreferenceKeys.CloudLinkEmail))
        assertEquals(LocalPartnerMemberId, e.prefs.get(PreferenceKeys.ActiveMemberId))
        assertNull(e.prefs.get(PreferenceKeys.LocalRestoredAt), "baglanti yedek izini temizler")
    }

    @Test
    fun `girissiz kurulum baglanti yazmaz`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)
        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Volkan"))
        e.vm.onIntent(ProfileSetupIntent.ChangePartnerName("Ayşe"))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertNull(e.prefs.get(PreferenceKeys.CloudLinkUserId))
    }

    /**
     * Ekranda beklerken oturum kapandiysa onizlenen hesaba baglanilmaz ve
     * secim de YAZILMAZ: secim hesabin adlarina gore yapilmisti, cihaza inmemis
     * adlarla yazilsa telefon yanlis kisi olurdu. Ekran girissiz haliyle
     * yeniden yuklenir.
     */
    @Test
    fun `oturum kapandiysa baglanti yazilmaz`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)

        e.auth.state.value = AuthState.SignedOut
        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        e.vm.onIntent(ProfileSetupIntent.Save)
        val s = realTime { e.vm.state.first { it.phase == ProfileSetupPhase.Ready && !it.signedIn } }

        assertFalse(s.done)
        assertTrue(s.editingNames, "girissiz, adsiz cihaz: olusturma")
        assertNull(e.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertNull(e.prefs.get(PreferenceKeys.ActiveMemberId))
        assertEquals(listOf(0L, 0L), e.stamps(), "hesabin adlari cihaza inmemeli")
    }

    /**
     * Ayarlar'dan giren kurulu cihaz: secim HESABIN adlariyla yeniden sorulur
     * (onceki secim korunmaz). Onizlemede cihaza hicbir sey yazilmaz; "Devam"
     * hesabin adlarini devralir.
     */
    @Test
    fun `bagli olmayan cihaz hesabin adlarini devralir ve yeniden sorar`() = runTest {
        val e = Env(signedIn = true)
        e.repo.renameMember(LocalOwnerMemberId, "Merve", "M")
        e.repo.renameMember(LocalPartnerMemberId, "Burak", "B")
        e.prefs.put(PreferenceKeys.ActiveMemberId, LocalOwnerMemberId)
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertEquals(listOf("Merve", "Burak"), e.names(), "onizleme bir sey yazmaz")
        assertEquals("Burak Can", s.ownerName)
        assertTrue(s.accountHasProfiles)
        assertNull(s.thisDeviceIsOwner, "secim yeniden sorulmali")

        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(false))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()
        assertEquals(listOf("Burak Can", "Merve"), e.names())
        assertEquals(LocalPartnerMemberId, e.prefs.get(PreferenceKeys.ActiveMemberId))
    }

    /**
     * Zaten BAGLI cihaz (ayni hesap) yeniden indirirken adlar devralinmaz: duz
     * LWW. NEYDI: devralma "hic push'lamadi" tahminine bagliydi; ilk push
     * patlarsa her acilista yeniden calisip yeni yapilan adlandirmayi geri
     * aliyordu.
     */
    @Test
    fun `bagli cihaz adlari devralmaz`() = runTest {
        val e = Env(signedIn = true)
        e.repo.renameMember(LocalOwnerMemberId, "Yerel", "Y")
        e.prefs.put(PreferenceKeys.CloudLinkUserId, "u1")
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)

        assertEquals("Yerel", e.names().first(), "yerel (9000) hesabinkinden (5000) yeni")
    }

    /**
     * Baska bir hesaba bagli cihaz o hesap icin "bagli" sayilmaz: devralir.
     * Yeni baglantida watermark SIFIRLANIR: eski hesabin watermark'i kalsaydi
     * yeni hesaba yalniz ondan sonra degisenler gider, daha once kurulan
     * pozisyonlar ve hedefler hic gitmezdi.
     */
    @Test
    fun `baska hesaba bagli cihaz devralir ve yeni baglantiyi yazar`() = runTest {
        val e = Env(signedIn = true)
        e.repo.renameMember(LocalOwnerMemberId, "Yerel", "Y")
        e.prefs.put(PreferenceKeys.CloudLinkUserId, "u2")
        e.prefs.put(PreferenceKeys.LastPushedAt, "4000")
        e.prefs.put(PreferenceKeys.LastSyncedAt, "4000")
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)
        assertEquals("Burak Can", s.ownerName)

        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()
        assertEquals("Burak Can", e.names().first(), "baglanti hesabin adlarini devralir")
        assertEquals("u1", e.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertNull(e.prefs.get(PreferenceKeys.LastPushedAt), "yeni hesaba her sey bastan gitmeli")
        assertNull(e.prefs.get(PreferenceKeys.LastSyncedAt), "onceki baglantinin ani gosterilmez")
    }

    /** Acik cikistan sonra (baglanti yok) ayni hesaba donus de tam gonderimdir. */
    @Test
    fun `cikistan sonra yeniden baglanti watermarki sifirlar`() = runTest {
        val e = Env(signedIn = true)
        e.prefs.put(PreferenceKeys.LastPushedAt, "4000")
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)

        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals("u1", e.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertNull(e.prefs.get(PreferenceKeys.LastPushedAt))
    }

    /** Zaten bu hesaba bagli cihazin secimi watermark'a dokunmaz. */
    @Test
    fun `ayni hesaba bagli cihaz watermarki korur`() = runTest {
        val e = Env(signedIn = true)
        e.prefs.put(PreferenceKeys.CloudLinkUserId, "u1")
        e.prefs.put(PreferenceKeys.LastPushedAt, "4000")
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)

        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals("u1", e.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals("4000", e.prefs.get(PreferenceKeys.LastPushedAt))
    }

    /** Adlari duzenlemede yalniz DEGISEN ad yazilir. */
    @Test
    fun `yalniz degisen ad yeniden yazilir`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)

        e.vm.onIntent(ProfileSetupIntent.EditNames)
        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Burak"))
        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(listOf("Burak", "Merve"), e.names())
        assertEquals(listOf(9_000L, 5_000L), e.stamps())
    }

    /**
     * Girissiz ama profilleri adlandirilmis cihaz (orn. "Tüm verileri sil"
     * sonrasi adlar kaldi): secim YEREL metinle sorulur, "hesabınızda" denmez.
     */
    @Test
    fun `girissiz adlandirilmis profiller yerel secimle sorulur`() = runTest {
        val e = Env(signedIn = false)
        e.repo.renameMember(LocalOwnerMemberId, "Volkan", "V")
        e.repo.renameMember(LocalPartnerMemberId, "Ayşe", "A")
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertFalse(s.editingNames)
        assertTrue(s.profilesNamed)
        assertFalse(s.accountHasProfiles, "girissizken hesap profili yok")
        assertEquals("Bu cihazda iki profil var. Hangisi sizsiniz?", s.readyBody())
        assertTrue(s.showLinkFooter)
        assertEquals(0, e.api.calls)
    }

    /** Bulut yapilandirmasi ekrana gecer: "Hesaba bağla" ancak o zaman cizilir. */
    @Test
    fun `girissiz olusturmada hesaba bagla gorunur`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)
        assertTrue(s.cloudConfigured)
        assertTrue(s.showLinkFooter)
        assertTrue("yalnız bu cihazda durur" in s.readyBody())
    }

    /**
     * Sunucu oturumu REDDETTI (jeton yok, oturum silindi): ag hatasi degil.
     * Ekran "internet"i sucladigi "ulaşılamadı"ya DUSMEZ; girissiz kuruluma
     * doner ve "Hesaba bağla" gorunur.
     */
    @Test
    fun `reddedilen oturum girissiz kuruluma doner`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.auth.rejectRefresh = true
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertFalse(s.signedIn)
        assertTrue(s.editingNames)
        assertTrue(s.showLinkFooter)
        assertEquals(0, e.api.calls, "oturumsuz hesap indirilmez")
    }

    /** Jeton alinamadi ama oturum duruyor: gecici hata, tekrar denenir. */
    @Test
    fun `jeton alinamazsa ama oturum duruyorsa hata gosterilir`() = runTest {
        val e = Env(signedIn = true)
        e.auth.tokenUnavailable = true
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Failed)

        assertTrue(s.signedIn)
        assertEquals("Oturum doğrulanamadı", s.failureDetail)
    }

    /** Hata ekranindayken oturum kapandiysa devam, girissiz kurulumdur. */
    @Test
    fun `oturum kapandiktan sonra devam girissiz kurulum`() = runTest {
        val e = Env(signedIn = true)
        e.api.failWith = "offline"
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Failed)

        e.auth.state.value = AuthState.SignedOut
        e.vm.onIntent(ProfileSetupIntent.ContinueOffline)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)
        assertFalse(s.signedIn)
        assertTrue(s.editingNames)
        assertTrue(s.showLinkFooter)
    }

    /**
     * Kurulum adini ("Ben"/"Eşim") yazan kullanicinin adi da YAZILIR. NEYDI:
     * yuklenen ad kurulum adiydi; ayni ad "degismedi" sayiliyor, profil adsiz
     * (damgasiz) kaliyordu - ikinci telefon onu hic goremiyordu.
     */
    @Test
    fun `kurulum adini yazan kullanicinin adi da yazilir`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)
        assertEquals("", s.loadedOwnerName)
        assertEquals("", s.loadedPartnerName)

        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Ben"))
        e.vm.onIntent(ProfileSetupIntent.ChangePartnerName("Eşim"))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(listOf(9_000L, 9_000L), e.stamps(), "iki profil de adlandirilmis olmali")
        assertTrue(e.repo.observeMembers().first().all { it.isNamed })
    }

    /**
     * Ekran her gorundugunde yeniden yuklenir. Olusturma modunda yazilan adlar
     * ve secim KORUNUR. NEYDI: "Hesabım var, giriş yap"a gidip geri donen (ya da
     * Android'de geri kaydirmayi yarida birakan) kullanicinin yazdiklari
     * siliniyordu.
     */
    @Test
    fun `yeniden yuklemede yazilan adlar korunur`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)
        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Volkan"))
        e.vm.onIntent(ProfileSetupIntent.ChangePartnerName("Ayşe"))
        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(false))

        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertTrue(s.editingNames)
        assertEquals("Volkan", s.ownerName)
        assertEquals("Ayşe", s.partnerName)
        assertEquals(false, s.thisDeviceIsOwner)
    }

    /** Girip hesabi BOS bulan kullanici yazdiklarini yeniden yazmaz. */
    @Test
    fun `bos hesaba girince yazilan adlar korunur`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)
        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Volkan"))
        e.vm.onIntent(ProfileSetupIntent.ChangePartnerName("Ayşe"))

        e.auth.state.value = SignedInU1
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertTrue(s.signedIn)
        assertTrue(s.editingNames)
        assertEquals("Volkan", s.ownerName)
        assertEquals("Ayşe", s.partnerName)
        assertTrue("hesabınıza da kaydedilir" in s.readyBody())
    }

    /** Hesapta profil varsa yazilanlar degil HESABIN adlari; secim yeniden sorulur. */
    @Test
    fun `hesapta profil varsa yazilanlar yerine hesabin adlari gelir`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)
        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Volkan"))
        e.vm.onIntent(ProfileSetupIntent.ChangePartnerName("Ayşe"))

        e.auth.state.value = SignedInU1
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertFalse(s.editingNames)
        assertEquals("Burak Can", s.ownerName)
        assertEquals("Merve", s.partnerName)
        assertNull(s.thisDeviceIsOwner)
    }

    /**
     * Hesap indirilemeden gecildi, cihazda adlar var (Ayarlar'dan baglanan
     * hesapsiz kullanici): adlar CIHAZIN. NEYDI: "Hesabınızda iki profil var:
     * Volkan ve Ayşe" deniyordu; hesaptan hicbir sey gelmemisti ve "Tamamla"
     * hesabi indirince bu adlar hesabinkilerle degisecekti.
     */
    @Test
    fun `indirilemeden devamda cihaz adlari hesabin sayilmaz`() = runTest {
        val e = Env(signedIn = true)
        e.repo.renameMember(LocalOwnerMemberId, "Volkan", "V")
        e.repo.renameMember(LocalPartnerMemberId, "Ayşe", "A")
        e.api.failWith = "offline"
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Failed)

        e.vm.onIntent(ProfileSetupIntent.ContinueOffline)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)
        assertTrue(s.signedIn)
        assertFalse(s.accountHasProfiles)
        assertFalse("Hesabınızda" in s.readyBody(), s.readyBody())
        assertEquals("Volkan", s.ownerName)
        assertFalse(s.canEditNames, "hesabin adlari gelince degisecek adlar duzenlenmez")
        assertEquals("e@k.app", s.accountEmail)
    }

    /**
     * Hesap indirildi ama BOS, adlar cihazda yazilmis: "Hesabınızda" denmez -
     * adlar hesaba ancak baglantiyla gidecek.
     */
    @Test
    fun `bos hesapta cihaz adlari hesabin sayilmaz`() = runTest {
        val e = Env(signedIn = true)
        e.repo.renameMember(LocalOwnerMemberId, "Volkan", "V")
        e.repo.renameMember(LocalPartnerMemberId, "Ayşe", "A")
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertTrue(s.accountDownloaded)
        assertFalse(s.accountHasProfiles)
        assertTrue(s.readyBody().startsWith("Bu cihazda iki profil var."), s.readyBody())
        assertTrue(s.canEditNames)
    }

    /** Girisliyken hangi e-postayla girildigi ekrana gelir; oturum reddedilince duser. */
    @Test
    fun `girisliyken e-posta gorunur, oturum dusunce gider`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)
        assertEquals("e@k.app", s.accountEmail)
        assertTrue(s.showAccountFooter)

        e.auth.rejectRefresh = true
        e.vm.onIntent(ProfileSetupIntent.Load)
        val gone = e.awaitPhase(ProfileSetupPhase.Ready)
        assertFalse(gone.signedIn)
        assertNull(gone.accountEmail)
        assertFalse(gone.showAccountFooter)
    }

    /** VM surec boyunca yasiyor: cikista sifirlanir, sonraki gorunus eski "done"u gormez. */
    @Test
    fun `reset eski durumu siler`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)
        e.vm.onIntent(ProfileSetupIntent.Reset)
        assertEquals(ProfileSetupUiState(), e.vm.state.value)
    }
}

private val SignedInU1 = AuthState.SignedIn(AuthSession("u1", "e@k.app", "tok", "r", 0L))

/**
 * Depo ve pull GERCEK is parcaciklarinda (Dispatchers.Default) calisiyor;
 * runTest'in sanal saati onlari beklemez, zaman asimi hemen dolardi.
 */
private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }
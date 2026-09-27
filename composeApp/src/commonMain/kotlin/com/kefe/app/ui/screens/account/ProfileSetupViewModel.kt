package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.sync.AccountLinker
import com.kefe.app.data.sync.ConflictChoice
import com.kefe.app.data.sync.LinkDecision
import com.kefe.app.data.sync.MemberRename
import com.kefe.app.data.sync.PreparedLink
import com.kefe.app.data.sync.PullEngine
import com.kefe.app.data.sync.linkedUserId
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.ui.format.trUpper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Profiller / Bu telefon kimin?" adimi - hesaba baglanmanin da adimi.
 *
 * GIRISLIYSE ONCE HESABA BAKILIR, SONRA SORULUR. Cihaz bu hesaba henuz bagli
 * degilse hesap onizlenir ([AccountLinker.preview]) ve HICBIR SEY YAZILMAZ;
 * karar tablosu (bkz. classifyLink) ne olacagini soyler:
 *  - cihaz bos: hesaptakiler iner, "bu telefon kimin" sorulur;
 *  - hesap bos: cihazdakiler hesaba gider, yine sorulur;
 *  - ayni hesaba donus (ortak kayitlar): soru da secim de yok, dogrudan baglanir;
 *  - iki tarafta da kayit: once "Hesaptakileri kullan / Birleştir / Vazgeç",
 *    sonra secim.
 * "Devam" hepsini TEK ISLEMDE yazar ([AccountLinker.commit]): hesabin
 * satirlari, adlar, bu telefonun profili ve baglanti.
 *
 * NEYDI. Hesap yuklemede hemen uygulaniyordu: cihazda da kayit varsa iki
 * portfoy kullanici hicbir sey gormeden karisiyordu; hesap indirilemeyince
 * de yerelde yazilmis adlar bir sonraki push'la hesabin ustune gidiyordu.
 * Artik onizleme patlarsa baglanti yazilmaz ve sunucuya tek satir gitmez.
 *
 * Adlar YENIDEN YAZILMAZ secim modunda; yalniz "Adları düzenle" ile degisen ad
 * yazilir - aksi halde bu cihazin damgasi hesaptakinden yeni olur ve LWW ile
 * iki telefondaki gercek adlarin uzerine yazardi.
 */
class ProfileSetupViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val preferences: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val pullEngine: PullEngine,
    private val accountLinker: AccountLinker,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    // Cihaz bu hesaba ZATEN bagliyken indirilen hesap (userId, e-posta). Kaydet
    // o zaman yalniz secimi yazar; baglanti zaten var.
    private var pulledAccount: Pair<String, String>? = null

    // Indirilen hesap adlandirilmis bir profil GETIRDI mi (bkz. load). "Hesabınızda
    // iki profil var" yalniz o zaman denir.
    private var accountBroughtNames = false

    // Onizlenmis, HENUZ YAZILMAMIS baglanti. Kaydet onu tek islemde kurar.
    private var pending: PendingLink? = null

    fun onIntent(intent: ProfileSetupIntent) {
        when (intent) {
            ProfileSetupIntent.Load, ProfileSetupIntent.Retry -> load()

            is ProfileSetupIntent.ChooseConflict -> {
                val link = pending ?: return
                if (_state.value.phase != ProfileSetupPhase.Conflict) return
                pending = link.copy(choice = intent.choice)
                viewModelScope.launch { showLinkPick(link.prepared, intent.choice) }
            }

            ProfileSetupIntent.BackToConflict -> {
                val link = pending ?: return
                if (link.prepared.decision != LinkDecision.Conflict || _state.value.saving) return
                pending = link.copy(choice = null)
                _state.value = _state.value.copy(phase = ProfileSetupPhase.Conflict, conflictChoice = null)
            }

            // Oturum yeniden okunur: bu arada kapandiysa (sunucu jetonu
            // reddetti) bu artik girissiz bir kurulumdur - ad yazilir ve
            // "Hesaba bağla" gorunur. NEYDI: signedIn true kaliyor, giris
            // baglantisi gizleniyordu; girissiz kullanici geri donemiyordu.
            ProfileSetupIntent.ContinueOffline -> viewModelScope.launch {
                pending = null
                val auth = currentAuth()
                val signedIn = auth is AuthState.SignedIn
                _state.value = _state.value.copy(
                    signedIn = signedIn,
                    accountEmail = (auth as? AuthState.SignedIn)?.session?.email,
                    linking = false,
                )
                showMembers(pickOnly = signedIn)
            }

            ProfileSetupIntent.EditNames -> _state.value = _state.value.copy(editingNames = true)

            is ProfileSetupIntent.ChangeOwnerName ->
                _state.value = _state.value.copy(ownerName = intent.value)

            is ProfileSetupIntent.ChangePartnerName ->
                _state.value = _state.value.copy(partnerName = intent.value)

            is ProfileSetupIntent.SelectThisDevice ->
                _state.value = _state.value.copy(thisDeviceIsOwner = intent.isOwner)

            ProfileSetupIntent.Save -> save()

            ProfileSetupIntent.Reset -> {
                loadJob?.cancel()
                pulledAccount = null
                accountBroughtNames = false
                pending = null
                _state.value = ProfileSetupUiState()
            }
        }
    }

    /**
     * Ekran her gorundugunde yeniden calisir: "Hesaba bağla" ile
     * giris ekranina gidip donuldugunde oturum artik acik ve hesaba bakilmali.
     */
    private fun load() {
        loadJob?.cancel()
        pulledAccount = null
        accountBroughtNames = false
        pending = null
        _state.value = _state.value.copy(
            phase = ProfileSetupPhase.Checking,
            failureDetail = null,
            commitFailed = false,
            done = false,
            linkedNow = false,
            linking = false,
            conflictChoice = null,
            cloudConfigured = authRepository.isCloudConfigured,
        )
        loadJob = viewModelScope.launch {
            val auth = currentAuth()
            val signedIn = auth is AuthState.SignedIn
            _state.value = _state.value.copy(
                signedIn = signedIn,
                accountEmail = (auth as? AuthState.SignedIn)?.session?.email,
            )
            if (auth !is AuthState.SignedIn) {
                showMembers(pickOnly = false)
                return@launch
            }

            _state.value = _state.value.copy(phase = ProfileSetupPhase.Syncing)
            // Devralma ve onizleme BAGLANTIYA gore: cihaz bu hesaba zaten
            // bagliysa (ayni hesaba yeniden giris, profil secimi eksik) olagan
            // LWW pull'u yeter - esitleme zaten o hesapla calisiyor. Degilse hesap
            // yalniz ONIZLENIR. NEYDI: "hic push'lamadi mi" (LastPushedAt == null)
            // soruluyordu - ilk pull patlayip push gecince isaret kalici
            // kayboluyordu.
            val link = preferences.get(PreferenceKeys.CloudLinkUserId)
            val alreadyLinked = linkedUserId(auth, link) != null
            val stampsBefore = memberStamps()
            val result = runCatching {
                // Jeton alinamiyorsa hesaba ulasilmadi - "bos hesap" sanilmasin.
                if (authRepository.validAccessToken() == null) {
                    // Iki ayri sebep, iki ayri ekran. Sunucu yenileme jetonunu
                    // REDDETTIYSE oturum silinmistir: kullanici artik girissiz,
                    // ekran hesapsiz kuruluma doner ve "Hesaba bağla" gorunur.
                    // Oturum duruyorsa gecici bir ag hatasi: tekrar denenir.
                    if (currentAuth() !is AuthState.SignedIn) throw SessionGone()
                    error("Oturum doğrulanamadı")
                }
                if (alreadyLinked) {
                    pullEngine.pullOnce(adoptServerMembers = false)
                    null
                } else {
                    accountLinker.preview() ?: error("Oturum doğrulanamadı")
                }
            }
            result.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error
                if (error is SessionGone) {
                    _state.value = _state.value.copy(signedIn = false, accountEmail = null)
                    showMembers(pickOnly = false)
                    return@launch
                }
                println("Kefe profil: hesaba bakilamadi - ${error.message}")
                _state.value = _state.value.copy(
                    phase = ProfileSetupPhase.Failed,
                    failureDetail = error.message?.take(160),
                )
                return@launch
            }

            val prepared = result.getOrNull()
            if (prepared == null) {
                // Bagli cihaz: hesap LWW ile indi. Pull'dan ONCEKI damgalar:
                // adlandirilmis bir profilin damgasi degistiyse adi hesap getirdi.
                pulledAccount = auth.session.userId to auth.session.email
                accountBroughtNames = memberStamps().any { (id, stamp) -> stamp > 0L && stampsBefore[id] != stamp }
                showMembers(pickOnly = false)
                return@launch
            }

            pending = PendingLink(auth.session.userId, auth.session.email, prepared, choice = null)
            when (prepared.decision) {
                LinkDecision.Conflict -> _state.value = _state.value.copy(
                    phase = ProfileSetupPhase.Conflict,
                    conflictLocal = prepared.preview.localRecords,
                    conflictServer = prepared.preview.serverRecords,
                    conflictChoice = null,
                )

                // Ayni hesaba donus: ortak kayitlar var, soru yok, secim de yok -
                // bu telefon zaten kim oldugunu biliyor. Profil hic secilmediyse
                // (kurulum yarida kaldi) secim yine sorulur.
                LinkDecision.Relink -> {
                    val active = preferences.get(PreferenceKeys.ActiveMemberId)
                    if (active != null) {
                        commit(pending!!, activeMemberId = active, renames = emptyList())
                    } else {
                        showLinkPick(prepared, choice = null)
                    }
                }

                LinkDecision.Download, LinkDecision.Upload -> showLinkPick(prepared, choice = null)
            }
        }
    }

    /** Profil kimligi -> damga. Damga 0 = kurulumun adsiz satiri. */
    private suspend fun memberStamps(): Map<String, Long> =
        portfolioRepository.observeMembers().first().associate { it.id to it.updatedAt }

    /**
     * Baglanti bekliyorken secim ekrani. Adlar HESAPTAN (adlandirilmissa) - cihaza
     * henuz inmediler, "Devam" indirecek. Hesap adsizsa cihazin adlari; o da
     * yoksa iki ad yazilir.
     *
     * SECIM ZORUNLU: hazir isaretli bir satir olmaz. Cihazin eski secimi
     * cihazdaki adlara gore yapilmisti; hesabin adlari gelince o secim diger
     * kisiyi gosterebilir (bkz. remapNote).
     *
     * "Hesaptakileri kullan"da cihazin adlari sayilmaz - cihazdaki her sey
     * silinecek.
     */
    private suspend fun showLinkPick(prepared: PreparedLink, choice: ConflictChoice?) {
        val members = portfolioRepository.observeMembers().first()
        val owner = members.firstOrNull { it.id == LocalOwnerMemberId }
        val partner = members.firstOrNull { it.id == LocalPartnerMemberId }
        val replace = choice == ConflictChoice.UseAccount
        val serverNamed = prepared.serverNamed
        val localNamed = !replace && members.any { it.isNamed }
        val pick = serverNamed || localNamed
        val current = _state.value
        val keepTyped = !pick && current.editingNames && !current.profilesNamed
        val typedSomething = current.ownerName.isNotBlank() || current.partnerName.isNotBlank()
        val ownerLoaded = when {
            serverNamed -> prepared.serverOwnerName.orEmpty()
            localNamed -> owner.namedOrEmpty()
            else -> ""
        }
        val partnerLoaded = when {
            serverNamed -> prepared.serverPartnerName.orEmpty()
            localNamed -> partner.namedOrEmpty()
            else -> ""
        }
        _state.value = current.copy(
            phase = ProfileSetupPhase.Ready,
            linking = true,
            accountDownloaded = true,
            conflictChoice = choice,
            profilesNamed = pick,
            accountHasProfiles = serverNamed,
            editingNames = !pick,
            ownerName = if (pick) ownerLoaded else if (keepTyped) current.ownerName else "",
            partnerName = if (pick) partnerLoaded else if (keepTyped) current.partnerName else "",
            loadedOwnerName = ownerLoaded,
            loadedPartnerName = partnerLoaded,
            // Ad yazip gelen kullanicinin ekranda gordugu secim kalir; digerinde
            // secim bos baslar.
            thisDeviceIsOwner = if (keepTyped && typedSomething) current.thisDeviceIsOwner else null,
            previousMemberId = preferences.get(PreferenceKeys.ActiveMemberId),
            localOnlyByAuthor = if (replace) emptyMap() else prepared.localOnlyByAuthor,
        )
    }

    /**
     * Yereldeki profilleri ekrana koyar (baglanti beklemiyorken). Adlandirilmis
     * profil varsa (ya da [pickOnly]) yalniz secim yapilir; yoksa adlar yazilir.
     */
    private suspend fun showMembers(pickOnly: Boolean) {
        val members = portfolioRepository.observeMembers().first()
        val owner = members.firstOrNull { it.id == LocalOwnerMemberId }
        val partner = members.firstOrNull { it.id == LocalPartnerMemberId }
        val named = members.any { it.isNamed }
        val pick = named || pickOnly
        val current = _state.value
        // YAZILANLAR KORUNUR. Ekran her gorundugunde (LaunchedEffect) yeniden
        // yuklenir: "Hesaba bağla"ya gidip geri donen ya da hesabi bos cikan
        // kullanicinin yazdigi iki ad ve secimi siliniyordu. Yalniz zaten
        // olusturma modundaysak korunur; secime gecilince hesabin adlari gelir.
        // Varsayilan durum da bu kosulu saglar (bos adlar, true) - ilk yukleme
        // ve Reset sonrasi degisen bir sey yok.
        val keepTyped = !pick && current.editingNames && !current.profilesNamed
        _state.value = current.copy(
            phase = ProfileSetupPhase.Ready,
            linking = false,
            conflictChoice = null,
            previousMemberId = null,
            localOnlyByAuthor = emptyMap(),
            profilesNamed = named,
            // Hesap sozu yalniz hesap INDIRILDIYSE ve adlari o getirdiyse. NEYDI:
            // "adlandirilmis ve girisli" yetiyordu; "Şimdilik hesapsız devam et"
            // cihazda yazilmis adlari "Hesabınızda iki profil var" diye sunuyordu.
            accountDownloaded = pulledAccount != null,
            accountHasProfiles = named && current.signedIn && pulledAccount != null && accountBroughtNames,
            editingNames = !pick,
            // Kurulumun "Ben"/"Eşim"i YAZILMIS BIR AD DEGIL; ne alana konur ne
            // secenek olarak gosterilir. NEYDI: hesap indirilemeden gecilince
            // "Bu telefon kimin?" "Ben" ile "Eşim" arasinda soruluyordu; esin
            // telefonu dogal olarak "Ben"i (sahibin profili) seciyor, hesap
            // gelince o profil karsi tarafin adini aliyordu. Adsiz satirlar
            // "1. profil"/"2. profil" okunur.
            ownerName = when {
                pick -> owner.namedOrEmpty()
                keepTyped -> current.ownerName
                else -> ""
            },
            partnerName = when {
                pick -> partner.namedOrEmpty()
                keepTyped -> current.partnerName
                else -> ""
            },
            // Karsilastirma yalniz adlandirilmis profille: kurulum adiyla
            // karsilastirilinca "Ben"/"Eşim" yazan kullanicinin adi "degismedi"
            // sayiliyor, hic yazilmiyor ve profil adsiz (damgasiz) kaliyordu.
            loadedOwnerName = owner.namedOrEmpty(),
            loadedPartnerName = partner.namedOrEmpty(),
            thisDeviceIsOwner = when {
                pick -> null
                keepTyped -> current.thisDeviceIsOwner ?: true
                else -> true
            },
        )
    }

    /** Oturumun su anki hali; acilistaki "bilinmiyor" beklenir. */
    private suspend fun currentAuth(): AuthState =
        authRepository.observeAuthState().first { it !is AuthState.Unknown }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        val isOwner = s.thisDeviceIsOwner ?: return
        val activeMemberId = if (isOwner) LocalOwnerMemberId else LocalPartnerMemberId
        _state.value = s.copy(saving = true)
        val link = pending
        viewModelScope.launch {
            // Secim modunda adlara DOKUNULMAZ. Duzenlemede yalniz DEGISEN ad
            // yazilir: degismeyeni yeniden yazmak damgasini tazeler ve digerinin
            // telefondaki son hali ezilebilirdi.
            val renames = if (s.editingNames) {
                listOfNotNull(
                    renameOf(LocalOwnerMemberId, s.ownerName, s.loadedOwnerName),
                    renameOf(LocalPartnerMemberId, s.partnerName, s.loadedPartnerName),
                )
            } else {
                emptyList()
            }

            if (link != null) {
                commit(link, activeMemberId, renames)
                return@launch
            }

            renames.forEach {
                portfolioRepository.renameMember(memberId = it.memberId, name = it.name, initials = it.initials)
            }
            // Secim ve (bagli hesap yeniden indirildiyse) baglanti TEK islemde:
            // yarim yazilirsa esitleme "bu telefon kimin" secilmeden baslayabilirdi.
            val account = linkToCommit()
            preferences.putAll(
                buildMap {
                    put(PreferenceKeys.ActiveMemberId, activeMemberId)
                    if (account != null) {
                        put(PreferenceKeys.CloudLinkUserId, account.first)
                        put(PreferenceKeys.CloudLinkEmail, account.second)
                        put(PreferenceKeys.LocalRestoredAt, null)
                    }
                },
            )
            _state.value = _state.value.copy(saving = false, done = true)
        }
    }

    /**
     * Bekleyen baglantiyi kurar. Oturum bu arada kapandiysa ya da baska bir
     * hesaba gectiyse KURULMAZ: onizlenen hesap artik oturumun hesabi degil.
     * Ekran yeniden yuklenir - secim hesabin adlarina gore yapilmisti, cihazin
     * adlariyla yazilirsa telefon yanlis kisi olurdu.
     *
     * Patlarsa hicbir sey yazilmamistir (tek islem): "Tekrar dene". Hata AG
     * degil YEREL yazma: ekran "internetinizi kontrol edin" demez (bkz.
     * commitFailed, failureMessage).
     */
    private suspend fun commit(link: PendingLink, activeMemberId: String, renames: List<MemberRename>) {
        _state.value = _state.value.copy(saving = true)
        val session = (currentAuth() as? AuthState.SignedIn)?.session
        if (session?.userId != link.userId) {
            _state.value = _state.value.copy(saving = false)
            load()
            return
        }
        val result = runCatching {
            accountLinker.commit(
                prepared = link.prepared,
                choice = link.choice,
                userId = link.userId,
                email = link.email,
                activeMemberId = activeMemberId,
                renames = renames,
            )
        }
        result.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
            println("Kefe profil: baglanti kurulamadi - ${error.message}")
            _state.value = _state.value.copy(
                saving = false,
                phase = ProfileSetupPhase.Failed,
                failureDetail = error.message?.take(160),
                commitFailed = true,
            )
            return
        }
        pending = null
        _state.value = _state.value.copy(saving = false, done = true, linkedNow = true)
    }

    /**
     * Bagli hesabi yeniden indiren yuklemede yazilacak baglanti: oturum HALA o
     * hesaptaysa. Ekranda beklerken cikis yapildiysa ya da baska hesaba
     * girildiyse yazilmaz.
     */
    private suspend fun linkToCommit(): Pair<String, String>? {
        val pulled = pulledAccount ?: return null
        val session = (currentAuth() as? AuthState.SignedIn)?.session ?: return null
        return pulled.takeIf { session.userId == it.first }
    }

    private fun renameOf(memberId: String, typed: String, loaded: String): MemberRename? {
        val name = typed.trim()
        if (name.isEmpty() || name == loaded.trim()) return null
        return MemberRename(memberId = memberId, name = name, initials = name.initials())
    }
}

/** Onizlenmis, henuz yazilmamis baglanti ve (cakismada) kullanicinin secimi. */
private data class PendingLink(
    val userId: String,
    val email: String,
    val prepared: PreparedLink,
    val choice: ConflictChoice?,
)

/** Yalniz adlandirilmis profilin adi; kurulumun "Ben"/"Eşim"i bos sayilir. */
private fun Member?.namedOrEmpty(): String = this?.takeIf { it.isNamed }?.name.orEmpty()

/** Sunucu oturumu reddetti ve oturum silindi - ag hatasi degil. */
private class SessionGone : Exception()

/** Addan bas harf: ilk harf, Turkce buyuk. Bos ad "?" verir. */
private fun String.initials(): String =
    trim().firstOrNull()?.toString()?.trUpper() ?: "?"

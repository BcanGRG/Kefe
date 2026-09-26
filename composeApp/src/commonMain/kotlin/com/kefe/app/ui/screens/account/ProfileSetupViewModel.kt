package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
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
 * "Profiller / Bu telefon kimin?" adimi.
 *
 * GIRISLIYSE ONCE HESAP INDIRILIR, SONRA SORULUR. Hesapta adlandirilmis
 * profil varsa yalniz "hangisi sensin" sorulur ve kaydetmek YALNIZ bu cihazin
 * secimini ([PreferenceKeys.ActiveMemberId]) yazar - adlara dokunmaz. Hesapta
 * profil yoksa (ilk telefon ya da hesapsiz baslangic) iki ad yazilir.
 *
 * NEYDI. Adlar bir kez, pull beklenmeden okunuyordu: yeni telefonda veritabani
 * henuz kurulumun "Ben"/"Eşim"ini tasiyordu, ekran bos alanlar gosteriyordu ve
 * kaydetmek iki adi `updatedAt = simdi` ile yeniden yaziyordu - hesaptaki
 * gercek adlardan YENI bir damgayla. Push onlari iki telefonun ustune itti.
 *
 * HESAP BAGLANTISI BURADA YAZILIR. Esitleme yalniz cihaz bir hesaba BAGLIYKEN
 * calisir (bkz. CloudMode); baglanti ancak hesap BASARIYLA indirildikten ve bu
 * telefonun kim oldugu secildikten sonra, secimle AYNI islemde yazilir. Hesap
 * indirilemeden gecilirse ("Şimdilik hesapsız devam et") baglanti yazilmaz: mod
 * "Bağlantı yarım" kalir, hicbir sey gonderilmez ve cekilmez.
 */
class ProfileSetupViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val preferences: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val pullEngine: PullEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    // Bu yuklemede BASARIYLA indirilen hesap (userId, e-posta). Kaydet
    // baglantiyi yalniz bu doluysa yazar; girissiz ya da indirilemeyen
    // yuklemede null kalir.
    private var pulledAccount: Pair<String, String>? = null

    // Indirilen hesap adlandirilmis bir profil GETIRDI mi (bkz. load). "Hesabınızda
    // iki profil var" yalniz o zaman denir.
    private var accountBroughtNames = false

    fun onIntent(intent: ProfileSetupIntent) {
        when (intent) {
            ProfileSetupIntent.Load, ProfileSetupIntent.Retry -> load()

            // Oturum yeniden okunur: bu arada kapandiysa (sunucu jetonu
            // reddetti) bu artik girissiz bir kurulumdur - ad yazilir ve
            // "Hesaba bağla" gorunur. NEYDI: signedIn true kaliyor, giris
            // baglantisi gizleniyordu; girissiz kullanici geri donemiyordu.
            ProfileSetupIntent.ContinueOffline -> viewModelScope.launch {
                val auth = currentAuth()
                val signedIn = auth is AuthState.SignedIn
                _state.value = _state.value.copy(
                    signedIn = signedIn,
                    accountEmail = (auth as? AuthState.SignedIn)?.session?.email,
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
                _state.value = ProfileSetupUiState()
            }
        }
    }

    /**
     * Ekran her gorundugunde yeniden calisir: "Hesaba bağla" ile
     * giris ekranina gidip donuldugunde oturum artik acik ve hesap indirilmeli.
     */
    private fun load() {
        loadJob?.cancel()
        pulledAccount = null
        accountBroughtNames = false
        _state.value = _state.value.copy(
            phase = ProfileSetupPhase.Checking,
            failureDetail = null,
            done = false,
            cloudConfigured = authRepository.isCloudConfigured,
        )
        loadJob = viewModelScope.launch {
            val auth = currentAuth()
            val signedIn = auth is AuthState.SignedIn
            _state.value = _state.value.copy(
                signedIn = signedIn,
                accountEmail = (auth as? AuthState.SignedIn)?.session?.email,
            )

            if (auth is AuthState.SignedIn) {
                _state.value = _state.value.copy(phase = ProfileSetupPhase.Syncing)
                // Pull'dan ONCEKI damgalar: adlandirilmis bir profilin damgasi
                // degistiyse adi hesap getirdi. Degismediyse adlar cihazda
                // yazilmisti (hesap bos) ya da zaten ayniydi (ayni hesaba donus);
                // ikisinde de "Bu cihazda" demek dogru.
                val stampsBefore = memberStamps()
                val pulled = runCatching {
                    // Jeton alinamiyorsa pull sessizce 0 donerdi - "indirildi"
                    // sanilip bos bir hesap gibi davranilmasin.
                    if (authRepository.validAccessToken() == null) {
                        // Iki ayri sebep, iki ayri ekran. Sunucu yenileme
                        // jetonunu REDDETTIYSE oturum silinmistir: kullanici artik
                        // girissiz, ekran hesapsiz kuruluma doner ve "Hesaba
                        // bağla" gorunur. NEYDI: ikisi de "İnternet bağlantınızı
                        // kontrol edin" diyordu ve signedIn true kaldigi icin
                        // giris baglantisi gizleniyordu. Oturum duruyorsa gecici
                        // bir ag hatasi: tekrar denenir.
                        if (currentAuth() !is AuthState.SignedIn) throw SessionGone()
                        error("Oturum doğrulanamadı")
                    }
                    // Devralma BAGLANTIYA gore: cihaz bu hesaba zaten bagliysa
                    // (ayni hesaba yeniden giris) pull duz LWW'dir; degilse
                    // hesabin adlari devralinir. NEYDI: "hic push'lamadi mi"
                    // (LastPushedAt == null) soruluyordu - ilk pull patlayip
                    // push gecince isaret kalici kayboluyor, devralma bir daha
                    // olmuyordu; baska hesaba geciste de hic olmuyordu.
                    val link = preferences.get(PreferenceKeys.CloudLinkUserId)
                    val alreadyLinked = linkedUserId(auth, link) != null
                    pullEngine.pullOnce(adoptServerMembers = !alreadyLinked)
                }
                pulled.exceptionOrNull()?.let { error ->
                    if (error is CancellationException) throw error
                    if (error is SessionGone) {
                        _state.value = _state.value.copy(signedIn = false, accountEmail = null)
                        showMembers(pickOnly = false)
                        return@launch
                    }
                    println("Kefe profil: hesap indirilemedi - ${error.message}")
                    _state.value = _state.value.copy(
                        phase = ProfileSetupPhase.Failed,
                        failureDetail = error.message?.take(160),
                    )
                    return@launch
                }
                pulledAccount = auth.session.userId to auth.session.email
                accountBroughtNames = memberStamps().any { (id, stamp) -> stamp > 0L && stampsBefore[id] != stamp }
            }
            showMembers(pickOnly = false)
        }
    }

    /** Profil kimligi -> damga. Damga 0 = kurulumun adsiz satiri. */
    private suspend fun memberStamps(): Map<String, Long> =
        portfolioRepository.observeMembers().first().associate { it.id to it.updatedAt }

    /**
     * Yereldeki profilleri ekrana koyar. Adlandirilmis profil varsa (ya da
     * [pickOnly]) yalniz secim yapilir; yoksa adlar yazilir.
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
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            // Secim modunda adlara DOKUNULMAZ. Duzenlemede yalniz DEGISEN ad
            // yazilir: degismeyeni yeniden yazmak damgasini tazeler ve digerinin
            // telefondaki son hali ezilebilirdi.
            if (s.editingNames) {
                renameIfChanged(LocalOwnerMemberId, s.ownerName, s.loadedOwnerName)
                renameIfChanged(LocalPartnerMemberId, s.partnerName, s.loadedPartnerName)
            }
            // Secim ve (hesap indirildiyse) baglanti TEK islemde: yarim
            // yazilirsa esitleme "bu telefon kimin" secilmeden baslayabilirdi.
            val link = linkToCommit()
            val previousLink = preferences.get(PreferenceKeys.CloudLinkUserId)
            preferences.putAll(
                buildMap {
                    put(
                        PreferenceKeys.ActiveMemberId,
                        if (isOwner) LocalOwnerMemberId else LocalPartnerMemberId,
                    )
                    if (link != null) {
                        put(PreferenceKeys.CloudLinkUserId, link.first)
                        put(PreferenceKeys.CloudLinkEmail, link.second)
                        // Baglanti kuruldu: hesapsizken yuklenen yedegin izi artik
                        // bir sonraki baglantiyi ilgilendirmez.
                        put(PreferenceKeys.LocalRestoredAt, null)
                        // YENI bir baglantida (hic bagli degildi, acik cikistan
                        // sonra ya da baska hesaptan geliyor) watermark sifirlanir.
                        // NEYDI: eski hesabin watermark'i (T) kaliyordu ve push
                        // yalniz T'den sonra degisenleri gonderiyordu - T'den once
                        // kurulan pozisyon, hedef ve uyeler yeni hesaba hic
                        // gitmiyor, karsi telefon pozisyonu olmayan islemler
                        // goruyordu. Ayni hesaba tam yeniden gonderim zararsiz:
                        // sunucunun LWW korumasi esit/eski damgayi yok sayar.
                        if (previousLink != link.first) {
                            put(PreferenceKeys.LastPushedAt, null)
                            // "Son eşitleme" de onceki baglantinin ani; yeni
                            // hesabin ilk turu kendi anini yazar.
                            put(PreferenceKeys.LastSyncedAt, null)
                        }
                    }
                },
            )
            _state.value = _state.value.copy(saving = false, done = true)
        }
    }

    /**
     * Yazilacak baglanti: bu yuklemede indirilen hesap, oturum HALA o hesaptaysa.
     * Ekranda beklerken cikis yapildiysa ya da baska hesaba girildiyse baglanti
     * yazilmaz - indirilen hesap artik oturumun hesabi degil.
     */
    private suspend fun linkToCommit(): Pair<String, String>? {
        val pulled = pulledAccount ?: return null
        val session = (currentAuth() as? AuthState.SignedIn)?.session ?: return null
        return pulled.takeIf { session.userId == it.first }
    }

    private suspend fun renameIfChanged(memberId: String, typed: String, loaded: String) {
        val name = typed.trim()
        if (name.isEmpty() || name == loaded.trim()) return
        portfolioRepository.renameMember(memberId = memberId, name = name, initials = name.initials())
    }
}

/** Yalniz adlandirilmis profilin adi; kurulumun "Ben"/"Eşim"i bos sayilir. */
private fun Member?.namedOrEmpty(): String = this?.takeIf { it.isNamed }?.name.orEmpty()

/** Sunucu oturumu reddetti ve oturum silindi - ag hatasi degil. */
private class SessionGone : Exception()

/** Addan bas harf: ilk harf, Turkce buyuk. Bos ad "?" verir. */
private fun String.initials(): String =
    trim().firstOrNull()?.toString()?.trUpper() ?: "?"

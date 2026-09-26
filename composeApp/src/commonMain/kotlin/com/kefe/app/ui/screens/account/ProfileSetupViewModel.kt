package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.sync.PullEngine
import com.kefe.app.data.sync.linkedUserId
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
 * indirilemeden gecilirse ("Bağlanmadan devam et") baglanti yazilmaz: mod
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

    fun onIntent(intent: ProfileSetupIntent) {
        when (intent) {
            ProfileSetupIntent.Load, ProfileSetupIntent.Retry -> load()

            ProfileSetupIntent.ContinueOffline -> viewModelScope.launch { showMembers(pickOnly = true) }

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
                _state.value = ProfileSetupUiState()
            }
        }
    }

    /**
     * Ekran her gorundugunde yeniden calisir: "Hesabım var, giriş yap" ile
     * giris ekranina gidip donuldugunde oturum artik acik ve hesap indirilmeli.
     */
    private fun load() {
        loadJob?.cancel()
        pulledAccount = null
        _state.value = _state.value.copy(phase = ProfileSetupPhase.Checking, failureDetail = null, done = false)
        loadJob = viewModelScope.launch {
            val auth = authRepository.observeAuthState().first { it !is AuthState.Unknown }
            val signedIn = auth is AuthState.SignedIn
            _state.value = _state.value.copy(signedIn = signedIn)

            if (auth is AuthState.SignedIn) {
                _state.value = _state.value.copy(phase = ProfileSetupPhase.Syncing)
                val pulled = runCatching {
                    // Jeton alinamiyorsa pull sessizce 0 donerdi - "indirildi"
                    // sanilip bos bir hesap gibi davranilmasin.
                    authRepository.validAccessToken() ?: error("Oturum doğrulanamadı")
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
                    println("Kefe profil: hesap indirilemedi - ${error.message}")
                    _state.value = _state.value.copy(
                        phase = ProfileSetupPhase.Failed,
                        failureDetail = error.message?.take(160),
                    )
                    return@launch
                }
                pulledAccount = auth.session.userId to auth.session.email
            }
            showMembers(pickOnly = false)
        }
    }

    /**
     * Yereldeki profilleri ekrana koyar. Hesapta adlandirilmis profil varsa
     * (ya da [pickOnly]) yalniz secim yapilir; yoksa adlar yazilir.
     */
    private suspend fun showMembers(pickOnly: Boolean) {
        val members = portfolioRepository.observeMembers().first()
        val owner = members.firstOrNull { it.id == LocalOwnerMemberId }
        val partner = members.firstOrNull { it.id == LocalPartnerMemberId }
        val named = members.any { it.isNamed }
        val pick = named || pickOnly
        _state.value = _state.value.copy(
            phase = ProfileSetupPhase.Ready,
            accountHasProfiles = named,
            editingNames = !pick,
            // Olusturmada alanlar bos: kurulumun "Ben"/"Eşim"i yazilmis bir ad degil.
            ownerName = if (pick) owner?.name.orEmpty() else "",
            partnerName = if (pick) partner?.name.orEmpty() else "",
            loadedOwnerName = owner?.name.orEmpty(),
            loadedPartnerName = partner?.name.orEmpty(),
            thisDeviceIsOwner = if (pick) null else true,
        )
    }

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
        val auth = authRepository.observeAuthState().first { it !is AuthState.Unknown }
        val session = (auth as? AuthState.SignedIn)?.session ?: return null
        return pulled.takeIf { session.userId == it.first }
    }

    private suspend fun renameIfChanged(memberId: String, typed: String, loaded: String) {
        val name = typed.trim()
        if (name.isEmpty() || name == loaded.trim()) return
        portfolioRepository.renameMember(memberId = memberId, name = name, initials = name.initials())
    }
}

/** Addan bas harf: ilk harf, Turkce buyuk. Bos ad "?" verir. */
private fun String.initials(): String =
    trim().firstOrNull()?.toString()?.trUpper() ?: "?"

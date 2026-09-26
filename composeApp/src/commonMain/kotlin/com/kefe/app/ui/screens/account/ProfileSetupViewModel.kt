package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.sync.PullEngine
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
        _state.value = _state.value.copy(phase = ProfileSetupPhase.Checking, failureDetail = null, done = false)
        loadJob = viewModelScope.launch {
            val auth = authRepository.observeAuthState().first { it !is AuthState.Unknown }
            val signedIn = auth is AuthState.SignedIn
            _state.value = _state.value.copy(signedIn = signedIn)

            if (signedIn) {
                _state.value = _state.value.copy(phase = ProfileSetupPhase.Syncing)
                val pulled = runCatching {
                    // Jeton alinamiyorsa pull sessizce 0 donerdi - "indirildi"
                    // sanilip bos bir hesap gibi davranilmasin.
                    authRepository.validAccessToken() ?: error("Oturum doğrulanamadı")
                    val firstLink = preferences.get(PreferenceKeys.LastPushedAt) == null
                    pullEngine.pullOnce(adoptServerMembers = firstLink)
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
            preferences.put(
                PreferenceKeys.ActiveMemberId,
                if (isOwner) LocalOwnerMemberId else LocalPartnerMemberId,
            )
            _state.value = _state.value.copy(saving = false, done = true)
        }
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

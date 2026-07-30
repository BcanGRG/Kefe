package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.ui.format.trUpper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Bu telefon kimin?" adimi.
 *
 * Iki profil bootstrap'ta kuruluydu; burada adlari yazip bu cihazin hangisi
 * oldugunu isaretleriz. Kaydetmek uc sey yapar: iki adi da gunceller ve
 * [PreferenceKeys.ActiveMemberId]'yi bu cihazin profiline yazar.
 */
class ProfileSetupViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    init {
        prefillFromMembers()
    }

    fun onIntent(intent: ProfileSetupIntent) {
        when (intent) {
            is ProfileSetupIntent.ChangeOwnerName ->
                _state.value = _state.value.copy(ownerName = intent.value)

            is ProfileSetupIntent.ChangePartnerName ->
                _state.value = _state.value.copy(partnerName = intent.value)

            is ProfileSetupIntent.SelectThisDevice ->
                _state.value = _state.value.copy(thisDeviceIsOwner = intent.isOwner)

            ProfileSetupIntent.Save -> save()
        }
    }

    /**
     * Ikinci cihazda adlar SENKRONDAN gelmis olabilir - varsayilan "Ben"/"Eşim"
     * yerine gercek adlari gosteririz. Varsayilanlar bos birakilir ki kullanici
     * ilk kurulumda temiz alan gorsun.
     */
    private fun prefillFromMembers() {
        viewModelScope.launch {
            val members = portfolioRepository.observeMembers().first()
            val owner = members.firstOrNull { it.id == LocalOwnerMemberId }
            val partner = members.firstOrNull { it.id == LocalPartnerMemberId }
            _state.value = _state.value.copy(
                ownerName = owner?.name?.takeUnless { it == "Ben" }.orEmpty(),
                partnerName = partner?.name?.takeUnless { it == "Eşim" }.orEmpty(),
            )
        }
    }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            portfolioRepository.renameMember(
                memberId = LocalOwnerMemberId,
                name = s.ownerName.trim(),
                initials = s.ownerName.initials(),
            )
            portfolioRepository.renameMember(
                memberId = LocalPartnerMemberId,
                name = s.partnerName.trim(),
                initials = s.partnerName.initials(),
            )
            preferences.put(
                PreferenceKeys.ActiveMemberId,
                if (s.thisDeviceIsOwner) LocalOwnerMemberId else LocalPartnerMemberId,
            )
            _state.value = _state.value.copy(saving = false, done = true)
        }
    }
}

/** Addan bas harf: ilk harf, Turkce buyuk. Bos ad "?" verir. */
private fun String.initials(): String =
    trim().firstOrNull()?.toString()?.trUpper() ?: "?"

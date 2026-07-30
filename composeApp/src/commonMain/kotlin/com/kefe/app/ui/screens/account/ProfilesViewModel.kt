package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.ui.format.trUpper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Profiller ekrani. Iki profilin adini duzenler ve bu cihazin hangi profil
 * oldugunu degistirir - degisiklikler ANLIK diske yazilir, "kaydet" adimi yok.
 *
 * Eski ShareViewModel'in bellek ici izin/cikarma hilesi (permissionOverrides,
 * removedMemberIds) tamamen gitti: artik gercek yazma var.
 */
class ProfilesViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    init {
        observe()
    }

    fun onIntent(intent: ProfilesIntent) {
        when (intent) {
            is ProfilesIntent.SetThisDevice -> viewModelScope.launch {
                preferences.put(PreferenceKeys.ActiveMemberId, intent.memberId)
            }

            is ProfilesIntent.OpenRename -> {
                val row = _state.value.profiles.firstOrNull { it.id == intent.memberId } ?: return
                _state.value = _state.value.copy(
                    editing = ProfileNameEdit(id = row.id, name = row.name),
                )
            }

            is ProfilesIntent.ChangeName -> _state.value = _state.value.copy(
                editing = _state.value.editing?.copy(name = intent.value),
            )

            ProfilesIntent.SaveName -> saveName()

            ProfilesIntent.DismissRename -> _state.value = _state.value.copy(editing = null)
        }
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observeMembers(),
                preferences.observeAll(),
            ) { members, prefs ->
                val activeId = prefs[PreferenceKeys.ActiveMemberId]
                members.mapIndexed { index, member ->
                    ProfileRow(
                        id = member.id,
                        name = member.name,
                        initials = member.initials,
                        index = index,
                        isThisDevice = member.id == activeId,
                    )
                }
            }.collect { rows ->
                _state.value = _state.value.copy(profiles = rows)
            }
        }
    }

    private fun saveName() {
        val edit = _state.value.editing ?: return
        val name = edit.name.trim()
        if (name.isBlank()) {
            _state.value = _state.value.copy(editing = null)
            return
        }
        viewModelScope.launch {
            portfolioRepository.renameMember(
                memberId = edit.id,
                name = name,
                initials = name.firstOrNull()?.toString()?.trUpper() ?: "?",
            )
            _state.value = _state.value.copy(editing = null)
        }
    }
}

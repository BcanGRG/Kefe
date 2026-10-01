package com.kefe.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.sync.SyncCoordinator
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.ui.format.trUpper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    // Yalniz ustteki notun metni icin: adlar hesapla mi esitleniyor, bu
    // cihazda mi kaliyor.
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    val state: StateFlow<ProfilesUiState>
        field = MutableStateFlow(ProfilesUiState())

    init {
        observe()
    }

    fun onIntent(intent: ProfilesIntent) {
        when (intent) {
            is ProfilesIntent.SetThisDevice -> viewModelScope.launch {
                preferences.put(PreferenceKeys.ActiveMemberId, intent.memberId)
            }

            is ProfilesIntent.OpenRename -> {
                val row = state.value.profiles.firstOrNull { it.id == intent.memberId } ?: return
                state.value = state.value.copy(
                    editing = ProfileNameEdit(id = row.id, name = row.name),
                )
            }

            is ProfilesIntent.ChangeName -> state.value = state.value.copy(
                editing = state.value.editing?.copy(name = intent.value),
            )

            ProfilesIntent.SaveName -> saveName()

            ProfilesIntent.DismissRename -> state.value = state.value.copy(editing = null)
        }
    }

    private fun observe() {
        viewModelScope.launch {
            syncCoordinator.mode().collect { mode ->
                state.value = state.value.copy(cloudMode = mode)
            }
        }
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
                state.value = state.value.copy(profiles = rows)
            }
        }
    }

    private fun saveName() {
        val edit = state.value.editing ?: return
        val name = edit.name.trim()
        if (name.isBlank()) {
            state.value = state.value.copy(editing = null)
            return
        }
        viewModelScope.launch {
            portfolioRepository.renameMember(
                memberId = edit.id,
                name = name,
                initials = name.firstOrNull()?.toString()?.trUpper() ?: "?",
            )
            state.value = state.value.copy(editing = null)
        }
    }
}

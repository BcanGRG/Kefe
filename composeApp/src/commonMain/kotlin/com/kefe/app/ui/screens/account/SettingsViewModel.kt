package com.kefe.app.ui.screens.account

import androidx.lifecycle.viewModelScope
import com.kefe.app.data.backup.CsvMimeType
import com.kefe.app.data.backup.FileTransfer
import com.kefe.app.data.backup.JsonMimeType
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.backup.decodeBackup
import com.kefe.app.domain.backup.encode
import com.kefe.app.domain.backup.transactionsCsv
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.ui.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Ayarlar.
 *
 * Tercihler DISKE yazilir: once yalniz bellekteydi ve kullanici temayi Acik yapip
 * uygulamayi kapatinca secim kayboluyordu. Yazma ile okuma ayni akista bulusur -
 * ekran her zaman diskteki degeri gosterir, iyimser bir kopyayi degil.
 */
class SettingsViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val preferences: PreferencesRepository,
    private val files: FileTransfer,
    private val clock: KefeClock,
    private val authRepository: AuthRepository,
) : MviViewModel<SettingsUiState, SettingsIntent, SettingsEffect>(
    SettingsUiState(),
) {

    init {
        observe()
        observeAccount()
    }

    /**
     * Hesap satirindaki e-posta OTURUMDAN gelir - once ekranda sabit bir ornek
     * adres yaziliydi ve kullanici baskasinin adresini kendi hesabi saniyordu.
     */
    private fun observeAccount() {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { auth ->
                setState {
                    copy(
                        email = (auth as? AuthState.SignedIn)?.session?.email.orEmpty(),
                        signedIn = auth is AuthState.SignedIn,
                    )
                }
            }
        }
    }

    /**
     * Oturumu kapatir. YEREL VERI SILINMEZ - portfoy cihazda kalir, yalniz
     * hesap baglantisi kesilir.
     */
    private fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            // Aksi halde "bir kez girildiyse giris ekrani atlanir" kurali
            // kullaniciyi ayni karede iceri geri alirdi.
            portfolioRepository.clearOnboarded()
            emitEffect(SettingsEffect.SignedOut)
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTheme -> put(PreferenceKeys.ThemeMode, intent.mode.name)
            is SettingsIntent.SetShowCents -> put(PreferenceKeys.ShowCents, intent.value)
            is SettingsIntent.SetHideBalanceOnStart ->
                put(PreferenceKeys.HideBalanceOnStart, intent.value)
            is SettingsIntent.SetBiometricLock -> put(PreferenceKeys.BiometricLock, intent.value)
            is SettingsIntent.SetNotifyPartnerEntry ->
                put(PreferenceKeys.NotifyPartnerEntry, intent.value)
            is SettingsIntent.SetNotifyMonthlyReminder ->
                put(PreferenceKeys.NotifyMonthlyReminder, intent.value)
            is SettingsIntent.SetNotifyMilestone -> put(PreferenceKeys.NotifyMilestone, intent.value)

            // Silme ONAY ISTER. Dogrudan silen bir satir, yanlislikla dokunulunca
            // geri donusu olmayan bir kayip demekti.
            SettingsIntent.DeleteAllData -> setState { copy(confirmDelete = true) }
            SettingsIntent.DismissDeleteConfirm -> setState { copy(confirmDelete = false) }
            SettingsIntent.ConfirmDeleteAllData -> deleteAll()

            // Henuz karsiligi olmayanlar. Sessizce yutmak yerine kullaniciya
            // soylenir - dokununca hicbir sey olmamasi hata gibi gorunuyordu.
            SettingsIntent.Backup -> exportBackup()
            SettingsIntent.ExportCsv -> exportCsv()
            SettingsIntent.Restore -> setState { copy(confirmRestore = true) }
            SettingsIntent.DismissRestoreConfirm -> setState { copy(confirmRestore = false) }
            SettingsIntent.ConfirmRestore -> restore()

            SettingsIntent.SignOut -> signOut()

            SettingsIntent.OpenCurrency,
            SettingsIntent.OpenPriceRefresh,
            SettingsIntent.OpenPriceSource,
            SettingsIntent.OpenPrivacy,
            SettingsIntent.OpenTerms,
            -> emitEffect(SettingsEffect.NotReady)
        }
    }

    private fun put(key: String, value: Boolean) = put(key, value.toString())

    private fun put(key: String, value: String) {
        viewModelScope.launch { preferences.put(key, value) }
    }

    // --- Yedekleme ----------------------------------------------------------

    /**
     * Yedek dosyasi uretir ve kullanicinin sectigi yere gonderir.
     *
     * Dosya adinda TARIH var: kullanici birden fazla yedek tutabilmeli ve
     * hangisinin hangi gune ait oldugunu ad satirindan gorebilmeli.
     */
    private fun exportBackup() {
        setState { copy(working = true) }
        viewModelScope.launch {
            runCatching {
                val today = clock.today()
                val file = portfolioRepository.exportBackup(takenOn = today.stamp())
                files.share(
                    fileName = "kefe-yedek-${today.stamp()}.json",
                    mimeType = JsonMimeType,
                    content = file.encode(),
                )
            }
                .onSuccess { emitEffect(SettingsEffect.BackupReady) }
                .onFailure { emitEffect(SettingsEffect.BackupFailed(it.reason())) }
            setState { copy(working = false) }
        }
    }

    /** CSV yedek DEGILDIR: geri yuklenemez, bakmak icindir. */
    private fun exportCsv() {
        setState { copy(working = true) }
        viewModelScope.launch {
            runCatching {
                val today = clock.today()
                val backup = portfolioRepository.exportBackup(takenOn = today.stamp())
                files.share(
                    fileName = "kefe-islemler-${today.stamp()}.csv",
                    mimeType = CsvMimeType,
                    content = backup.transactionsCsv(),
                )
            }
                .onSuccess { emitEffect(SettingsEffect.BackupReady) }
                .onFailure { emitEffect(SettingsEffect.BackupFailed(it.reason())) }
            setState { copy(working = false) }
        }
    }

    /**
     * Yedegi geri yukler - mevcut verinin YERINE.
     *
     * Onay istenir cunku geri donusu yok: yanlis dosya secildiginde kullanici
     * bugunku portfoyunu kaybeder.
     */
    private fun restore() {
        setState { copy(confirmRestore = false, working = true) }
        viewModelScope.launch {
            runCatching {
                val text = files.pickText()
                if (text != null) portfolioRepository.restoreBackup(decodeBackup(text))
                text != null
            }
                .onSuccess { picked -> if (picked) emitEffect(SettingsEffect.Restored) }
                .onFailure { emitEffect(SettingsEffect.BackupFailed(it.reason())) }
            setState { copy(working = false) }
        }
    }

    private fun deleteAll() {
        setState { copy(confirmDelete = false, deleting = true) }
        viewModelScope.launch {
            runCatching { portfolioRepository.deleteAllData() }
                .onSuccess { emitEffect(SettingsEffect.AllDataDeleted) }
                .onFailure { emitEffect(SettingsEffect.DeleteFailed(it.message ?: "Silinemedi.")) }
            setState { copy(deleting = false) }
        }
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePortfolio(),
                portfolioRepository.observeMembers(),
                preferences.observeAll(),
            ) { portfolio, members, prefs ->
                current.copy(
                    portfolioName = portfolio.name,
                    members = members.mapIndexed { index, member ->
                        SettingsMember(member.name, member.initials, index)
                    },
                    themeMode = prefs.themeMode(),
                    showCents = prefs.flag(PreferenceKeys.ShowCents, default = false),
                    hideBalanceOnStart = prefs.flag(PreferenceKeys.HideBalanceOnStart, true),
                    biometricLock = prefs.flag(PreferenceKeys.BiometricLock, true),
                    prefsLoaded = true,
                    notifyPartnerEntry = prefs.flag(PreferenceKeys.NotifyPartnerEntry, true),
                    notifyMonthlyReminder = prefs.flag(PreferenceKeys.NotifyMonthlyReminder, true),
                    notifyMilestone = prefs.flag(PreferenceKeys.NotifyMilestone, false),
                )
            }.collect { next -> setState { next } }
        }
    }
}

/** Taninmayan deger varsayilana duser - eski bir kayit uygulamayi dusurmemeli. */
private fun Map<String, String>.themeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == this[PreferenceKeys.ThemeMode] } ?: ThemeMode.System

private fun Map<String, String>.flag(key: String, default: Boolean): Boolean =
    this[key]?.toBooleanStrictOrNull() ?: default

/** "2026-07-28" - dosya adinda ve yedegin icinde ayni bicim. */
private fun KefeDate.stamp(): String {
    val mm = if (month < 10) "0$month" else "$month"
    val dd = if (day < 10) "0$day" else "$day"
    return "$year-$mm-$dd"
}

/**
 * Hatanin kullaniciya gosterilecek sebebi.
 *
 * Dosya islemleri platforma iniyor ve oradan gelen mesajlar ("EACCES", "No such
 * file") kullaniciya bir sey soylemez; kendi attigimiz mesajlar soyler.
 */
private fun Throwable.reason(): String =
    message?.takeIf { it.isNotBlank() } ?: "Bilinmeyen hata"

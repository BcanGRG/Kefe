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
import com.kefe.app.data.sync.SyncCoordinator
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.data.remote.SupabaseConfig
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.domain.repository.lockEnabled
import com.kefe.app.security.BiometricGate
import com.kefe.app.ui.format.relativeSince
import com.kefe.app.ui.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    private val biometric: BiometricGate,
    // Hesap bolumu MODU okur (bkz. CloudMode); cikis ve "Şimdi eşitle" de
    // baglantinin sahibi olan kordinatorden gecer.
    private val syncCoordinator: SyncCoordinator,
) : MviViewModel<SettingsUiState, SettingsIntent, SettingsEffect>(
    SettingsUiState(),
) {

    // Acma istemi suruyor. Anahtara art arda dokunmak ikinci bir sistem istemi
    // acmamali; ilki bitene kadar yenisi yok sayilir.
    private var lockJob: Job? = null

    init {
        observe()
        observeAccount()
    }

    /**
     * Hesap bolumu MODDAN cizilir; e-posta da modun icinde, oturumdan gelir -
     * once ekranda sabit bir ornek adres yaziliydi ve kullanici baskasinin
     * adresini kendi hesabi saniyordu.
     *
     * "Son eşitleme" DAKIKADA BIR yeniden yazilir. NEYDI: etiket yalniz ayar
     * emisyonunda hesaplaniyordu ve "her basarili tur anahtari yeniden yazar,
     * kendiliginden tazelenir" varsayiliyordu. Esitleme olay-gudumlu: hesaba
     * ulasilamayan ya da bosta duran cihazda hicbir sey yazmaz, satir saatlerce
     * "az önce" kaliyordu - hem de "Hesaba ulaşılamıyor"un hemen altinda.
     * Ozet'teki ayni etiket ayni sebeple zaten dakikada bir tazeleniyor.
     */
    private fun observeAccount() {
        setState { copy(cloudConfigured = authRepository.isCloudConfigured) }
        viewModelScope.launch {
            combine(
                syncCoordinator.mode(),
                preferences.observeAll()
                    .map { it[PreferenceKeys.LastSyncedAt]?.toLongOrNull() }
                    .distinctUntilChanged(),
                minuteTicker(),
            ) { mode, syncedAt, _ ->
                mode to (syncedAt?.let { relativeSince(it, clock.nowEpochMillis()) } ?: "Henüz yok")
            }.distinctUntilChanged().collect { (mode, label) ->
                setState { copy(cloudMode = mode, lastSyncedLabel = label) }
            }
        }
    }

    private fun minuteTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(SyncedLabelTickMillis)
        }
    }

    /**
     * Hesaptan cikar ya da yarim/dusmus baglantiyi birakir. YEREL VERI
     * SILINMEZ - portfoy cihazda kalir, yalniz hesapla bag ve oturum gider.
     *
     * Baglanti da silinir: yalniz oturum kapansaydi mod "Oturum kapandı" olur ve
     * kullaniciya kendi istedigi cikis bir ariza gibi gosterilirdi.
     */
    private fun dropLink() {
        viewModelScope.launch {
            syncCoordinator.dropLink()
            // Onboarded BIRAKILIR, yerel veri durur: hesap zorunlu bir kapi
            // degil, Ayarlar'dan baglanip birakilan istege bagli bir esitleme.
            // Kullanici uygulamada kalir, hesap bolumu "Yalnız bu cihazda"ya doner.
            emitEffect(SettingsEffect.SignedOut)
        }
    }

    /**
     * Cikis onayini acar. Gitmemis degisiklik o an sorulur: onay metni "çıkarsanız
     * yalnız bu cihazda kalır" diyecekse bunu bilmeli.
     */
    private fun askSignOut() {
        viewModelScope.launch {
            val unsent = runCatching { syncCoordinator.hasUnsentChanges() }.getOrDefault(false)
            setState { copy(confirmSignOut = true, unsentChanges = unsent) }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTheme -> put(PreferenceKeys.ThemeMode, intent.mode.name)
            is SettingsIntent.SetShowCents -> put(PreferenceKeys.ShowCents, intent.value)
            is SettingsIntent.SetHideBalanceOnStart ->
                put(PreferenceKeys.HideBalanceOnStart, intent.value)
            is SettingsIntent.SetBiometricLock ->
                if (intent.value) enableLock() else put(PreferenceKeys.BiometricLock, false)

            // Silme ONAY ISTER. Dogrudan silen bir satir, yanlislikla dokunulunca
            // geri donusu olmayan bir kayip demekti.
            SettingsIntent.DeleteAllData -> setState { copy(confirmDelete = true) }
            SettingsIntent.DismissDeleteConfirm -> setState { copy(confirmDelete = false) }
            SettingsIntent.ConfirmDeleteAllData -> deleteAll()

            SettingsIntent.Backup -> exportBackup()
            SettingsIntent.ExportCsv -> exportCsv()
            // Bagli cihazda geri yukleme KAPALI (bkz. restoreLocked): satir
            // soluk durur, dokununca nedenini ve cikis yolunu soyler.
            SettingsIntent.Restore ->
                if (restoreLocked(current.cloudMode)) {
                    emitEffect(SettingsEffect.Notice(restoreLockedMessage(current.cloudMode)))
                } else {
                    setState { copy(confirmRestore = true) }
                }
            SettingsIntent.DismissRestoreConfirm -> setState { copy(confirmRestore = false) }
            SettingsIntent.ConfirmRestore -> restore()

            // Acik cikis ONAY ISTER: kayitlarin akibeti ve gitmemis degisiklik
            // cikmadan once soylenir. "Vazgeç" (yarim baglanti) ve "Hesapsız
            // devam et" (dusen oturum) sormaz - orada esitlenen bir sey yok.
            SettingsIntent.SignOut -> askSignOut()
            SettingsIntent.DismissSignOutConfirm -> setState { copy(confirmSignOut = false) }
            SettingsIntent.ConfirmSignOut -> {
                setState { copy(confirmSignOut = false) }
                dropLink()
            }
            SettingsIntent.DropLink -> dropLink()
            SettingsIntent.SyncNow -> syncCoordinator.syncNow()
        }
    }

    private fun put(key: String, value: Boolean) = put(key, value.toString())

    /**
     * Acilis kilidini ACAR - ancak kimlik bir kez dogrulanirsa.
     *
     * Kararin kendisi saf fonksiyonlarda (lockEnableStep, lockEnableOutcome):
     * BiometricGate testte taklit edilemiyor. Kapatmak kimlik sormaz; kilidi
     * kapatmak isteyen zaten uygulamanin icinde, kilidi bir kez acmis biri.
     */
    private fun enableLock() {
        if (lockJob?.isActive == true) return
        lockJob = viewModelScope.launch {
            when (val step = lockEnableStep(biometric.availability())) {
                LockEnableStep.Unavailable -> Unit
                is LockEnableStep.Refuse -> emitEffect(SettingsEffect.Notice(step.message))
                LockEnableStep.Authenticate -> {
                    val outcome = lockEnableOutcome(
                        biometric.authenticate(LockEnablePromptTitle, LockEnablePromptSubtitle),
                    )
                    if (outcome.enable) preferences.put(PreferenceKeys.BiometricLock, true.toString())
                    outcome.notice?.let { emitEffect(SettingsEffect.Notice(it)) }
                }
            }
        }
    }

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
                // Paylasim penceresi acildi; dosyanin nereye gittigini bilemeyiz
                // ama yedegi URETTIK. Satirin sagindaki tarih bunu gosterir.
                preferences.put(PreferenceKeys.LastBackupAt, today.stamp())
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
        setState { copy(confirmRestore = false) }
        // Onay acikken baglanti kurulmus olabilir: kapi burada da tutulur.
        if (restoreLocked(current.cloudMode)) {
            emitEffect(SettingsEffect.Notice(restoreLockedMessage(current.cloudMode)))
            return
        }
        setState { copy(working = true) }
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

    /**
     * "Tüm verileri sil" / "Bu cihazı sıfırla".
     *
     * Hesap isin icindeyse (bkz. resetsAccount) ONCE hesaptan cikilir (yalniz bu
     * cihazin oturumu), SONRA silinir. NEYDI: yalniz silinirdi; oturum ve
     * baglanti durdugu icin silmenin tetikledigi push -> pull ~1,5 sn icinde
     * hesabin tum kayitlarini geri indiriyordu. Ters sira da olmaz: silme ile
     * cikis arasinda gelen bir pull ayni isi yapardi. Silme de pull'larla ayni
     * kilitte calisir (bkz. SyncCoordinator.resetDevice): cikis aninda zaten
     * suren bir pull, silmeden sonra bitip hesabi geri yazmasin.
     */
    private fun deleteAll() {
        val resets = resetsAccount(current.cloudMode)
        setState { copy(confirmDelete = false, deleting = true) }
        viewModelScope.launch {
            runCatching {
                if (resets) {
                    syncCoordinator.resetDevice { portfolioRepository.deleteAllData() }
                } else {
                    portfolioRepository.deleteAllData()
                }
            }
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
                // Emisyon aninda bakmak yeter: satiri gizleyen tek sey
                // donanimin/platformun olmamasi ve o surec icinde degismez.
                // Parmak izi eklenip silinmesi (NotEnrolled <-> Available)
                // satiri gizlemez; o fark acma aninda yeniden sorulur.
                val availability = biometric.availability()
                current.copy(
                    portfolioName = portfolio.name,
                    members = members.mapIndexed { index, member ->
                        SettingsMember(member.name, member.initials, index)
                    },
                    themeMode = prefs.themeMode(),
                    showCents = prefs.flag(PreferenceKeys.ShowCents, default = false),
                    hideBalanceOnStart = prefs.flag(PreferenceKeys.HideBalanceOnStart, true),
                    // Anahtar kilidin YAPACAGINI gosterir (bkz. lockSwitchOn):
                    // kimligi tanimsiz eski kurulumda "acik" ama sessiz bir
                    // anahtar yerine kapali bir anahtar.
                    biometricLock = lockSwitchOn(prefs.lockEnabled(), availability),
                    lockAvailable = lockRowVisible(availability),
                    prefsLoaded = true,
                    activeMemberId = prefs[PreferenceKeys.ActiveMemberId],
                    // Bu cihazin profili OLMAYAN, adlandirilmis profil: onay
                    // metinleri "Merve'nin telefonu" diyebilsin. Profil secilmediyse
                    // ya da es adsizsa null ("eşinizin telefonu").
                    partnerName = prefs[PreferenceKeys.ActiveMemberId]?.let { active ->
                        members.firstOrNull { it.id != active && it.isNamed }?.name
                    },
                    // Yedek satirinin sagi: son yedek tarihi. Bos ise "Henüz
                    // alınmadı" - once bu etiket hicbir zaman yazilmiyor ve yedek
                    // alindiktan sonra bile bos kaliyordu.
                    lastBackupLabel = prefs[PreferenceKeys.LastBackupAt]?.toBackupLabel()
                        ?: "Henüz alınmadı",
                    // "Son eşitleme" burada DEGIL, observeAccount'ta: dakikada
                    // bir tazelenmesi gerekiyor (bkz. orasi).
                    appVersion = SupabaseConfig.AppVersion,
                )
            }.collect { next -> setState { next } }
        }
    }
}

/**
 * "Son eşitleme" etiketinin tazelenme araligi. Etiketin en ince duragi dakika
 * ("5 dk önce"); daha sik uyanmak ekranda bir sey degistirmez.
 */
private const val SyncedLabelTickMillis = 60_000L

/** "2026-07-28" -> "28 Temmuz 2026". Bicimsizse bos. */
private fun String.toBackupLabel(): String {
    val parts = split("-")
    val year = parts.getOrNull(0)
    val month = parts.getOrNull(1)?.toIntOrNull()
    val day = parts.getOrNull(2)?.toIntOrNull()
    if (year == null || month == null || day == null || month !in 1..12) return ""
    return "$day ${BackupMonthNames[month - 1]} $year"
}

private val BackupMonthNames = listOf(
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
)

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

package com.kefe.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.screens.account.SignInPurpose

sealed interface KefeKey : NavKey

// --- Ust duzey sekmeler ----------------------------------------------------

data object SummaryKey : KefeKey

data object AssetsKey : KefeKey

data object GoalsKey : KefeKey

/** Aylik plan: yatirim plani, gelir-gider, butce ve seri. Alt barda Ayarlar'in yerini aldi. */
data object PlanKey : KefeKey

// --- Ikincil ekranlar ------------------------------------------------------

/**
 * Ayarlar. Masaustunde yan menude UST DUZEY satirdir (kok olur); telefonda ve
 * tablette ITILEN ikincil ekrandir (geri oklu) - Ozet'teki ya da raydaki
 * disliyle acilir. NEDEN: alt bar dort sekmeyle dolu ve Plan onun yerini aldi.
 */
data object SettingsKey : KefeKey

data class AssetDetailKey(val positionId: String) : KefeKey

data class GoalDetailKey(val goalId: String) : KefeKey

/**
 * Harcamalar sayfasi: bir ayin harcamalari, tumu ya da tek kalem.
 * [filter] ExpenseFilter.key() bicimi - null = tumu.
 */
data class PlanExpensesKey(val year: Int, val month: Int, val filter: String?) : KefeKey

data object MarketKey : KefeKey

data object ActivityKey : KefeKey

data object ProfilesKey : KefeKey

// --- Hesap akisi -----------------------------------------------------------
//
// Eskiden tek bir LoginKey uc isi birden goruyordu: acilis kilidi (kok iken),
// ilk acilis (kok iken) ve Ayarlar'dan itilen giris. Ayni ViewModel uc duruma
// hizmet ettigi icin kilit kalintisi (stage=Locked, unlocked=true) itilen
// girise sizuyor, kabuk bunu "asRoot" ve vmState.copy yamalariyla bastiriyordu.
// Artik her is kendi anahtarinda; hangisinin kok olabilecegi de belli.

/**
 * Ilk acilis: "Nasil kullanmak istersiniz?" - yalniz KOK. Hesapsiz ve hesapli
 * kullanim esit iki kart; ikisi de secili gelmez.
 */
data object WelcomeKey : KefeKey

/** Acilis kilidi - yalniz KOK ve yalniz acilista (bkz. isLaunchLocked). */
data object LockKey : KefeKey

/**
 * E-posta koduyla giris. HER ZAMAN ITILIR (ust cubuk + geri oku): geri
 * gidilecek bir yer her zaman var - hosgeldin, profil adimi ya da Ayarlar.
 * [purpose] basligi ve notu belirler (bkz. signInCopy).
 */
data class SignInKey(val purpose: SignInPurpose) : KefeKey

data object OnboardingKey : KefeKey

/** "Bu telefon kimin?" - iki profilin adi ve bu cihazin hangisi oldugu. */
data object ProfileSetupKey : KefeKey

/** Bilesen katalogu - Ayarlar'in altindaki gizli girisle acilir. */
data object GalleryKey : KefeKey

/**
 * Alt navigasyondaki sekmeler. Orta slot (Islem Ekle) sekme DEGIL, one cikan
 * aksiyondur - bu yuzden listede yer almaz, ayri ele alinir.
 * Alt bar ve ray "Ekle"yi ikinci sekmeden (indeks 1) SONRA cizer.
 */
data class TopLevelDestination(
    val key: KefeKey,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations: List<TopLevelDestination> = listOf(
    TopLevelDestination(SummaryKey, "Özet", KefeIcons.Balance),
    TopLevelDestination(AssetsKey, "Varlıklar", KefeIcons.Wallet),
    TopLevelDestination(GoalsKey, "Hedefler", KefeIcons.Target),
    TopLevelDestination(PlanKey, "Plan", KefeIcons.Calendar),
)

/**
 * Masaustu yan navigasyonu YEDI satirdir - telefon alt navigasyonundan farkli.
 * Piyasa ve Aktivite telefonda ikincil ekranken masaustunde ust duzeydedir;
 * 240dp'lik kolonda yer var ve tasarim bunlari orada gosteriyor.
 */
val desktopDestinations: List<TopLevelDestination> = listOf(
    TopLevelDestination(SummaryKey, "Özet", KefeIcons.Balance),
    TopLevelDestination(AssetsKey, "Varlıklar", KefeIcons.Wallet),
    TopLevelDestination(GoalsKey, "Hedefler", KefeIcons.Target),
    TopLevelDestination(PlanKey, "Plan", KefeIcons.Calendar),
    TopLevelDestination(MarketKey, "Piyasa", KefeIcons.Fund),
    TopLevelDestination(ActivityKey, "Aktivite", KefeIcons.Clock),
    TopLevelDestination(SettingsKey, "Ayarlar", KefeIcons.Settings),
)

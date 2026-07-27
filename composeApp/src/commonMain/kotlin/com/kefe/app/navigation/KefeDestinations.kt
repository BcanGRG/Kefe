package com.kefe.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.kefe.app.ui.icons.KefeIcons

sealed interface KefeKey : NavKey

// --- Ust duzey sekmeler ----------------------------------------------------

data object SummaryKey : KefeKey

data object AssetsKey : KefeKey

data object GoalsKey : KefeKey

data object SettingsKey : KefeKey

// --- Ikincil ekranlar ------------------------------------------------------

data class AssetDetailKey(val positionId: String) : KefeKey

data class GoalDetailKey(val goalId: String) : KefeKey

data object MarketKey : KefeKey

data object ActivityKey : KefeKey

data object ShareKey : KefeKey

// --- Hesap akisi -----------------------------------------------------------

data object LoginKey : KefeKey

data object OnboardingKey : KefeKey

/** Bilesen katalogu - Ayarlar'in altindaki gizli girisle acilir. */
data object GalleryKey : KefeKey

/**
 * Alt navigasyondaki sekmeler. Orta slot (Islem Ekle) sekme DEGIL, one cikan
 * aksiyondur - bu yuzden listede yer almaz, ayri ele alinir.
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
    TopLevelDestination(SettingsKey, "Ayarlar", KefeIcons.ListMenu),
)

/**
 * Masaustu yan navigasyonu ALTI satirdir - telefon alt navigasyonundan farkli.
 * Piyasa ve Aktivite telefonda ikincil ekranken masaustunde ust duzeydedir;
 * 240dp'lik kolonda yer var ve tasarim bunlari orada gosteriyor.
 */
val desktopDestinations: List<TopLevelDestination> = listOf(
    TopLevelDestination(SummaryKey, "Özet", KefeIcons.Balance),
    TopLevelDestination(AssetsKey, "Varlıklar", KefeIcons.Wallet),
    TopLevelDestination(GoalsKey, "Hedefler", KefeIcons.Target),
    TopLevelDestination(MarketKey, "Piyasa", KefeIcons.Fund),
    TopLevelDestination(ActivityKey, "Aktivite", KefeIcons.Clock),
    TopLevelDestination(SettingsKey, "Ayarlar", KefeIcons.ListMenu),
)

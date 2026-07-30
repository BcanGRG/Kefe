package com.kefe.app.ui.screens.account

/**
 * Profiller ekrani - Ayarlar'dan acilir, ilk kurulumdan (ProfileSetup) FARKLI:
 * burada iki profil zaten kurulu, yalniz adlar duzenlenir ve bu cihazin hangi
 * profil oldugu degistirilir.
 *
 * Cok kullanicili "Paylasim" ekraninin yerini aldi: davet kodu, QR, izin ve uye
 * cikarma iki esit profilde karsiliksizdi.
 */
data class ProfileRow(
    val id: String,
    val name: String,
    val initials: String,
    val index: Int,
    /** Bu cihazin secili profili mi. */
    val isThisDevice: Boolean,
)

data class ProfilesUiState(
    val profiles: List<ProfileRow> = emptyList(),
    /** Adi duzenlenen profil; null ise sheet kapali. */
    val editing: ProfileNameEdit? = null,
)

data class ProfileNameEdit(
    val id: String,
    val name: String,
)

sealed interface ProfilesIntent {
    /** Bu cihazi verilen profile baglar. */
    data class SetThisDevice(val memberId: String) : ProfilesIntent

    data class OpenRename(val memberId: String) : ProfilesIntent
    data class ChangeName(val value: String) : ProfilesIntent
    data object SaveName : ProfilesIntent
    data object DismissRename : ProfilesIntent
}

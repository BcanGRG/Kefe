package com.kefe.app.ui.screens.account

import com.kefe.app.data.sync.CloudMode

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
    /** Hesap modu: ustteki not adlarin nerede yasadigini buna gore soyler. */
    val cloudMode: CloudMode? = null,
) {
    /** Listenin ustundeki aciklama (bkz. [profilesNote]). */
    val note: String
        get() = profilesNote(cloudMode)
}

/**
 * Profiller ekraninin notu. Adlar hesapla esitleniyorsa bunu, hesapsizsa adlarin
 * bu cihazda kaldigini soyler. NEYDI: tek metin iki durumda da aynisini
 * diyordu; hesapsiz kullanici adlarin esine gittigini sanabiliyordu.
 *
 * Yarim baglanti ve dusen oturumda adlar su an eşitlenmiyor - "iki telefona
 * eşitlenir" demek yanlis olurdu; notr metin.
 */
fun profilesNote(mode: CloudMode?): String {
    val head = when (mode) {
        CloudMode.Local ->
            "Profiller yalnız bu cihazda. Bu cihazdan eklenen kayıtlar işaretli profile yazılır."
        is CloudMode.Cloud ->
            "Adlar hesapla iki telefona eşitlenir. 'Bu telefon' seçimi yalnız bu cihaza aittir."
        is CloudMode.LinkPending, is CloudMode.SessionLost, null ->
            "Bu cihazdan eklenen kayıtlar işaretli profile yazılır."
    }
    return "$head Adı düzenlemek için dokunun."
}

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

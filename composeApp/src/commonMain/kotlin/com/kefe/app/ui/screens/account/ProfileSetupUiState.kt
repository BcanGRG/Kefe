package com.kefe.app.ui.screens.account

/**
 * "Bu telefon kimin?" adiminin durumu.
 *
 * Iki profil bootstrap'ta zaten kurulu; bu ekran YALNIZ adlari duzeltir ve bu
 * cihazin hangisi oldugunu secer. Ikinci telefonda senkron gelince adlar dolu
 * gelecegi icin kullanici yalniz "hangisi sensin" sorusuyla karsilasir - ama o
 * mantik kabukta, burada iki alan da her zaman gosterilir.
 */
data class ProfileSetupUiState(
    val ownerName: String = "",
    val partnerName: String = "",
    /** Bu cihazin profili: true ise sahip (ilk alan), false ise es. */
    val thisDeviceIsOwner: Boolean = true,
    val saving: Boolean = false,
    /** Kaydedildi - kabuk uygulamaya gecirir. */
    val done: Boolean = false,
) {
    /** Iki ad da bos degilse kaydedilebilir. */
    val canSave: Boolean
        get() = !saving && ownerName.isNotBlank() && partnerName.isNotBlank()
}

sealed interface ProfileSetupIntent {
    data class ChangeOwnerName(val value: String) : ProfileSetupIntent
    data class ChangePartnerName(val value: String) : ProfileSetupIntent
    data class SelectThisDevice(val isOwner: Boolean) : ProfileSetupIntent
    data object Save : ProfileSetupIntent
}

package com.kefe.app.ui.screens.account

/**
 * "Profiller / Bu telefon kimin?" adiminin durumu.
 *
 * IKI AYRI SORU TEK EKRANDA:
 *   - Hesapta profil YOKSA (ilk telefon, ya da hesapsiz baslangic): iki ad
 *     yazilir, bu cihazin hangisi oldugu secilir - [editingNames] true.
 *   - Hesapta profil VARSA (ikinci telefon, ya da yeniden kurulum): adlar
 *     hesaptan gelir, yalniz "hangisi sensin" sorulur - [editingNames] false.
 *     Adlar YENIDEN YAZILMAZ: yazilsaydi bu cihazin damgasi hesaptakinden yeni
 *     olur ve LWW ile iki telefondaki gercek adlarin uzerine yazardi.
 *
 * NEYDI. Ekran her zaman ilk soruyu soruyordu. Hesabi olan biri yeni telefonda
 * "İki profil oluşturun" goruyor, bos alanlara ad yaziyordu; o sirada senkron
 * hic beklenmedigi icin hesaptaki adlar ekrana hic gelmiyordu.
 */
enum class ProfileSetupPhase {
    /** Oturum durumu okunuyor - bir sey cizilmez, parlama olmaz. */
    Checking,

    /** Hesaptaki kayitlar indiriliyor. */
    Syncing,

    /** Hesaba ulasilamadi - tekrar dene ya da baglanmadan devam et. */
    Failed,
    Ready,
}

data class ProfileSetupUiState(
    val phase: ProfileSetupPhase = ProfileSetupPhase.Checking,
    /** Girisli mi - "Hesabım var, giriş yap" yalniz girissizken gosterilir. */
    val signedIn: Boolean = false,
    /** Adlar yazilabilir mi (profil olusturma) yoksa yalniz secim mi. */
    val editingNames: Boolean = true,
    /** Hesapta adlandirilmis profil bulundu - "Adları düzenle" geri donusu icin. */
    val accountHasProfiles: Boolean = false,
    val failureDetail: String? = null,
    val ownerName: String = "",
    val partnerName: String = "",
    /** Yuklenen adlar - kaydederken YALNIZ degisen ad yeniden yazilir. */
    val loadedOwnerName: String = "",
    val loadedPartnerName: String = "",
    /**
     * Bu cihazin profili: true sahip, false es, null henuz secilmedi.
     *
     * Hesaptan gelen profillerde bilerek null baslar: iki telefon ayni profili
     * secerse bunu hicbir yer yakalayamaz (secim cihaza ait, senkronlanmaz);
     * hazir isaretli bir satir, "Devam"a basip gecmeyi fazla kolay kilardi.
     */
    val thisDeviceIsOwner: Boolean? = true,
    val saving: Boolean = false,
    /** Kaydedildi - kabuk uygulamaya gecirir. */
    val done: Boolean = false,
) {
    val canSave: Boolean
        get() = !saving &&
            phase == ProfileSetupPhase.Ready &&
            thisDeviceIsOwner != null &&
            (!editingNames || (ownerName.isNotBlank() && partnerName.isNotBlank()))
}

sealed interface ProfileSetupIntent {
    /** Ekran her gorundugunde: oturuma bakar, girisliyse hesabi indirir. */
    data object Load : ProfileSetupIntent
    data object Retry : ProfileSetupIntent

    /** Hesaba ulasilamadi; yereldeki adlarla yalniz secim yapilir. */
    data object ContinueOffline : ProfileSetupIntent
    data object EditNames : ProfileSetupIntent
    data class ChangeOwnerName(val value: String) : ProfileSetupIntent
    data class ChangePartnerName(val value: String) : ProfileSetupIntent
    data class SelectThisDevice(val isOwner: Boolean) : ProfileSetupIntent
    data object Save : ProfileSetupIntent

    /** Kabuk ekrandan cikarken - VM surec boyunca yasiyor, eski "done" kalmasin. */
    data object Reset : ProfileSetupIntent
}

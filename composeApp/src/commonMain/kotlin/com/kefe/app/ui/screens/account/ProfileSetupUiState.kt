package com.kefe.app.ui.screens.account

/**
 * "Profiller / Bu telefon kimin?" adiminin durumu.
 *
 * IKI AYRI SORU TEK EKRANDA:
 *   - Profiller adlandirilmamissa (ilk telefon, ya da hesapsiz baslangic): iki
 *     ad yazilir, bu cihazin hangisi oldugu secilir - [editingNames] true.
 *   - Adlandirilmis profil varsa (ikinci telefon, yeniden kurulum ya da bu
 *     cihazda daha once yazilmis adlar): yalniz "hangisi sensin" sorulur -
 *     [editingNames] false. Adlar YENIDEN YAZILMAZ: yazilsaydi bu cihazin
 *     damgasi hesaptakinden yeni olur ve LWW ile iki telefondaki gercek adlarin
 *     uzerine yazardi.
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

    /** Hesaba ulasilamadi - tekrar dene ya da simdilik hesapsiz devam et. */
    Failed,
    Ready,
}

data class ProfileSetupUiState(
    val phase: ProfileSetupPhase = ProfileSetupPhase.Checking,
    /** Girisli mi - "Hesaba bağla" yalniz girissizken gosterilir. */
    val signedIn: Boolean = false,
    /**
     * Girilen hesabin e-postasi (girisliyken). Ekranda "… ile giriş yaptınız ·
     * Farklı e-postayla gir" olarak durur. NEYDI: ilk acilista hesapla giren
     * kullanici bu ekrana KOK olarak geliyordu; hangi e-postayla girdigini
     * gormuyor, yanlis yazdiysa geri donecek yeri de yoktu - tek cikis profilleri
     * olusturup (yanlis hesaba baglanip) sonra Ayarlar'dan cikmakti.
     */
    val accountEmail: String? = null,
    /**
     * Hesap bu yuklemede BASARIYLA indirildi. Girisli ama indirilemeden
     * gecildiyse ("Şimdilik hesapsız devam et") false: ekrandaki adlar hesabin
     * degil cihazin, "Hesabınızda" denemez ve adlar duzenlenmez.
     */
    val accountDownloaded: Boolean = false,
    /** Bulut bu surumde yapilandirilmis mi; degilse "Hesaba bağla" cizilmez. */
    val cloudConfigured: Boolean = false,
    /** Adlar yazilabilir mi (profil olusturma) yoksa yalniz secim mi. */
    val editingNames: Boolean = true,
    /**
     * Bu cihazda adlandirilmis profil var - hesaptan inmis ya da daha once
     * burada yazilmis. Secim modunun ve "Adları düzenle"nin sebebi.
     */
    val profilesNamed: Boolean = false,
    /**
     * Adlandirilmis profiller HESAPTAN geldi: hesap bu yuklemede indirildi VE
     * adlari o getirdi (bkz. ProfileSetupViewModel.load).
     *
     * NEYDI: once yalniz "yerelde adlandirilmis profil var" demekti; girissizken
     * de (orn. "Tüm verileri sil" sonrasi, adlar cihazda kalmisken) ekran
     * "Hesabınızda iki profil bulduk" diyor, ayni anda "giriş yap" baglantisini
     * gosteriyordu. Sonra "ve girisli" eklendi; ama hesap indirilemeden
     * gecilince ("Şimdilik hesapsız devam et") ya da hesap bos cikinca da
     * cihazda yazilmis adlar "Hesabınızda iki profil var" diye sunuluyordu.
     */
    val accountHasProfiles: Boolean = false,
    val failureDetail: String? = null,
    val ownerName: String = "",
    val partnerName: String = "",
    /**
     * Yuklenen adlar - kaydederken YALNIZ degisen ad yeniden yazilir. YALNIZ
     * adlandirilmis profillerden dolar; kurulumun "Ben"/"Eşim"i yuklenmis bir
     * ad sayilmaz (bkz. ProfileSetupViewModel.showMembers).
     */
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

    /**
     * Girisli ama hesap indirilemeden gecildi ("Şimdilik hesapsız devam et"):
     * ekrandaki adlar cihazin, hesabinkiler henuz gelmedi.
     */
    val offlinePick: Boolean
        get() = signedIn && !accountDownloaded

    /**
     * Secim modunda "Adları düzenle" gosterilir mi: yalniz adlandirilmis
     * profillerde ve hesap indirilemeden gecilmediyse. O durumda ("Şimdilik
     * hesapsız devam et") yazilan adlar, "Tamamla" hesabi indirdiginde hesabin
     * adlariyla degisir - duzenlemek bosa is.
     */
    val canEditNames: Boolean
        get() = !editingNames && profilesNamed && !offlinePick

    /** Girissizken, bulut varsa: "Eşinizle iki telefonda mı…? Hesaba bağla". */
    val showLinkFooter: Boolean
        get() = !signedIn && cloudConfigured

    /**
     * Girisliyken: hangi e-postayla girildigi ve "Farklı e-postayla gir".
     * Baglanti ancak "Devam"la yazildigi icin burada oturumu birakmak hicbir
     * kaydi etkilemez.
     */
    val showAccountFooter: Boolean
        get() = signedIn && !accountEmail.isNullOrBlank()
}

/**
 * Hazir ekranin govde metni. SAF: her durumun metni testte denenebilsin.
 *
 * HESAP SOZU YALNIZ GIRISLIYKEN. NEYDI: secim metni "Hesabınızda iki profil
 * bulduk" diyordu, oysa profiller cihazda adlandirilmis olmasi yetiyordu -
 * hic hesabi olmayan biri de bunu goruyordu.
 */
fun ProfileSetupUiState.readyBody(): String = when {
    editingNames && profilesNamed ->
        "Profil adlarını düzenleyin. Bu telefondan eklediğiniz her kayıt, seçtiğiniz profile yazılır."

    // Hesap var ama bos (ilk telefon): adlar baglantiyla hesaba gider.
    editingNames && signedIn ->
        "Kefe iki kişilik: siz ve eşiniz. İki profil oluşturun; bu cihazdan eklenen her kayıt " +
            "seçtiğiniz profile yazılır. Profiller hesabınıza da kaydedilir; eşiniz aynı " +
            "e-postayla girince görür."

    editingNames ->
        "Kefe iki kişilik: siz ve eşiniz. İki profil oluşturun; bu cihazdan eklenen her kayıt " +
            "seçtiğiniz profile yazılır. Hesap kullanmadığınız için profiller yalnız bu cihazda durur."

    accountHasProfiles ->
        "Hesabınızda iki profil var: ${ownerName.ifBlank { "1. profil" }} ve " +
            "${partnerName.ifBlank { "2. profil" }}. Bu cihaz hangisinin?"

    // Hesap indirilemeden gecildi: adlar CIHAZIN. NEYDI: "Hesabınızda iki
    // profil var: Volkan ve Ayşe" deniyordu - hesaptan hicbir sey gelmemisti ve
    // "Tamamla" hesabi indirince bu adlar hesabinkilerle degisecekti.
    offlinePick && profilesNamed ->
        "Bu cihazda iki profil var. Hangisi sizsiniz? " +
            "Bağlantı gelince Özet'teki \"Tamamla\" ile hesabınızdaki kayıtları indirin."

    // Hesap indirildi ama bos: cihazdaki adlar baglantiyla hesaba gider.
    signedIn && profilesNamed ->
        "Bu cihazda iki profil var. Hangisi sizsiniz? Profiller hesabınıza da kaydedilir; " +
            "eşiniz aynı e-postayla girince görür."

    profilesNamed -> "Bu cihazda iki profil var. Hangisi sizsiniz?"

    // Yalniz "Şimdilik hesapsız devam et"ten gelinir: hesap indirilemedi,
    // baglanti yazilmadi, mod "Bağlantı yarım". NEYDI: "kayitlar baglanti
    // gelince kendiliginden gelir" deniyordu - oysa esitleme yalniz BAGLI
    // cihazda calisiyor; kayitlar ancak Ozet'teki (ya da Ayarlar'daki)
    // "Tamamla" ile iner ve secim orada yeniden sorulur.
    else ->
        "Bu telefondan eklediğiniz her kayıt, seçtiğiniz profile yazılır. " +
            "Bağlantı gelince Özet'teki \"Tamamla\" ile hesabınızdaki kayıtları indirin."
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

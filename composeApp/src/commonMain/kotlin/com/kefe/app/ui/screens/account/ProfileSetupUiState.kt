package com.kefe.app.ui.screens.account

import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.sync.ConflictChoice

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

    /**
     * Cihazda da hesapta da kayit var ve ortak gecmis yok (bkz. classifyLink):
     * "Hesaptakileri kullan", "Birleştir" ya da "Vazgeç". Secilene kadar HICBIR
     * SEY yazilmaz.
     */
    Conflict,
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
    /**
     * [ProfileSetupPhase.Failed]'in sebebi: hesap okundu ama baglanti CIHAZA
     * yazilamadi (tek islem geri alindi). Ag hatasindan ayri bir metin gerekir
     * (bkz. failureMessage).
     */
    val commitFailed: Boolean = false,
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

    /**
     * Bir hesap BAGLANTISI bekliyor: hesap onizlendi ama henuz hicbir sey
     * yazilmadi; "Devam" onu tek islemde kurar (bkz. AccountLinker.commit).
     * Ekrandaki adlar hesabin (adlandirilmissa) - cihaza ancak o an iner.
     */
    val linking: Boolean = false,
    /** Cakismada cihazdaki ve hesaptaki kayit sayisi (bkz. conflictCopy). */
    val conflictLocal: Int = 0,
    val conflictServer: Int = 0,
    /** Cakismada secilen yol; secim ekraninda "ne olacak" notu buna gore. */
    val conflictChoice: ConflictChoice? = null,
    /**
     * Bu telefonun ONCEKI profili. Secim ondan farkliysa cihazda girilmis
     * kayitlar yeni profile aktarilir (bkz. remapNote).
     */
    val previousMemberId: String? = null,
    /** Bu cihazda girilip hesapta olmayan kayitlar, ekleyen profile gore. */
    val localOnlyByAuthor: Map<String, Int> = emptyMap(),
    /**
     * Kaydetme bu cihazi bir hesaba YENI bagladi. Kabuk "Hesaba bağlandı" der ve
     * akisin basladigi yere doner.
     */
    val linkedNow: Boolean = false,
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

/**
 * Basarisiz ekranin metni. SAF.
 *
 * NEYDI: hesabi okuma da, baglantiyi cihaza yazma da ayni "Hesabınıza
 * ulaşılamadı. İnternet bağlantınızı kontrol edin" metnini gosteriyordu. Yazma
 * patladiginda hesaba ulasilmisti; kullanici interneti kontrol ediyor, "Tekrar
 * dene" ayni yerde yeniden patliyordu. Yazma TEK islemde oldugu icin cihazda
 * hicbir sey degismedi - metin bunu soyler.
 */
fun ProfileSetupUiState.failureMessage(): String =
    if (commitFailed) {
        "Bağlantı kaydedilemedi; bu cihazda hiçbir şey değişmedi. Tekrar deneyin."
    } else {
        "Hesabınıza ulaşılamadı. İnternet bağlantınızı kontrol edip tekrar deneyin."
    }

/** Cakisma ekraninin metinleri. */
data class ConflictCopy(
    val title: String,
    val body: String,
    val useAccountTitle: String,
    val useAccountNote: String,
    val mergeTitle: String,
    val mergeNote: String,
)

/**
 * Cakisma ekrani. SAF.
 *
 * Iki yolun notu NE KAYBEDILECEGINI soyler: "Hesaptakileri kullan" cihazdakini
 * siler (yanindaki "Önce yedek al" bunun icin), "Birleştir" ayni alimi iki kez
 * sayabilir. Kullanici sonucu secmeden once gormeli.
 */
fun conflictCopy(localRecords: Int, serverRecords: Int): ConflictCopy = ConflictCopy(
    title = "Bu cihazda da, hesabınızda da kayıt var",
    body = "Bu cihaz: $localRecords kayıt · Hesap: $serverRecords kayıt",
    useAccountTitle = "Hesaptakileri kullan",
    useAccountNote = "Bu cihazdaki kayıtlar silinir.",
    mergeTitle = "Birleştir",
    mergeNote = "Aynı alımı iki cihaza da girdiyseniz iki kez sayılır; sonra Aktivite'den silebilirsiniz.",
)

/**
 * Cakismadan sonraki secim ekraninda, secilen yolun hatirlatmasi. Secim
 * ekranina gecince cakisma metni kaybolur; "Devam"a basmadan once ne olacagi
 * yine gorunmeli.
 */
fun ProfileSetupUiState.choiceNote(): String? = when (conflictChoice) {
    ConflictChoice.UseAccount -> "Devam edince bu cihazdaki kayıtlar silinir, hesaptakiler gelir."
    ConflictChoice.Merge -> "Devam edince bu cihazdaki kayıtlar hesaptakilerle birleştirilir."
    null -> null
}

/**
 * Secim onceki profilden farkliysa: cihazda girilmis kayitlarin akibeti. SAF.
 *
 * NEYDI: aktarim yoktu. Cihazda "Merve" birinci profildi; hesapta birinci profil
 * "Burak Can" cikinca Merve ikinciyi seciyordu, ama bu telefonda girdigi her
 * sey birinci profilde - yani Burak'in adina - kaliyordu.
 */
fun ProfileSetupUiState.remapNote(): String? {
    if (!linking) return null
    val owner = thisDeviceIsOwner ?: return null
    val chosen = if (owner) LocalOwnerMemberId else LocalPartnerMemberId
    val previous = previousMemberId ?: return null
    if (chosen == previous) return null
    val count = localOnlyByAuthor[previous] ?: 0
    if (count == 0) return null
    val name = if (owner) ownerName.ifBlank { "1. profil" } else partnerName.ifBlank { "2. profil" }
    return "Bu cihazda daha önce girilen $count kayıt da $name adına aktarılır."
}

sealed interface ProfileSetupIntent {
    /** Ekran her gorundugunde: oturuma bakar, girisliyse hesabi indirir. */
    data object Load : ProfileSetupIntent
    data object Retry : ProfileSetupIntent

    /** Cakismada "Hesaptakileri kullan" ya da "Birleştir": secim ekranina gecer. */
    data class ChooseConflict(val choice: ConflictChoice) : ProfileSetupIntent

    /** Secim ekranindan cakisma sorusuna geri doner (secim degistirilebilsin). */
    data object BackToConflict : ProfileSetupIntent

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

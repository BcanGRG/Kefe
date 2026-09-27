package com.kefe.app.navigation

import com.kefe.app.ui.screens.account.SignInPurpose

/**
 * Acilista yiginin KOKU. SAF: her satiri testte denenebilsin (bkz. RootRouteTest).
 *
 * Sira onemli:
 *  1. [locked]: kilit ekrani. Kilit yalniz kurulum bitmisken gelir (bkz.
 *     isLaunchLocked), yani burada profil zaten secili.
 *  2. Acilis akisi gecilmis VE bu telefonun profili secili: Ozet.
 *  3. Acilis akisi gecilmis YA DA oturum acik: "bu telefon kimin" adimi. Bu,
 *     kurulumun ya da hesaba baglanmanin ortasinda kapatilan uygulamayi yakalar
 *     - kod dogrulandi ama profil secilmedi, ya da "Atla" deyip profil
 *     ekraninda kapatildi.
 *  4. Hicbiri: hosgeldin - "Nasil kullanmak istersiniz?".
 *
 * NEYDI: "onboarded degil ya da kilitli" tek bir LoginKey'e gidiyordu. Giris
 * formu, kilit ve ilk acilis ayni ekranin asamalariydi; oturumu acik ama profil
 * secmemis bir telefon bile yeniden e-posta formuyla karsilaniyordu.
 */
internal fun rootFor(
    locked: Boolean,
    onboarded: Boolean,
    activeMemberId: String?,
    signedIn: Boolean,
): KefeKey = when {
    locked -> LockKey
    onboarded && activeMemberId != null -> SummaryKey
    onboarded || signedIn -> ProfileSetupKey
    else -> WelcomeKey
}

/**
 * Hesap akisinin ekranlari: kok bunlardan biriyken navigasyon kromu (alt nav,
 * ray, yan nav) cizilmez; giristen sonra da "cagiran sekmeye don" denemez -
 * donulecek bir sekme yok.
 */
internal fun KefeKey.isAccountFlow(): Boolean =
    this == WelcomeKey ||
        this == LockKey ||
        this is SignInKey ||
        this == OnboardingKey ||
        this == ProfileSetupKey

/**
 * Profil adiminda "Farklı e-postayla gir"den sonra: giris ekraninin ALTINDA
 * ne kalacak ([root]; null = yigin degismez, yalniz profil adimi kapanir) ve
 * giris hangi amacla acilacak. SAF.
 *
 *  - Profil adimi uygulamanin USTUNE itilmisse (Ozet'teki ya da Ayarlar'daki
 *    "Tamamla"): o kapanir, giris cagiranin ustune "Hesaba bağla" olarak acilir.
 *  - Kokse ve bu telefonun profili henuz secilmediyse kurulum bitmemistir:
 *    hosgeldine donulur. Giristen geri gelen iki yolu yeniden gorur -
 *    "Bu cihazda kullan" da secilebilir.
 *  - Kokse ve profil seciliyse (Ayarlar'dan baglanan kurulu cihaz): Ozet.
 *
 * NEYDI: ilk acilista hesapla giren kullanici profil adimina KOK olarak
 * geliyordu; geri tusu uygulamadan cikiyor, yanlis yazilan e-postayi
 * duzeltmenin yolu yoktu.
 */
internal fun restartSignIn(pushedOverApp: Boolean, activeMemberId: String?): SignInRestart = when {
    pushedOverApp -> SignInRestart(root = null, purpose = SignInPurpose.Link)
    activeMemberId == null -> SignInRestart(root = WelcomeKey, purpose = SignInPurpose.FirstRun)
    else -> SignInRestart(root = SummaryKey, purpose = SignInPurpose.Link)
}

/** [restartSignIn]'in sonucu. */
internal data class SignInRestart(val root: KefeKey?, val purpose: SignInPurpose)

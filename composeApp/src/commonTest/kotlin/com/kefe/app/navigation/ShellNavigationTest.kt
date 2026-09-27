package com.kefe.app.navigation

import com.kefe.app.ui.screens.account.SignInPurpose
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kabugun Ayarlar ve secim kurallari.
 *
 * Alt barda Ayarlar'in yerini Plan aldi. Masaustunde Ayarlar yan menunun kok
 * satiri olarak kaldi; telefonda ve tablette ITILEN bir ekran oldu. Uc sey
 * sabitlenir: ayni disliye ikinci dokunus ikinci bir kopya itmez; Ayarlar yarim
 * bir giris ya da profil adiminin ustune itilmez; ve Ayarlar yiginin herhangi bir
 * yerindeyken hicbir sekme (telefon/tablet) ya da yalniz "Ayarlar" satiri
 * (masaustu) secili gorunur.
 *
 * NEYDI: secim yalniz koke bakip `coerceAtLeast(0)` ile sifira kirpiliyordu;
 * kok listede yokken Ozet secili gorunuyordu.
 */
class ShellNavigationTest {

    @Test
    fun `masaustunde Ayarlar kok olur`() {
        assertEquals(OpenSettingsStep.AsRoot, openSettingsStep(listOf(SummaryKey), expanded = true))
        // Yiginda zaten olsa da: masaustunde Ayarlar bir satir, selectTab onu koke alir.
        assertEquals(
            OpenSettingsStep.AsRoot,
            openSettingsStep(listOf(SummaryKey, SettingsKey, ProfilesKey), expanded = true),
        )
    }

    @Test
    fun `telefonda ve tablette Ayarlar itilir`() {
        assertEquals(OpenSettingsStep.Push(popTo = 0), openSettingsStep(listOf(SummaryKey), expanded = false))
        // Ikincil ekran yerinde kalir: geri oku oraya doner.
        assertEquals(
            OpenSettingsStep.Push(popTo = 1),
            openSettingsStep(listOf(GoalsKey, GoalDetailKey("g")), expanded = false),
        )
    }

    @Test
    fun `yarim giris ya da profil adimi Ayarlarin altinda kalmaz`() {
        // Tablette ray itilmis girisin yaninda da gorunur. Disli Ayarlar'i girisin
        // USTUNE itseydi, Ayarlar'dan tamamlanan ikinci bir giristen sonra geri tusu
        // terk edilmis formu yeniden acardi.
        assertEquals(
            OpenSettingsStep.Push(popTo = 0),
            openSettingsStep(listOf(SummaryKey, SignInKey(SignInPurpose.Relogin)), expanded = false),
        )
        // Ozet'teki "Tamamla" ile itilen profil adimi ve onun ustundeki giris.
        assertEquals(
            OpenSettingsStep.Push(popTo = 0),
            openSettingsStep(listOf(SummaryKey, ProfileSetupKey), expanded = false),
        )
        assertEquals(
            OpenSettingsStep.Push(popTo = 0),
            openSettingsStep(
                listOf(SummaryKey, ProfileSetupKey, SignInKey(SignInPurpose.Link)),
                expanded = false,
            ),
        )
        // Ayarlar'dan acilan giris: Ayarlar zaten yiginda, giris kapanir.
        assertEquals(
            OpenSettingsStep.PopTo(1),
            openSettingsStep(listOf(SummaryKey, SettingsKey, SignInKey(SignInPurpose.Link)), expanded = false),
        )
    }

    @Test
    fun `yiginda zaten varsa ikinci kopya itilmez, ustundekiler kapanir`() {
        assertEquals(
            OpenSettingsStep.PopTo(1),
            openSettingsStep(listOf(SummaryKey, SettingsKey, ProfilesKey), expanded = false),
        )
        // Masaustunde kok olan Ayarlar, pencere daraldiktan sonra.
        assertEquals(OpenSettingsStep.PopTo(0), openSettingsStep(listOf(SettingsKey), expanded = false))
    }

    @Test
    fun `Ayarlar aciksa telefonda hicbir sekme secili degil`() {
        assertEquals(-1, navSelection(listOf(SummaryKey, SettingsKey), topLevelDestinations))
        // Ustune giris itilmis olsa da secim degismez.
        assertEquals(
            -1,
            navSelection(listOf(SummaryKey, SettingsKey, SignInKey(SignInPurpose.Link)), topLevelDestinations),
        )
    }

    @Test
    fun `Ayarlar aciksa masaustunde Ayarlar satiri secili`() {
        // Telefonda itilip pencere genisleyince de: Ayarlar kok degil ama yiginda.
        assertEquals(6, navSelection(listOf(SummaryKey, SettingsKey, ProfilesKey), desktopDestinations))
        assertEquals(6, navSelection(listOf(SettingsKey), desktopDestinations))
    }

    @Test
    fun `aksi halde kokun satiri`() {
        assertEquals(0, navSelection(listOf(SummaryKey), topLevelDestinations))
        assertEquals(2, navSelection(listOf(GoalsKey, GoalDetailKey("g")), topLevelDestinations))
        assertEquals(3, navSelection(listOf(PlanKey), topLevelDestinations))
        assertEquals(3, navSelection(listOf(PlanKey), desktopDestinations))
        assertEquals(4, navSelection(listOf(MarketKey), desktopDestinations))
    }

    @Test
    fun `kok listede yoksa hicbiri - Ozete kirpilmaz`() {
        assertEquals(-1, navSelection(listOf(ProfileSetupKey), topLevelDestinations))
        // Piyasa telefonda sekme degil.
        assertEquals(-1, navSelection(listOf(MarketKey), topLevelDestinations))
        assertEquals(-1, navSelection(emptyList(), topLevelDestinations))
    }
}

package com.kefe.app.ui.screens.account

import com.kefe.app.data.sync.CloudMode
import com.kefe.app.data.sync.CloudStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hosgeldin ve giris ekranlarinin metinleri ve gezinmesi.
 *
 * NEYDI: ilk ekran e-posta formuydu, hesapsiz kullanim altta kucuk bir
 * "Hesapsız başla"ydi; dugmeler "Giriş kodu gönder"/"Giriş yap" diyordu, oysa
 * ayni akis hesap da aciyor. Farkli e-postanin ayri ve bos bir hesap actigi
 * hic soylenmiyordu.
 */
class SignInCopyTest {

    // --- Hosgeldin ---------------------------------------------------------

    @Test
    fun `hosgeldin iki esit kart ve not`() {
        val options = welcomeOptions(cloudConfigured = true)
        assertEquals(listOf(WelcomeChoice.OnDevice, WelcomeChoice.WithAccount), options.map { it.choice })
        assertEquals("Bu cihazda kullan", options[0].title)
        assertEquals("Hesapla, iki telefonda", options[1].title)
        assertTrue("Hesap gerekmez" in options[0].body)
        assertTrue("bu e-postayla hesap yoksa açılır" in options[1].body)
        assertNotNull(welcomeFooter(cloudConfigured = true))
    }

    @Test
    fun `bulut yoksa yalniz cihaz karti ve not yok`() {
        val options = welcomeOptions(cloudConfigured = false)
        assertEquals(listOf(WelcomeChoice.OnDevice), options.map { it.choice })
        assertNull(welcomeFooter(cloudConfigured = false))
    }

    // --- Giris metinleri -----------------------------------------------------

    @Test
    fun `amaca gore baslik`() {
        assertEquals("Hesapla başla", signInCopy(SignInPurpose.FirstRun).title)
        assertEquals("Hesaba bağla", signInCopy(SignInPurpose.Link).title)
        assertEquals("Yeniden giriş yap", signInCopy(SignInPurpose.Relogin).title)
    }

    @Test
    fun `her amacta ayni e-posta uyarisi`() {
        for (purpose in SignInPurpose.entries) {
            val warning = signInCopy(purpose).warning
            assertTrue("aynı e-postayı" in warning, "$purpose uyarisi eksik")
            assertTrue("ayrı ve boş bir hesap" in warning, "$purpose uyarisi eksik")
        }
    }

    @Test
    fun `ilk giris ve baglama tek akisi soyler`() {
        for (purpose in listOf(SignInPurpose.FirstRun, SignInPurpose.Link)) {
            assertTrue("hesap yoksa açılır" in signInCopy(purpose).note, "$purpose")
        }
        // Yeniden giriste hesap zaten var: "acilir" sozu yanlis olurdu.
        assertFalse("hesap yoksa açılır" in signInCopy(SignInPurpose.Relogin).note)
    }

    @Test
    fun `yalniz baglama kayitlarin akibetini soyler`() {
        assertEquals("Bu cihazdaki kayıtlar hesaba eklenecek.", signInCopy(SignInPurpose.Link).extra)
        assertNull(signInCopy(SignInPurpose.FirstRun).extra)
        assertNull(signInCopy(SignInPurpose.Relogin).extra)
    }

    @Test
    fun `dugmeler giris ile kaydi ayirmaz`() {
        assertEquals("Kod gönder", SendCodeLabel)
        assertEquals("Doğrula", VerifyCodeLabel)
    }

    @Test
    fun `hicbir metin hesapsiz baslamayi onermez`() {
        for (purpose in SignInPurpose.entries) {
            val copy = signInCopy(purpose)
            val all = listOfNotNull(copy.title, copy.note, copy.warning, copy.extra).joinToString(" ")
            assertFalse("Hesapsız" in all, "$purpose: $all")
        }
    }

    // --- Geri ----------------------------------------------------------------

    @Test
    fun `dogrulama surerken geri yok`() {
        assertEquals(SignInBack.Ignore, signInBack(LoginUiState(codeSent = true, verifying = true)))
        assertEquals(SignInBack.Ignore, signInBack(LoginUiState(codeSent = true, signedIn = true)))
    }

    @Test
    fun `kod kutusunda geri e-postaya doner e-postada ekrani kapatir`() {
        assertEquals(SignInBack.EditEmail, signInBack(LoginUiState(codeSent = true)))
        assertEquals(SignInBack.Leave, signInBack(LoginUiState()))
    }

    // --- Giristen sonra --------------------------------------------------------

    @Test
    fun `ayni hesaba yeniden giris cagiran sekmeye doner`() {
        val cloud = CloudMode.Cloud("e", CloudStatus.Syncing)
        assertEquals(AfterSignIn.ReturnToCaller, afterSignIn(cloud, "member_owner", callerInShell = true))
        // Altinda sekme yoksa (hosgeldin) Ozet'e girilir.
        assertEquals(AfterSignIn.EnterApp, afterSignIn(cloud, "member_owner", callerInShell = false))
    }

    @Test
    fun `bagli olmayan hesap her yerden profil adimina`() {
        for (caller in listOf(true, false)) {
            assertEquals(AfterSignIn.ProfileSetup, afterSignIn(CloudMode.LinkPending("e"), "member_owner", caller))
            assertEquals(AfterSignIn.ProfileSetup, afterSignIn(CloudMode.LinkPending("e"), null, caller))
            assertEquals(AfterSignIn.ProfileSetup, afterSignIn(null, "member_owner", caller))
            // Bagli ama profil secilmemis: yine sorulur.
            assertEquals(
                AfterSignIn.ProfileSetup,
                afterSignIn(CloudMode.Cloud("e", CloudStatus.Synced), null, caller),
            )
        }
    }
}

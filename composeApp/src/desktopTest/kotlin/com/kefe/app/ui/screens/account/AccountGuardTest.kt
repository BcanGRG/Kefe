package com.kefe.app.ui.screens.account

import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hesap degistirme engeli: bir hesaba BAGLI cihaz baska bir hesapla giremez.
 *
 * NEYDI: engel yoktu. Yanlis (ya da esin kendi) e-postasiyla giren bagli cihaz
 * profil adimina gidiyor, iki hesabin kayitlari ayni veritabaninda
 * bulusuyordu. Artik kod dogru olsa da yeni oturum yalniz bu cihazda kapanir,
 * baglanti ve kayitlar yerinde kalir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountGuardTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Dogrulama [userId] hesabinin oturumunu yazar; cikislari sayar. */
    private class GuardAuth(private val userId: String) : AuthRepository {
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
        var signOuts = 0
        override fun observeAuthState(): Flow<AuthState> = state
        override val isCloudConfigured: Boolean = true
        override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun verifyCode(email: String, code: String): Result<Unit> {
            state.value = AuthState.SignedIn(AuthSession(userId, email, "tok", "r", 0L))
            return Result.success(Unit)
        }
        override suspend fun validAccessToken(): String? = null
        override suspend fun signOut() {
            signOuts++
            state.value = AuthState.SignedOut
        }
    }

    private fun linkedTo(userId: String, email: String = "burak@k.app") = MemoryPreferences(
        mapOf(PreferenceKeys.CloudLinkUserId to userId, PreferenceKeys.CloudLinkEmail to email),
    )

    private fun LoginViewModel.verify(email: String = "merve@k.app") {
        onIntent(LoginIntent.ChangeEmail(email))
        onIntent(LoginIntent.SendCode)
        onIntent(LoginIntent.ChangeCode("123456"))
        onIntent(LoginIntent.VerifyCode)
    }

    @Test
    fun `baska hesaba bagli cihazda giris geri alinir`() = runTest {
        val auth = GuardAuth(userId = "u2")
        val prefs = linkedTo("u1")
        val vm = LoginViewModel(auth, prefs)
        vm.verify()

        val s = vm.state.value
        assertFalse(s.signedIn, "kabuk profil adimina gecmemeli")
        assertFalse(s.verifying)
        assertFalse(s.codeSent, "e-posta adimina donulur")
        assertEquals(1, auth.signOuts, "yeni oturum bu cihazda kapanmali")
        assertEquals(AuthState.SignedOut, auth.state.value)
        val guard = assertNotNull(s.guard)
        assertEquals("Bu cihaz başka bir hesaba bağlı", guard.title)
        assertEquals(
            "Bu cihazdaki kayıtlar burak@k.app hesabına ait. " +
                "Farklı bir hesapla kullanmak için önce Ayarlar › Bu cihazı sıfırla.",
            guard.body,
        )
        // Baglanti ve (dolayisiyla) kayitlarin sahibi degismez.
        assertEquals("u1", prefs.get(PreferenceKeys.CloudLinkUserId))
    }

    @Test
    fun `e-posta degisince engel kalkar`() = runTest {
        val vm = LoginViewModel(GuardAuth(userId = "u2"), linkedTo("u1"))
        vm.verify()
        assertNotNull(vm.state.value.guard)
        vm.onIntent(LoginIntent.ChangeEmail("burak@k.app"))
        assertNull(vm.state.value.guard)
    }

    @Test
    fun `ayni hesaba giris engellenmez`() = runTest {
        val auth = GuardAuth(userId = "u1")
        val vm = LoginViewModel(auth, linkedTo("u1"))
        vm.verify("burak@k.app")
        assertTrue(vm.state.value.signedIn)
        assertNull(vm.state.value.guard)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun `bagli olmayan cihaz her hesapla girer`() = runTest {
        val auth = GuardAuth(userId = "u2")
        val vm = LoginViewModel(auth, MemoryPreferences())
        vm.verify()
        assertTrue(vm.state.value.signedIn)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun `saf kural`() {
        assertTrue(accountSwitchBlocked("u1", "u2"))
        assertFalse(accountSwitchBlocked("u1", "u1"))
        assertFalse(accountSwitchBlocked(null, "u2"))
        assertFalse(accountSwitchBlocked("", "u2"))
        assertFalse(accountSwitchBlocked("u1", null))
        assertTrue("başka bir hesaba ait" in accountSwitchCopy(null).body)
        assertTrue("başka bir hesaba ait" in accountSwitchCopy(" ").body)
    }
}

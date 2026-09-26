package com.kefe.app.ui.screens.account

import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthState
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue

/**
 * Giris ekraninin VM'i: her acilista sifirlanir, dogrulama surerken geri
 * donulmez.
 *
 * NEYDI: VM surec boyunca yasiyor ve hic sifirlanmiyordu. Yarida birakilan bir
 * girisin `signedIn` bayragi kaliyor, sonraki "Giriş yap" e-postayi sormadan
 * geciyordu; "Kontrol ediliyor…" iken geri basilinca dogrulama arkada bitiyor,
 * karsilayan ekran yoktu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInFlowTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Dogrulamayi elle bitirilen sahte kimlik: "Kontrol ediliyor…" ani yakalanir. */
    private class GatedAuth : AuthRepository {
        var verifyGate: CompletableDeferred<Result<Unit>>? = null
        override fun observeAuthState(): Flow<AuthState> = MutableStateFlow(AuthState.SignedOut)
        override val isCloudConfigured: Boolean = true
        override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun verifyCode(email: String, code: String): Result<Unit> =
            verifyGate?.await() ?: Result.success(Unit)
        override suspend fun validAccessToken(): String? = null
        override suspend fun signOut() = Unit
    }

    private fun LoginViewModel.toCodeStep(email: String = "a@k.app") {
        onIntent(LoginIntent.ChangeEmail(email))
        onIntent(LoginIntent.SendCode)
        onIntent(LoginIntent.ChangeCode("123456"))
    }

    @Test
    fun `acilis onceki denemenin izini siler`() = runTest {
        val vm = LoginViewModel(GatedAuth(), MemoryPreferences())
        vm.toCodeStep()
        vm.onIntent(LoginIntent.VerifyCode)
        assertTrue(vm.state.value.signedIn)

        // Kabuk girisi karsilamadan ekran birakildi (orn. sekme degisti);
        // sonraki acilis temiz baslamali.
        vm.onIntent(LoginIntent.Begin())
        val s = vm.state.value
        assertFalse(s.signedIn)
        assertFalse(s.codeSent)
        assertEquals("", s.code)
        assertEquals("", s.email)
        assertEquals(0, s.resendCooldown)
    }

    @Test
    fun `yeniden giris e-postayla dolu acilir`() = runTest {
        val vm = LoginViewModel(GatedAuth(), MemoryPreferences())
        vm.onIntent(LoginIntent.Begin(" burak@k.app "))
        assertEquals("burak@k.app", vm.state.value.email)
        assertTrue(vm.state.value.canSendCode)
    }

    @Test
    fun `dogrulama surerken e-postaya donulmez`() = runTest {
        val auth = GatedAuth()
        val gate = CompletableDeferred<Result<Unit>>()
        auth.verifyGate = gate
        val vm = LoginViewModel(auth, MemoryPreferences())
        vm.toCodeStep()
        vm.onIntent(LoginIntent.VerifyCode)
        assertTrue(vm.state.value.verifying)
        assertEquals(SignInBack.Ignore, signInBack(vm.state.value))

        vm.onIntent(LoginIntent.EditEmail)
        assertTrue(vm.state.value.codeSent, "dogrulama surerken kod adimi korunur")

        gate.complete(Result.success(Unit))
        assertTrue(vm.state.value.signedIn)
    }

    @Test
    fun `birakilan dogrulamanin sonucu yeni acilisa yazilmaz`() = runTest {
        val auth = GatedAuth()
        val gate = CompletableDeferred<Result<Unit>>()
        auth.verifyGate = gate
        val vm = LoginViewModel(auth, MemoryPreferences())
        vm.toCodeStep()
        vm.onIntent(LoginIntent.VerifyCode)

        vm.onIntent(LoginIntent.Begin())
        gate.complete(Result.success(Unit))

        assertFalse(vm.state.value.signedIn)
        assertFalse(vm.state.value.verifying)
    }
}

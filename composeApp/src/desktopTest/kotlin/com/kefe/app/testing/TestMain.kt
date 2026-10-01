package com.kefe.app.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

/**
 * VM testlerinin Main'i. [install] Main'i UnconfinedTestDispatcher yapar; testte
 * kurulan her VM [track] ile kaydedilir; [release] once VM'leri DURDURUR ve
 * BITMELERINI bekler, Main'i ancak ondan sonra geri alir.
 *
 * ```
 * private val main = TestMain()
 *
 * @BeforeTest
 * fun setUp() = main.install()
 *
 * @AfterTest
 * fun tearDown() = main.release()
 *
 * val vm = main.track(LoginViewModel(auth, prefs))
 * ```
 *
 * NEDEN: viewModelScope Main uzerinden calisir, depolar ve turetim Default'ta.
 * VM temizlenmezse kapsami testten uzun yasar; yarim kalan bir is (withContext
 * donusu, Default'tan gelen bir akis degeri) Main'e geri doner. Bu donus
 * resetMain/setMain degisimine denk gelirse TestMainDispatcher "dispatch gerekli
 * mi"yi eski Main'e (Swing: evet), dispatch'i yeni testin Unconfined'ina sorar
 * ve UnsupportedOperationException firlar. runTest onu SIRADAKI testin - belki
 * baska bir sinifin - hatasi olarak raporlar: tam kosuda ara sira, tek basina hic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestMain {

    private val viewModels = mutableListOf<ViewModel>()

    fun install() = Dispatchers.setMain(UnconfinedTestDispatcher())

    /** VM'yi kaydeder ve aynen dondurur; [release] onu durdurur. */
    fun <T : ViewModel> track(vm: T): T = vm.also { viewModels += it }

    fun release() {
        try {
            // Sure siniri: bitmeyen bir VM butun kosuyu kilitlemesin, bu testi dusursun.
            runBlocking {
                withTimeout(10_000) { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
            }
        } finally {
            viewModels.clear()
            // Durdurma patlasa da Main geri alinir: kalsa sonraki testler bu testin Main'ini gorurdu.
            Dispatchers.resetMain()
        }
    }
}

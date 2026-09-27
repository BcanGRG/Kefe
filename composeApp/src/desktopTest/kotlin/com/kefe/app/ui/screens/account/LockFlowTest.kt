package com.kefe.app.ui.screens.account

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.security.BiometricGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Acilis kilidinin VM'i.
 *
 * NEYDI: `unlocked` true kaldiktan sonra yeni bir "Kilidi aç" yine istemi
 * aciyordu (yalniz "istem acik mi" bakiliyordu). Activity yeniden yaratilinca
 * kilit ekrani bir kare bestelenip Unlock gonderiyor, iceri alinmis
 * kullanicinin ekraninda bakiyelerin ustunde sistem istemi kaliyordu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockFlowTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun lockVm(): LockViewModel {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val database = createKefeDatabase(driver)
        database.bootstrapIfNeeded()
        val repo = SqlDelightPortfolioRepository(database, FixedKefeClock(millis = 9_000L), NoPrices())
        // Masaustu kapisi "desteklenmiyor" der: kilit perdedir, kapi degil -
        // kullanici icin kimlik sorulmadan iceri alinir.
        return LockViewModel(repo, BiometricGate())
    }

    @Test
    fun `kimlik sorulamayan cihazda kilit iceri alir`() = runTest {
        val vm = lockVm()
        vm.onIntent(LockIntent.Unlock)
        assertTrue(vm.state.value.unlocked)
        assertFalse(vm.state.value.unlocking)
    }

    @Test
    fun `acilmis kilit yeniden istem baslatmaz`() = runTest(UnconfinedTestDispatcher()) {
        val vm = lockVm()
        vm.onIntent(LockIntent.Unlock)
        assertTrue(vm.state.value.unlocked)

        // Sonraki her durum kaydedilir: ikinci Unlock istem baslatsaydi
        // `unlocking = true` bir kez gorunurdu.
        val seen = mutableListOf<LockUiState>()
        val job = launch { vm.state.collect { seen += it } }
        vm.onIntent(LockIntent.Unlock)
        job.cancel()

        assertTrue(seen.none { it.unlocking }, "acilmis kilit ikinci istem acmamali: $seen")
        assertTrue(vm.state.value.unlocked)
    }
}

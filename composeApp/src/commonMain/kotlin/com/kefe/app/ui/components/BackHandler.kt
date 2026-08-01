package com.kefe.app.ui.components

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Sistem geri tusunu ortak koddan yakalar.
 *
 * Compose'un `androidx.compose.ui.backhandler.BackHandler` fonksiyonu kullanimdan
 * kaldirildi; yerine navigationevent'in [NavigationBackHandler] fonksiyonu geldi.
 * Yeni API her isleyici icin ayri bir [rememberNavigationEventState] istiyor -
 * bu sarmalayici o kurulumu tek yerde tutar, cagiran taraf yalnizca [enabled] ve
 * [onBack] verir. NavDisplay de kendi geri isleyicisini ayni gonderici uzerinden
 * kaydeder, yani ikisi ayni sirada bulusur.
 *
 * KOSULSUZ CAGIRIN. Hangi isleyicinin calisacagini SON BESTELENEN belirler;
 * `if` icine alinan bir isleyici bestelemeye girip cikarken bu sirayi degistirir
 * ve davranis ongorulemez olur - kutuphanenin kendi uyarisi da budur
 * ("It is important to call this composable unconditionally").
 *
 * Emulatorde tam olarak bu yasandi: ayni alt sayfada geri tusu bir seferinde
 * 1. adima donuyor, bir seferinde sayfayi kapatiyor, bir seferinde hic
 * yakalanmayip uygulamadan cikiyordu.
 *
 * Dogrusu: isleyiciyi katmanin SAHIBI kosulsuz beste eder ve [enabled] ile
 * ac/kapa yapar ("menu acik mi", "sayfa gorunur mu"). Katmanin KENDISI yine
 * kosullu cizilebilir; kosullu olmamasi gereken yalniz bu cagridir.
 *
 * Tek istisna gezinme girdileridir (`entry<...> { }`): onlari NavDisplay
 * yonetir, girdinin icindeki isleyici girdiyle birlikte gelir gider.
 */
@Composable
fun KefeBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)

    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}

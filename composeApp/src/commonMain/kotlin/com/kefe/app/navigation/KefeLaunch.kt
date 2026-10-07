package com.kefe.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uygulamanin disaridan (ana ekran widget'i, kisayol) acildigi sayfa. */
enum class LaunchTarget {
    /** Bu ayin Harcamalar sayfasi. */
    Expenses,
}

/**
 * Disaridan gelen "su sayfada ac" istegi. Giris noktasi (Android: MainActivity)
 * yazar, kabuk (App.kt) uygulama icerideyken - kilit acildiktan, kurulum
 * bittikten sonra - gidip istegi tuketir.
 */
object KefeLaunch {
    private val pendingTarget = MutableStateFlow<LaunchTarget?>(null)
    val pending: StateFlow<LaunchTarget?> = pendingTarget.asStateFlow()

    fun request(target: LaunchTarget) {
        pendingTarget.value = target
    }

    /** Istek yalniz bir kez tuketilir; ikinci okuyucu bos bulur. */
    fun consume(target: LaunchTarget): Boolean = pendingTarget.compareAndSet(target, null)
}

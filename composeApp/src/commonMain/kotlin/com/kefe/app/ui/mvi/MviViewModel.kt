package com.kefe.app.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Ekranlarin ortak MVI tabani.
 *
 * Uc parca: DURUM (ekranda ne goruluyor), NIYET (kullanici ne yapti), ETKI (bir
 * kereye mahsus ne olmali).
 *
 * Etkinin ayri bir kanal olmasinin nedeni: "sheet kapansin", "hata mesaji
 * gorunsun", "su ekrana git" gibi seyler DURUM DEGILDIR. Duruma bayrak olarak
 * konurlarsa tuketildikten sonra elle temizlenmeleri gerekir - nitekim islem
 * ekleme ekraninda once oyle yapildi: `saved: Boolean` + `ConsumeSaved` niyeti.
 * Bayrak temizlenmeyi unutursa sheet bir daha acilir acilmaz kapaniyordu.
 *
 * [Channel] secildi, SharedFlow degil: etki dinleyici yokken de birikmeli
 * (ekran arka planda olabilir) ve YALNIZ BIR KEZ tuketilmeli. SharedFlow ikinci
 * bir toplayiciya ayni etkiyi tekrar verirdi.
 */
abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {

    // Dogrudan alt siniflara aciliyor. Kapatip yalniz setState(lambda) birakmak
    // daha sikiydi ama mevcut ekranlarin hepsi `_state.value = ...` yaziyor;
    // toplu cevirmek cok satirli copy() govdelerini kirma riski tasiyor.
    protected val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    // Tasma durumunda EN ESKI atilir: birikmis eski etkiyi gostermek, kullanicinin
    // az once yaptigi isin sonucunu yutmaktan iyidir.
    private val _effects = Channel<E>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: Flow<E> = _effects.receiveAsFlow()

    /** Okuma kolayligi: `current.copy(...)` kaliba `_state.value`dan daha yakin. */
    protected val current: S get() = _state.value

    protected fun setState(reduce: S.() -> S) {
        _state.value = _state.value.reduce()
    }

    protected fun emitEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /** Ekranin tek giris noktasi. */
    abstract fun onIntent(intent: I)
}

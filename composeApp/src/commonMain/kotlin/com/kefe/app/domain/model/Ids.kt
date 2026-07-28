package com.kefe.app.domain.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Kayit kimlikleri.
 *
 * ISLEM VE HEDEF KIMLIKLERI UUID'DIR - icerikten TURETILMEZ.
 *
 * Once turetiliyordu: "tx_<pozisyon>_<tarih>_<miktar>". Tek cihazda bile ayni
 * gun ayni miktarda ikinci alim ayni kimligi uretiyordu; depo bunu yerelde sonuna
 * "_2" ekleyerek cozuyordu. Iki cihaz oldugunda bu cozum bozulur: her cihaz kendi
 * basina numaralandirir, ayni islem iki farkli kimlikle iki kez gorunur ya da iki
 * ayri islem tek kimlikle carpisip biri kaybolur.
 *
 * POZISYONLAR BUNUN DISINDA: kimlikleri "pos_<varlik anahtari>" olarak kalir ve
 * bu bir kusur degil, KASITLI bir birlestirme noktasidir. Iki telefon "ceyrek
 * altin" icin bagimsiz olarak ayni kimligi uretir; esitlemede iki ayri "Ceyrek"
 * satiri olusmaz, ayni satirda bulusurlar.
 */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

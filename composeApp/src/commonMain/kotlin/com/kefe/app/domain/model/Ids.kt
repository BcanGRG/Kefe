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

/**
 * AYLIK PLAN, GELIR VE BUTCE KIMLIKLERI ICERIKTEN TURETILIR - bilerek.
 *
 * Yukaridaki UUID kurali "ayni gun ayni miktarda iki alim iki ayri kayittir"
 * icindi. Burada tersi dogru: "Ekim'in gram altin satiri", "Burak'in Ekim
 * maasi" ve "Ekim'in market butcesi" TEKTIR. Iki telefon bunu ayri ayri
 * olusturursa UUID ile iki satir olur ve toplamlar ikiye katlanir; turetilmis
 * kimlikle ayni satirda bulusur, son yazan kazanir. Pozisyon kimligi ile ayni
 * birlestirme noktasi.
 */
fun planItemId(month: YearMonth, assetKey: String): String = "pi_${month.idPart()}_$assetKey"

fun incomeId(month: YearMonth, memberId: String, kind: IncomeKind): String =
    "inc_${month.idPart()}_${memberId}_${kind.name}"

fun budgetId(month: YearMonth, category: ExpenseCategory): String = "eb_${month.idPart()}_${category.name}"

private fun YearMonth.idPart(): String = "${year}_${month.toString().padStart(2, '0')}"

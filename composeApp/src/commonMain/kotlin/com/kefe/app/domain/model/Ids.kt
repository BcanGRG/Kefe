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
    incomeIdOf(month, memberId, kind.name)

/**
 * [incomeId]'nin ham hali: tur METIN olarak gelir. Tablo turu duz metin tutar
 * (bkz. 12.sqm); hesaba baglanirken gelir baska profile aktarilinca (bkz.
 * data/sync/CloudLink.kt, planIncomeRemap) bu telefonun tanimadigi bir tur de ("Bonus",
 * daha yeni bir surumden) AYNI kurala gore yeniden kimliklenmeli. Enum'a
 * cevrilseydi o satir "Extra"nin kimligine kayar, oradaki geliri ezerdi.
 */
fun incomeIdOf(month: YearMonth, memberId: String, kindName: String): String =
    "inc_${month.idPart()}_${memberId}_$kindName"

/** Hazir kategoride "eb_2026_10_Groceries" (degismedi); ozel kalemde "eb_2026_10_c_tatil". */
fun budgetId(month: YearMonth, category: ExpenseCategory): String = "eb_${month.idPart()}_${category.idKey}"

private fun YearMonth.idPart(): String = "${year}_${month.toString().padStart(2, '0')}"

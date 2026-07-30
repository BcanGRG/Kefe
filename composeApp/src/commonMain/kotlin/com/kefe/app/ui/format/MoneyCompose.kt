package com.kefe.app.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.kefe.app.ui.theme.LocalShowCents

/**
 * Satir ici TL tutari - "Kuruşları göster" ayarina uyar.
 *
 * [Money.tl]'nin bilincli olarak Composable OLMAYAN kardesi degildir: bu, ayarin
 * degerini [LocalShowCents]'ten okur ve ondalik sayisini ona gore secer.
 *
 * YALNIZ SATIR ICI tutarlar bunu kullanmali - islem satiri, varlik detayi,
 * aktivite. Hero toplamlar (Ozet'in buyuk rakami) 0 ondalikta kalir: ayarin alt
 * metni de bunu soyluyor ("Ana toplamlarda yine gizli kalır").
 */
@Composable
@ReadOnlyComposable
fun moneyTl(value: Double, spaced: Boolean = false): String =
    Money.tl(value, spaced = spaced, decimals = if (LocalShowCents.current) 2 else 0)

/** Isaretli varyant (kar/zarar satirlari). */
@Composable
@ReadOnlyComposable
fun moneyTlSigned(value: Double, spaced: Boolean = false): String =
    Money.tlSigned(value, spaced = spaced, decimals = if (LocalShowCents.current) 2 else 0)

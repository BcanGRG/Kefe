package com.kefe.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.kefe.app.domain.model.AssetClass

/**
 * Varlik sinifinin ikonu - TEK kaynak.
 *
 * NEYDI: Ozet, Varliklar, ekleme sayfasi ve galeride dort ayri ozel kopyasi
 * vardi; Plan sekmesi besinci olacakti. Hepsi buna baglandi - bir sinifin ikonu
 * degisince bes ekran birlikte degisir.
 */
fun AssetClass.icon(): ImageVector = when (this) {
    AssetClass.Gold -> KefeIcons.Gold
    AssetClass.Silver -> KefeIcons.Silver
    AssetClass.Fx -> KefeIcons.Fx
    AssetClass.Fund -> KefeIcons.Fund
    AssetClass.Stock -> KefeIcons.Stock
    AssetClass.Cash -> KefeIcons.Cash
}

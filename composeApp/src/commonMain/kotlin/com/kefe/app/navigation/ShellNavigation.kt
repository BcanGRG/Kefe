package com.kefe.app.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Ayarlar'i acmanin yigina etkisi. SAF (bkz. ShellNavigationTest).
 *
 * NEDEN ayri bir kural: alt barda Ayarlar'in yerini Plan aldi. Masaustunde
 * Ayarlar yine yan menunun bir satiri (kok olur); telefonda ve tablette ise
 * Ozet'teki ya da raydaki disliyle ITILEN, geri oklu bir ekran. Ayni disliye
 * iki kez basmak ikinci bir kopya itmemeli.
 */
internal sealed interface OpenSettingsStep {
    /** Masaustu: yan menunun kok satiri - selectTab(SettingsKey). */
    data object AsRoot : OpenSettingsStep

    /** Yiginda zaten var: [index]'in ustundekiler kapanir, ikinci kopya itilmez. */
    data class PopTo(val index: Int) : OpenSettingsStep

    /**
     * Telefon/tablet: [popTo]'nun ustundekiler kapanir, Ayarlar onun ustune geri
     * oklu ikincil ekran olarak itilir.
     */
    data class Push(val popTo: Int) : OpenSettingsStep
}

/**
 * Hesap akisinin ITILMIS ekranlari (giris, profil adimi) Ayarlar'in altinda
 * kalmaz: kokun ustundeki ilk hesap akisi ekrani ve ustundekiler kapanir.
 *
 * NEDEN: kabuk kromunu KOKE bakarak cizer, yani tablette ray giris ya da profil
 * adimi itilmisken de gorunur. Raydaki disli (ya da durum satiri) Ayarlar'i o
 * yarim akisin USTUNE itseydi, Ayarlar'dan tamamlanan ikinci bir giristen sonra
 * geri tusu terk edilmis formu (ya da "bu telefon kimin"i) yeniden acardi.
 * NEYDI: Ayarlar bir sekmeyken selectTab yigini zaten temizliyordu; sekmeler
 * bugun de oyle yapar.
 */
internal fun openSettingsStep(stack: List<NavKey>, expanded: Boolean): OpenSettingsStep {
    if (expanded) return OpenSettingsStep.AsRoot
    val at = stack.indexOf(SettingsKey)
    if (at >= 0) return OpenSettingsStep.PopTo(at)
    val flowAt = (1..stack.lastIndex).firstOrNull { (stack[it] as? KefeKey)?.isAccountFlow() == true }
    val popTo = if (flowAt != null) flowAt - 1 else stack.lastIndex
    return OpenSettingsStep.Push(popTo = popTo.coerceAtLeast(0))
}

/**
 * Navigasyonun secili satiri; -1 = hicbiri. Ayarlar yiginin herhangi bir yerindeyse
 * Ayarlar'in satiri (masaustu) ya da -1 (telefon/tablet: Ayarlar sekme degil);
 * degilse kokun satiri.
 *
 * NEYDI: secim yalniz koke bakip `coerceAtLeast(0)` ile sifira kirpiliyordu. Kok
 * listede yokken (orn. Ayarlar itilmisken ya da profil adimi kokken) Ozet secili
 * gorunuyordu.
 */
internal fun navSelection(stack: List<NavKey>, items: List<TopLevelDestination>): Int =
    if (SettingsKey in stack) {
        items.indexOfFirst { it.key == SettingsKey }
    } else {
        items.indexOfFirst { it.key == stack.firstOrNull() }
    }

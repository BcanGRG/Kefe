package com.kefe.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Uygulamadaki tum ikonlar icin tek erisim noktasi.
 * Ikon tanimlari konu bazli dosyalara bolunmustur; cagiran taraf yalniz burayi bilir.
 */
object KefeIcons {

    // --- Cekirdek ---

    val Balance: ImageVector get() = KefeIconsCore.Balance
    val Wallet: ImageVector get() = KefeIconsCore.Wallet
    val Target: ImageVector get() = KefeIconsCore.Target
    val ListMenu: ImageVector get() = KefeIconsCore.ListMenu
    val Plus: ImageVector get() = KefeIconsCore.Plus
    val ChevronRight: ImageVector get() = KefeIconsCore.ChevronRight
    val ChevronDown: ImageVector get() = KefeIconsCore.ChevronDown
    val ChevronUp: ImageVector get() = KefeIconsCore.ChevronUp
    val ArrowBack: ImageVector get() = KefeIconsCore.ArrowBack
    val Close: ImageVector get() = KefeIconsCore.Close
    val Check: ImageVector get() = KefeIconsCore.Check
    val Pencil: ImageVector get() = KefeIconsCore.Pencil
    val Trash: ImageVector get() = KefeIconsCore.Trash
    val Eye: ImageVector get() = KefeIconsCore.Eye
    val EyeOff: ImageVector get() = KefeIconsCore.EyeOff
    val Refresh: ImageVector get() = KefeIconsCore.Refresh
    val Share: ImageVector get() = KefeIconsCore.Share
    val Download: ImageVector get() = KefeIconsCore.Download
    val Copy: ImageVector get() = KefeIconsCore.Copy
    val Search: ImageVector get() = KefeIconsCore.Search
    val DragHandle: ImageVector get() = KefeIconsCore.DragHandle
    val MoreHorizontal: ImageVector get() = KefeIconsCore.MoreHorizontal
    val MoreVertical: ImageVector get() = KefeIconsCore.MoreVertical
    val PlusSmall: ImageVector get() = KefeIconsCore.PlusSmall
    val MinusSmall: ImageVector get() = KefeIconsCore.MinusSmall
    val CloudOff: ImageVector get() = KefeIconsCore.CloudOff
    val Clock: ImageVector get() = KefeIconsCore.Clock
    val Info: ImageVector get() = KefeIconsCore.Info
    val Calendar: ImageVector get() = KefeIconsCore.Calendar
    val Lock: ImageVector get() = KefeIconsCore.Lock
    val Fingerprint: ImageVector get() = KefeIconsCore.Fingerprint
    val ArrowUpRight: ImageVector get() = KefeIconsCore.ArrowUpRight
    val ArrowDownRight: ImageVector get() = KefeIconsCore.ArrowDownRight
    val ArrowUp: ImageVector get() = KefeIconsCore.ArrowUp
    val ArrowDown: ImageVector get() = KefeIconsCore.ArrowDown
    val Pin: ImageVector get() = KefeIconsCore.Pin
    val QrCode: ImageVector get() = KefeIconsCore.QrCode
    val SignalBars: ImageVector get() = KefeIconsCore.SignalBars
    val Wifi: ImageVector get() = KefeIconsCore.Wifi
    val Battery: ImageVector get() = KefeIconsCore.Battery

    // --- Varlik siniflari ---

    val Gold: ImageVector get() = KefeIconsAsset.Gold
    val Silver: ImageVector get() = KefeIconsAsset.Silver
    val Fx: ImageVector get() = KefeIconsAsset.Fx
    val Fund: ImageVector get() = KefeIconsAsset.Fund
    val Cash: ImageVector get() = KefeIconsAsset.Cash

    // --- Hedefler ---

    val Home: ImageVector get() = KefeIconsGoal.Home
    val Car: ImageVector get() = KefeIconsGoal.Car
    val Plane: ImageVector get() = KefeIconsGoal.Plane
    val Education: ImageVector get() = KefeIconsGoal.Education
    val Shield: ImageVector get() = KefeIconsGoal.Shield
    val Heart: ImageVector get() = KefeIconsGoal.Heart
    val Gift: ImageVector get() = KefeIconsGoal.Gift
    val Star: ImageVector get() = KefeIconsGoal.Star
    val Child: ImageVector get() = KefeIconsGoal.Child
    val Laptop: ImageVector get() = KefeIconsGoal.Laptop
    val Palm: ImageVector get() = KefeIconsGoal.Palm
    val Ring: ImageVector get() = KefeIconsGoal.Ring

    /** Hedef anahtarini ikona cevirir; bilinmeyen anahtar Star dondurur. */
    fun goalIcon(key: String): ImageVector = KefeIconsGoal.goalIcon(key)
}

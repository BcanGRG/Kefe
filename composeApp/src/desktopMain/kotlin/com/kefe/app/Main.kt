package com.kefe.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.di.KefePlatform

fun main() {
    // Ilk bestelemede Koin depolari cozuyor, onlar da veritabanini istiyor:
    // surucu pencere acilmadan ONCE kurulmali.
    KefePlatform.install(DatabaseDriverFactory())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Kefe",
            state = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
        ) {
            App()
        }
    }
}

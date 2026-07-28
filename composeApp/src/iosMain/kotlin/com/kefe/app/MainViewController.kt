package com.kefe.app

import androidx.compose.ui.window.ComposeUIViewController
import com.kefe.app.data.db.DatabaseDriverFactory
import com.kefe.app.di.KefePlatform
import platform.UIKit.UIViewController

@Suppress("unused", "FunctionName") // iOS tarafindan cagrilir
fun MainViewController(): UIViewController {
    // Ilk bestelemede Koin depolari cozuyor, onlar da veritabanini istiyor:
    // surucu ekran cizilmeden ONCE kurulmali.
    KefePlatform.install(DatabaseDriverFactory())
    return ComposeUIViewController { App() }
}

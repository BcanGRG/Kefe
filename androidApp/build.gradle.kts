import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Android uygulamasi: yalniz giris noktasi. Ekranlarin, verinin ve platform
 * koprulerinin (veritabani surucusu, biyometrik kilit, dosya paylasimi,
 * Keystore) hepsi composeApp'te; burada MainActivity, manifest, ikon ve acilis
 * temasi durur.
 *
 * Kotlin'i AGP'nin gomulu Kotlin destegi derler - org.jetbrains.kotlin.android
 * UYGULANMAZ, AGP 9 ikisini birlikte reddeder. Surum, kok build dosyasindaki
 * KMP eklentisiyle ayni (libs.versions.toml: kotlin).
 */
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.kefe.app"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        // DEGISMEZ. Telefondaki veri (veritabani /data/data/com.kefe.app/,
        // Keystore'daki oturum anahtari, kilit tercihi) bu kimlige bagli; baska
        // bir applicationId ayri bir uygulama olarak kurulur ve bos acilir.
        applicationId = "com.kefe.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = libs.versions.appVersionCode.get().toInt()
        versionName = libs.versions.appVersionName.get()
    }

    // signingConfigs BILEREK YOK: debug paketi her zaman oldugu gibi
    // ~/.android/debug.keystore ile imzalanir. Telefondaki kurulum ayni imzayla
    // yerinde guncellenir; imza degisirse Android guncellemeyi reddeder ve
    // tek cikis kaldirip kurmak olur (veri gider).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":composeApp"))
    // setContent, enableEdgeToEdge, registerForActivityResult.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    // MainActivity bir FragmentActivity: BiometricPrompt (composeApp'teki
    // BiometricGate) ComponentActivity ile kurulamaz. Surum notu: libs.versions.toml.
    implementation(libs.androidx.fragment)
}

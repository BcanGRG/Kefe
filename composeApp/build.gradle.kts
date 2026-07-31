import java.util.Properties
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.sqldelight)
}

/**
 * Supabase adresi ve yayinlanabilir anahtari KAYNAKTA DURMAZ: local.properties
 * git tarafindan yok sayilir, buradan okunup uretilen tek bir Kotlin nesnesine
 * yazilir. Ortam degiskeni de kabul edilir - CI'da local.properties yoktur.
 *
 * Anahtar zaten istemcide dagitilmak uzere tasarlanmis "publishable" anahtardir;
 * veriyi koruyan sey anahtarin gizliligi degil, tablolarin RLS kurallaridir.
 * Yine de depoya yazmayiz: proje hesabini degistirmek icin kod degistirmek
 * gerekmesin.
 */
val supabaseUrl: String = localOrEnv("SUPABASE_URL")
val supabaseAnonKey: String = localOrEnv("SUPABASE_ANON_KEY")

/**
 * Uygulama surumu TEK YERDE. Hem Android paketine (versionName) hem uretilen
 * SupabaseConfig'e buradan gider - once Ayarlar'daki "Kefe 1.0.4" sabit
 * yaziliydi ve paketin gercek surumuyle (1.0.0) uyusmuyordu.
 */
val appVersionName = "1.0.0"

fun localOrEnv(key: String): String {
    val local = rootProject.file("local.properties")
    if (local.exists()) {
        val props = Properties()
        local.inputStream().use { props.load(it) }
        props.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return System.getenv(key).orEmpty()
}

val generateSupabaseConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/supabase")
    inputs.property("url", supabaseUrl)
    inputs.property("key", supabaseAnonKey)
    inputs.property("version", appVersionName)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("com/kefe/app/data/remote")
        dir.mkdirs()
        dir.resolve("SupabaseConfig.kt").writeText(
            """
            package com.kefe.app.data.remote

            /** Uretilen dosya - elle duzenlemeyin. Kaynak: local.properties + build.gradle. */
            object SupabaseConfig {
                const val Url: String = "$supabaseUrl"
                const val AnonKey: String = "$supabaseAnonKey"
                const val AppVersion: String = "$appVersionName"

                /** Anahtarlar girilmemisse bulut ozellikleri kapali kalir. */
                val isConfigured: Boolean get() = Url.isNotBlank() && AnonKey.isNotBlank()
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    jvm("desktop")

    // iOS hedefleri yalniz macOS'ta yapilandirilir. Kotlin/Native'in Apple arac zinciri
    // Windows/Linux'ta calismaz; kosulsuz tanimlamak bu makinede sync'i kirar.
    // iosMain kaynaklari yerinde durur ve bir Mac'te derlenir.
    if (OperatingSystem.current().isMacOsX) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSupabaseConfig)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.viewmodel.navigation3)
            // navigation3-ui, navigation3-runtime'i gecisli getirir; ayri artifact yok.
            implementation(libs.navigation3.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.lifecycle.runtime.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.biometric)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.android)
        }
        // iosMain kaynak kumesi yalniz Apple hedefleri yapilandirildiginda var olur.
        if (OperatingSystem.current().isMacOsX) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.driver.native)
            }
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.driver.jvm)
        }
        // Depo testleri GERCEK bir veritabanina ihtiyac duyar: mezar tasi ve
        // yeniden hesap kurallari SQL'de yasiyor, sahte bir depoyla dogrulanamaz.
        // JDBC surucusu bellekte calisir, dosya birakmaz.
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(libs.sqldelight.driver.jvm)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Sondalar GERCEK servislere baglanir: normal kosuda calisirlarsa testler
// internete ve ucuncu taraf calisma suresine bagli hale gelir. Elle calistirilir:
//   ./gradlew :composeApp:desktopTest -Pprobe --tests "*LivePriceProbeTest" --rerun -i
//   ./gradlew :composeApp:desktopTest -Pprobe --tests "*RealtimeProbeTest" --rerun -i
//
// -P BAYRAGI SART: Gradle'da excludeTestsMatching, komut satirindaki --tests
// icermesini EZER - yani yalniz --tests yazmak sondayi kosturmaz, sessizce
// hicbir test secmez (isFailOnNoMatchingTests = false oldugu icin de "BUILD
// SUCCESSFUL" der). Bayrak, haric tutmayi yapilandirma aninda kaldirir.
val runProbes = providers.gradleProperty("probe").isPresent
tasks.withType<Test>().configureEach {
    filter {
        isFailOnNoMatchingTests = false
        if (!runProbes) {
            excludeTestsMatching("*LivePriceProbeTest")
            excludeTestsMatching("*RealtimeProbeTest")
        }
    }
}

sqldelight {
    databases {
        create("KefeDatabase") {
            packageName.set("com.kefe.app.db")
            // Sema dosyalari varsayilan konumda: src/commonMain/sqldelight/com/kefe/app/db/
            // Lehce belirtilmiyor; varsayilan sqlite-3-18, minSdk 26 cihazlarin gomulu
            // SQLite surumuyle ayni. Daha yenisini secmek eski telefonlarda calisma
            // aninda patlar (ornegin ON CONFLICT ... DO UPDATE 3.24 ister).
        }
    }
}

android {
    namespace = "com.kefe.app"

    // android.newDsl=false ile AGP 8 DSL'i gecerli (bkz. gradle.properties).
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kefe.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = appVersionName
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
}

compose.desktop {
    application {
        mainClass = "com.kefe.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Kefe"
            packageVersion = "1.0.0"
            description = "Kefe - birikim ve hedef takibi"
            vendor = "Kefe"
        }
    }
}

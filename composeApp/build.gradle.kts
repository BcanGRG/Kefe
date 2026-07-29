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

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
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

// Fiyat sondasi GERCEK servislere baglanir: normal kosuda calisirsa testler
// internete ve ucuncu taraf calisma suresine bagli hale gelir. Elle calistirilir:
//   ./gradlew :composeApp:desktopTest --tests "*LivePriceProbeTest" -i
tasks.withType<Test>().configureEach {
    filter {
        isFailOnNoMatchingTests = false
        excludeTestsMatching("*LivePriceProbeTest")
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
        versionName = "1.0.0"
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

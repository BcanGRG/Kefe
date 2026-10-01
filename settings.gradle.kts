rootProject.name = "Kefe"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// composeApp: ortak kod + masaustu uygulamasi + iOS cercevesi + Android kutuphanesi.
// androidApp: yalniz Android giris noktasi (MainActivity, manifest, ikon, imza, surum).
include(":composeApp")
include(":androidApp")

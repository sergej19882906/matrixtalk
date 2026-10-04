plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.ui)

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

            // Navigation Compose (KMP)
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")

            // Lifecycle ViewModel Compose (KMP)
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

            // Koin
            implementation("io.insert-koin:koin-core:4.0.0")
            implementation("io.insert-koin:koin-compose:4.0.0")
            implementation("io.insert-koin:koin-compose-viewmodel:4.0.0")
            implementation("io.insert-koin:koin-compose-viewmodel-navigation:4.0.0")

            // Trixnity Matrix SDK (4.9.2 is the latest version with persistent repository modules)
            implementation("net.folivo:trixnity-client:4.9.2")
            implementation("net.folivo:trixnity-core:4.9.2")
            implementation("net.folivo:trixnity-client-repository-realm:4.9.2")

            // Ktor (required by Trixnity)
            implementation("io.ktor:ktor-client-core:3.0.1")
            implementation("io.ktor:ktor-client-okhttp:3.0.1")

            // Coil 3 (KMP image loading)
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

            // DataStore Preferences (KMP)
            implementation("androidx.datastore:datastore-preferences:1.1.1")

            // Logging
            implementation("io.github.oshai:kotlin-logging:7.0.3")
        }

        androidMain.dependencies {
            implementation("io.insert-koin:koin-android:4.0.0")
            implementation("io.insert-koin:koin-androidx-compose:4.0.0")
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.15.0")
            implementation("androidx.work:work-runtime-ktx:2.9.1")
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.matrix.messenger.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

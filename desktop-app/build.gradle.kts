import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.runtime)
                implementation(compose.desktop.currentOs)
                implementation("io.insert-koin:koin-core:4.0.0")
                implementation("io.insert-koin:koin-compose:4.0.0")
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.matrix.messenger.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)

            packageName = "MatrixTalk"
            packageVersion = "1.7.0"

            windows {
                menuGroup = "Matrix Talk"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                dirChooser = true
            }

            linux {
                debMaintainer = "matrix-talk@example.com"
                menuGroup = "Network;Chat;"
            }

            macOS {
                bundleID = "com.matrix.messenger"
                dockName = "MatrixTalk"
            }
        }
    }
}

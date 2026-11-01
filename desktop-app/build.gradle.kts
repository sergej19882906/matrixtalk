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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
                implementation("io.insert-koin:koin-core:4.0.0")
                implementation("io.insert-koin:koin-compose:4.0.0")
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                // SLF4J binding: without it Trixnity's internal logs (sync, outbox
                // sender, crypto) are silently discarded ("Defaulting to no-operation"),
                // hiding exactly the errors needed for error.log diagnostics.
                implementation("org.slf4j:slf4j-simple:2.0.13")
            }
        }
    }
}

tasks.register<JavaExec>("loginSmokeTest") {
    group = "verification"
    description = "Temporary smoke test: desktop login path against matrix.org"
    mainClass.set("com.matrix.messenger.LoginSmokeTestKt")
    classpath = configurations["desktopRuntimeClasspath"] + files(tasks.named("desktopJar"))
}

// Debug logs for the E2EE verification flow (slf4j-simple is on the classpath).
tasks.withType<JavaExec>().configureEach {
    jvmArgs(
        "-Dorg.slf4j.simpleLogger.log.net.folivo.trixnity.client.verification=trace",
        "-Dorg.slf4j.simpleLogger.log.net.folivo.trixnity.client.key=debug"
    )
}

// These JVM args apply to the Compose Desktop run task and packaged builds.
compose.desktop.application {
    applicationDefaultJvmArgs = listOf(
        "-Dorg.slf4j.simpleLogger.log.net.folivo.trixnity.client.verification=trace",
        "-Dorg.slf4j.simpleLogger.log.net.folivo.trixnity.client.key=debug"
    )
}

tasks.register<JavaExec>("engineSmokeTest") {
    group = "verification"
    description = "Temporary smoke test: desktop WebRTC engine init + local offer"
    mainClass.set("com.matrix.messenger.EngineSmokeTestKt")
    classpath = configurations["desktopRuntimeClasspath"] + files(tasks.named("desktopJar"))
}

/**
 * Runs the smoke test in a module-limited runtime (no jdk.unsupported ->
 * sun.misc.Unsafe unavailable), reproducing the desktop installer runtime
 * where DataStore's protobuf dependency used to crash session saving.
 */
tasks.register<JavaExec>("loginSmokeTestNoUnsafe") {
    group = "verification"
    description = "Smoke test on a runtime without jdk.unsupported (no sun.misc.Unsafe)"
    mainClass.set("com.matrix.messenger.LoginSmokeTestKt")
    classpath = configurations["desktopRuntimeClasspath"] + files(tasks.named("desktopJar"))
    val currentJavaHome = org.gradle.internal.jvm.Jvm.current().javaHome
    jvmArgs = listOf(
        "--module-path", File(currentJavaHome, "jmods").absolutePath,
        "--limit-modules", "java.base,java.logging,java.net.http,jdk.crypto.ec,jdk.crypto.cryptoki"
    )
}

compose.desktop {
    application {
        mainClass = "com.matrix.messenger.MainKt"

        buildTypes.release.proguard {
            // Keep Coil's network fetcher + ServiceLoader registration,
            // okhttp/okio — without this, packaged builds lose image loading.
            configurationFiles.from(project.file("proguard-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)

            packageName = "MatrixTalk"
            packageVersion = "1.16.0"

            windows {
                menuGroup = "Matrix Talk"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                dirChooser = true
                iconFile.set(project.file("icons/windows.ico"))
            }

            linux {
                debMaintainer = "matrix-talk@example.com"
                menuGroup = "Network;Chat;"
                iconFile.set(project.file("icons/linux.png"))
            }

            macOS {
                bundleID = "com.matrix.messenger"
                dockName = "MatrixTalk"
                iconFile.set(project.file("icons/macos.icns"))
            }
        }
    }
}

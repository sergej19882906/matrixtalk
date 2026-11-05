plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.compose") version "1.7.1" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
}

val verifyReleaseMetadata = tasks.register<Exec>("verifyReleaseMetadata") {
    group = "verification"
    description = "Verify the shared release version, Android code, notes and optional release tag"
    val releaseTag = providers.gradleProperty("releaseTag")
    inputs.file(layout.projectDirectory.file("gradle.properties"))
    inputs.file(layout.projectDirectory.file("RELEASE_NOTES.md"))
    inputs.file(layout.projectDirectory.file("scripts/release_metadata.py"))
    inputs.property("releaseTag", releaseTag.orElse(""))
    val defaultPython = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "python" else "python3"
    val python = providers.environmentVariable("PYTHON").orElse(defaultPython)
    commandLine(python.get(), "-I", "scripts/release_metadata.py", "check", "--root", layout.projectDirectory.asFile.absolutePath)
    if (releaseTag.isPresent) args("--tag", releaseTag.get())
}

subprojects {
    tasks.matching {
        it.name.startsWith("assemble") || it.name.startsWith("package") ||
            it.name == "createDistributable" || it.name == "createReleaseDistributable"
    }.configureEach {
        dependsOn(verifyReleaseMetadata)
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

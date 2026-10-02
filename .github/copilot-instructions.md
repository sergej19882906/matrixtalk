# Copilot instructions

## Build, test, and lint

The Android app is the `:app` Gradle module. Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

The equivalent CI/Linux commands use `./gradlew`. Instrumentation tests require a connected device or emulator. To run one JUnit test, pass its fully qualified class name:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.matrix.messenger.ExampleTest"
```

There are currently no test source files in `app/src/test` or `app/src/androidTest`; use the real test class name when adding tests. The pull-request workflow builds the debug APK and validates both bridge Compose configurations. Tag pushes build and publish the release APK.

## Architecture

- This is a single-module Kotlin Android application (`:app`) using Jetpack Compose, Material 3, Navigation Compose, Hilt, coroutines/Flow, and the Matrix Android SDK.
- `MainActivity` initializes `MatrixRepository` before showing the navigation graph. It uses the current Matrix user to choose the login or home start destination.
- `data/repository/MatrixRepository` is the app-facing contract for authentication, rooms, messages, and profile operations. `MatrixRepositoryImpl` adapts the Matrix SDK session/services and maps SDK data into the app models under `data/model`. `di/AppModule` provides the SDK `Matrix` and repository as application singletons.
- Screens live under `ui/`; screen-specific `@HiltViewModel`s own screen state and invoke repository operations. `AppNavigation` wires screen routes and navigation callbacks. `StateFlow` carries observable state and `UiEvent` flows carry one-off navigation/snackbar events.
- Calling spans `CallRepository` and `CallState`/`CallSession` models, call UI/ViewModels, and the Android `CallService` plus manifest-declared activities/receiver. Changes to call lifecycle or permissions may need coordinated updates across these layers.
- Telegram, WhatsApp, and Signal bridges are server-side Matrix services, not connections implemented by the Android client. The Compose files are an operational starting point; see `docs/bridges.md` and validate them with Docker Compose as in `.github/workflows/android.yml`.

## Repository conventions

- Keep Android code in the `com.matrix.messenger` package, organized by responsibility (`data/model`, `data/repository`, `di`, `receiver`, `service`, and `ui/<feature>`).
- Keep Matrix SDK access behind `MatrixRepository`; expose app-level models and `Flow`s to ViewModels instead of coupling Compose screens to SDK types.
- Follow the existing state/event pattern in feature ViewModels: immutable screen state is exposed as `StateFlow`, while one-off actions use `UiEvent` via a channel-backed flow.
- Hilt is the dependency wiring mechanism: the application is annotated with `@HiltAndroidApp`, Android entry points with `@AndroidEntryPoint`, and injectable ViewModels with `@HiltViewModel`.
- Keep platform component declarations and permissions in `app/src/main/AndroidManifest.xml` aligned with the corresponding service, receiver, and activity implementations.
- Release signing values are loaded from root `local.properties` (`keystore.file`, `keystore.password`, `keystore.alias`, and `key.password`). Do not add signing credentials, access tokens, or generated server configuration to source control.

# Matrix Talk — История версий

## 1.7.1
**Дата релиза:** 2026-10-06

### Исправления (критические)
- 🐛 **Windows/Linux/macOS: приложение не запускалось** — окно создавалось только после асинхронной инициализации, а Compose Desktop завершает процесс, если после стартовой композиции нет ни одного окна; теперь окно создаётся сразу, контент появляется по готовности
- 🐛 **Android: краш при запуске** — дефолтный инициализатор WorkManager был отключён в манифесте, но никто его не инициализировал; `WorkManager.getInstance()` падал в `Application.onCreate`. Добавлен `Configuration.Provider` в `MatrixMessengerApp`

### Сборка
- `versionCode`: 12, `versionName`: 1.7.1, Desktop `packageVersion`: 1.7.1

---

## 1.7.0
**Дата релиза:** 2026-10-05

### 📞 Звонки (VoIP) — Фаза 1: рабочие звонки на Android
- 🆕 **WebRTC-движок** — аудио- и видеозвонки через `m.call.invite/answer/candidates/hangup/reject` (сигналинг Matrix + stream-webrtc-android 1.3.10, Unified Plan)
- 🆕 **TURN/STUN** — учётные данные TURN запрашиваются у хомсервера (`/_matrix/client/v3/voip/turnServer`, кэширование по TTL), STUN-фолбэк; звонки работают через NAT и coturn из нашего Docker-стека
- 🆕 **Кнопки звонка в чате** — аудио/видео вызов из шапки чата с запросом разрешений (микрофон/камера)
- 🆕 **Экран звонка** — входящий вызов (принять/отклонить) из любого места приложения; микрофон, динамик, камера, смена камеры; таймер разговора
- 🆕 **Видео** — удалённое видео на весь экран, локальное превью (PiP) с зеркалированием
- 🆕 **Foreground-сервис** — звонок не убивается системой в фоне (Android 14+: типы microphone/camera/phoneCall)
- 🤝 **Interop с Element** — `party_id` (device id) во всех событиях `m.call.*`, игнорирование событий с чужим `dest_party_id`, поле `invitee` для личных чатов

### Платформы
- Android: полноценные звонки (аудио + видео)
- Desktop (Windows/Linux/macOS): сигналинг и UI звонков есть; WebRTC-медиа — в следующих версиях (заглушка движка)

### Сборка
- `versionCode`: 11, `versionName`: 1.7.0, Desktop `packageVersion`: 1.7.0

---

## 1.6.0
**Дата релиза:** 2026-10-05

### 💬 Мессенджер
- 🆕 **Загрузка истории** — кнопка «Загрузить ранее» в чате
- 🆕 **Реакции** — отображение счётчиков emoji под сообщениями
- 🆕 **Вложения** — изображения показываются прямо в чате, файлы открываются по клику (включая E2E-зашифрованные: расшифровка через MediaService)
- 🆕 **Уведомления на Android** — фоновая синхронизация (WorkManager) и уведомления о новых сообщениях

### ⚡ Производительность
- 🆕 **Персистентный кэш (Realm)** — комнаты, сообщения и E2E-ключи сохраняются между запусками; старт без полной синхронизации (Trixnity 4.9.2 + trixnity-client-repository-realm)

### 📞 Звонки (VoIP) — Фаза 0
- 🆕 **Сигналинг Matrix** — `m.call.invite/answer/candidates/hangup/reject` (VoIP v0): парсинг и отправка через Trixnity; WebRTC-медиа — в следующих версиях

### Сервер (Docker)
- 🆕 **synapse-admin** — веб-интерфейс администрирования сервера (порт 8080, `SYNAPSE_ADMIN_PORT`)

### Исправления
- 🐛 Android: интерфейс больше не заезжает под наэкранные кнопки и вырез под камеру (insets)
- 🐛 Windows: установленная версия не стартовала при недоступной нативной библиотеке Realm — теперь откат на in-memory хранилище + логи в `~/.matrixtalk/logs/error.log`

### Сборка
- `versionCode`: 10, `versionName`: 1.6.0, Desktop `packageVersion`: 1.6.0

---

## 1.5.1
**Дата релиза:** 2026-10-03

### Исправления
- 🐛 **Android: edge-to-edge** — интерфейс больше не заезжает под наэкранные кнопки навигации и вырез под камеру (insets: navigationBars + displayCutout)
- 🐛 **Windows: падение запуска установленной версии** — защитный откат на in-memory хранилище, если нативная библиотека Realm недоступна на платформе; приложение стартует в любом случае
- 🆕 **Desktop: диагностика** — необработанные исключения пишутся в `~/.matrixtalk/logs/error.log`

### Сборка
- `versionCode`: 9, `versionName`: 1.5.1, Desktop `packageVersion`: 1.5.1

---

## 1.5.0
**Дата релиза:** 2026-10-02

### 🚀 Kotlin Multiplatform — единая кодовая база
- Проект мигрирован на **Kotlin Multiplatform**: модули `:shared`, `:app`, `:desktop-app`.
- Весь UI и бизнес-логика (экраны, ViewModel'и, навигация, тема) перенесены в `commonMain` на **Compose Multiplatform**.
- 🆕 **Новый таргет: Desktop (Windows/Linux/macOS)** — модуль `desktop-app` с Compose Window.

### Matrix SDK: Trixnity
- `matrix-android-sdk2` (Android-only) заменён на **Trixnity 4.11** — чисто Kotlin KMP SDK.
- Реализовано: логин (пароль и access token), синхронизация, список комнат, timeline сообщений, отправка текста/файлов/реакций, редактирование и удаление сообщений, typing-индикаторы, read markers, создание/вход/выход из комнат, профиль (имя, аватар).
- 🆕 **Персистентность сессии** — DataStore Preferences (токен, deviceId), автовход при перезапуске приложения.
- Realm удалён из зависимостей (in-memory репозитории Trixnity).

### Архитектура
- Hilt заменён на **Koin 4** — общий DI для всех платформ.
- Платформенные абстракции через expect/actual (файлы, пути хранения, открытие URL).

### CI/CD
- 🆕 **Release workflow** — на тег `v*`: Android APK (signed, если настроены секреты) + Desktop-инсталляторы (MSI/DEB/DMG), GitHub Release с заметками из RELEASE_NOTES.md.
- CI на push/PR в main: сборка Android APK + Desktop, валидация docker-compose.

### Сборка
- `versionCode`: 8, `versionName`: 1.5.0, Desktop `packageVersion`: 1.5.0
- iOS-таргет — в планах.

---

## 1.1.4
**Дата релиза:** 2026-10-01

### Исправления крашей на Xiaomi HyperOS 3/4
- 🐛 **WorkManager double initialization** — Matrix SDK вызывал `WorkManager.initialize()`, но Android уже проинициализировал его через `WorkManagerInitializer`. Отключён дефолтный инициализатор в манифесте.
- 🐛 **Realm decryption failed** — после переустановки с другим signing key файл `matrix-sdk-auth.realm` не расшифровывался. `AppModule.provideMatrix()` теперь ловит исключение, удаляет повреждённые `.realm` файлы и повторяет.
- 🐛 **SuperNotCalledException** — try-catch вокруг `super.onCreate()` в MainActivity глотал исключение Hilt-инъекции до завершения `super.onCreate()`. Обёртка удалена.
- 🐛 **allowBackup="false"** — отключён бэкап для предотвращения восстановления несовместимых Realm файлов после переустановки.

### Совместимость с Android 15/16 (HyperOS 3/4, One UI и др.)
- Устранён конфликт `enableEdgeToEdge()` и `window.statusBarColor` на Android 15+.
- `compileSdk`/`targetSdk` обновлены до 36 (Android 16).
- Удалён SplashScreen compat library (adaptive icon crash на OEM-устройствах).

### Выживание на OEM-оболочках
- Запрос отключения оптимизации батареи (Samsung One UI, Xiaomi HyperOS/MIUI, Huawei EMUI, Oppo ColorOS, OnePlus, Vivo FuntouchOS).
- `BootReceiver` для восстановления после перезагрузки.
- `OemBatteryHelper` — OEM-специфичные экраны настроек.
- Централизованные каналы уведомлений (звонки, сообщения, синхронизация).

### Foreground Service
- Проверка разрешений camera/microphone перед стартом с соответствующими типами.
- Обработка `SecurityException` при запуске foreground service.

### Сервер (Docker)
- 🆕 **Coturn** — TURN/STUN сервер для VoIP-звонков (без него звонки работают только в LAN).
- 🆕 **Кастомные Docker-образы** с авто-конфигурацией из env-переменных — Synapse и мосты настраиваются автоматически при первом запуске, ручная генерация и редактирование конфигов не требуется.
- 🆕 **ghcr.io** — образы публикуются в GitHub Container Registry (`ghcr.io/sergej19882906/matrix-talk-*`), поддерживаются `linux/amd64` и `linux/arm64`.
- 🆕 **init-bridges-db** — однократный сервис для автоматического создания баз данных мостов в PostgreSQL.
- Конфиги мостов маунтятся `:ro` в контейнер Synapse для appservice registration.
- Полная инструкция по установке сервера в `docs/bridges.md` (на русском).
- `.env.example` расширен: `TURN_SHARED_SECRET`, `SYNAPSE_REGISTRATION_SHARED_SECRET`, `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`, `BRIDGE_ADMIN`, порты Coturn.

### Очистка зависимостей
- Удалена `security-crypto:1.1.0-alpha06` (alpha, не использовалась).
- Удалён `room-runtime`/`room-ktx`/`room-compiler` (нет Entity/DAO в проекте).
- Обновлены: `core-ktx 1.15.0`, `lifecycle 2.8.7`, `activity-compose 1.9.3`, `compose-bom 2024.12.01`, `navigation-compose 2.8.5`.

### CI/CD
- GitHub Actions собирает signed release APK из секретов.
- Debug-сборка на pull request.
- Валидация `docker-compose.bridges.yml` и ARM64 override.
- 🆕 **Docker CI** — автоматическая сборка и пуш кастомных образов (Synapse + 3 моста) в ghcr.io для `linux/amd64` + `linux/arm64` при пуше в main и при создании тега `v*`.
- `.dockerignore` добавлен.

### Сборка
- `versionCode`: 6
- `versionName`: 1.1.4
- Минимальная версия Android: API 24
- Целевая версия Android: API 36

---

## 1.1.3
**Дата релиза:** 2026-09-19

### Что нового
- 🚀 **CI/CD Автоматизация**:
  - Исправлены права доступа `GITHUB_TOKEN` в GitHub Actions.
  - Реализована автоматическая сборка и публикация Debug APK в разделе Releases при создании тега версии (`v*`).

### Сборка
- `versionCode`: 5
- `versionName`: 1.1.3
- Минимальная версия Android: API 24
- Целевая версия Android: API 35

---

## 1.1.2
**Дата релиза:** 2026-09-19

### Что нового
- 🛠 **Исправление критических ошибок сборки**:
  - Решена проблема с дублированием файлов `ChatScreen.kt`, приводившая к конфликтам компиляции.
  - Полностью исправлена типизация и работа с состоянием в `ChatScreen` (интеграция с `ChatViewModel`).
  - Исправлены ошибки в `CallService` для корректной работы Foreground Service на Android 14+.
- 🏗 **Рефакторинг архитектуры DI**:
  - Исправлена ошибка `MissingBinding` для `Session` в Hilt.
  - `CallRepository` переведен на получение сессии через `MatrixRepository`, что устранило сбои при инициализации зависимостей.
- 🚀 **Стабильность**: Проект теперь успешно проходит полную сборку (`assembleDebug`).

### Сборка
- `versionCode`: 4
- `versionName`: 1.1.2
- Минимальная версия Android: API 24
- Целевая версия Android: API 35

---

## 1.1.1
**Дата релиза:** 2026-09-18

### Что нового
- 🆕 **Аудио-видео звонки** — полноценная поддержка VoIP:
  - Исходящие и входящие аудио- и видеозвонки
  - Экран входящего звонка поверх заблокированного экрана
  - Управление микрофоном, камерой, переключение фронтальной/задней камеры
  - Foreground Service для поддержания звонка в фоне
  - Уведомления с кнопками «Принять», «Отклонить», «Завершить»
  - Таймер длительности звонка и анимированный UI
- 🆕 **Улучшенная передача файлов**:
  - Предпросмотр видео в чате (Coil Video)
  - Прогресс-бар загрузки/скачивания файлов
- 🆕 **Новые компоненты**:
  - `CallActivity` и `IncomingCallActivity`
  - `CallService` (Foreground Service для Android 14+)
  - `CallScreen`, `CallViewModel`, `CallRepository`, `CallState`
- 🆕 **Новые зависимости**:
  - `io.getstream:stream-webrtc-android:1.0.0`
  - `com.google.accompanist:accompanist-permissions:0.32.0`
  - `io.coil-kt:coil-video:2.7.0`
- 🔧 Обновлён `AndroidManifest.xml`:
  - Добавлены разрешения для камеры, микрофона, Bluetooth и Foreground Service
  - `tools:targetApi` обновлён до 34

### Сборка
- `versionCode`: 3
- `versionName`: 1.1.1
- Минимальная версия Android: API 24
- Целевая версия Android: API 35

---

## 1.1
**Дата релиза:** 2026-09-12

### Что нового
- Добавлена настройка мостов Telegram, WhatsApp и Signal через Matrix homeserver.
- Добавлена поддержка ARM64-конфигурации Docker Compose для серверной заготовки мостов.
- Обновлён Matrix Android SDK до версии 1.6.62.
- Добавлены экраны и сценарии для входа, списка комнат, чатов и обмена сообщениями.
- Добавлена отправка изображений, видео, аудио и файлов.
- Добавлены реакции, редактирование и удаление сообщений.
- Добавлена поддержка прямых и групповых комнат, нескольких серверов и тёмной темы.

### Сборка
- `versionCode`: 2
- `versionName`: 1.1

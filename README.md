# Matrix Talk for Android

Android-клиент Matrix Talk для децентрализованной сети Matrix, написанный на Kotlin с использованием Jetpack Compose.

## Возможности

### 💬 Мессенджер
- ✅ Вход в аккаунт Matrix по логину и паролю
- ✅ Вход по Matrix access token
- ✅ Список чатов/комнат
- ✅ Отправка текстовых сообщений
- ✅ Отправка изображений, видео, аудио и файлов
- ✅ Индикатор набора текста
- ✅ Реакции на сообщения
- ✅ Редактирование и удаление сообщений
- ✅ Изменение display name и аватара
- ✅ Прямые чаты (DM) и групповые комнаты
- ✅ Поддержка нескольких серверов
- ✅ Material Design 3 и тёмная тема

### 📞 Аудио-видео звонки (VoIP)
- ✅ Исходящие и входящие аудио- и видеозвонки
- ✅ Полноэкранный входящий звонок поверх заблокированного экрана
- ✅ Управление микрофоном (mute/unmute) и камерой (вкл/выкл)
- ✅ Переключение фронтальной/задней камеры и динамика
- ✅ Foreground Service для поддержания звонка в фоне
- ✅ Уведомления с кнопками «Принять», «Отклонить», «Завершить»
- ✅ Таймер длительности звонка и анимированный UI

### 📁 Передача файлов
- ✅ Предпросмотр видео в чате
- ✅ Прогресс-бар загрузки/скачивания файлов

### 🔋 Совместимость с OEM-оболочками
- ✅ Запрос отключения оптимизации батареи (Android 14+)
- ✅ Поддержка Samsung One UI, Xiaomi HyperOS/MIUI, Huawei EMUI, Oppo ColorOS, OnePlus, Vivo FuntouchOS
- ✅ Автовосстановление после перезагрузки устройства (BootReceiver)

### 🔗 Мосты Telegram, WhatsApp и Signal
- ✅ Экран настройки мостов в приложении
- ✅ Серверная заготовка: Synapse + PostgreSQL + Coturn + mautrix-мосты
- ✅ Полная инструкция по установке сервера в `docs/bridges.md`

## Сервер

`docker-compose.bridges.yml` — готовый стек для собственного Matrix-сервера.
Кастомные Docker-образы с авто-конфигурацией из env-переменных публикуются в [ghcr.io](https://github.com/sergej19882906/matrixtalk/pkgs/container/).

| Сервис | Образ | Назначение |
|--------|-------|------------|
| **Synapse** | `ghcr.io/sergej19882906/matrixtalk-synapse` | Matrix homeserver |
| **PostgreSQL** | `postgres:16-alpine` | База данных |
| **Coturn** | `coturn/coturn:latest` | TURN/STUN сервер для VoIP-звонков |
| **mautrix-telegram** | `ghcr.io/sergej19882906/matrixtalk-mautrix-telegram` | Мост Telegram |
| **mautrix-whatsapp** | `ghcr.io/sergej19882906/matrixtalk-mautrix-whatsapp` | Мост WhatsApp |
| **mautrix-signal** | `ghcr.io/sergej19882906/matrixtalk-mautrix-signal` | Мост Signal |

Пошаговая инструкция по установке и настройке — в [`docs/bridges.md`](docs/bridges.md).

## Скриншоты интерфейса

<p>
  <img src="docs/screenshots/login.svg" alt="Экран входа" width="220">
  <img src="docs/screenshots/home.svg" alt="Список чатов" width="220">
  <img src="docs/screenshots/chat.svg" alt="Экран чата" width="220">
</p>

## Технологии

| Компонент | Технология |
|-----------|-----------|
| **Язык** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Архитектура** | MVVM + Clean Architecture |
| **DI** | Hilt |
| **Matrix SDK** | matrix-android-sdk2 1.6.62 |
| **Звонки** | WebRTC (stream-webrtc-android 1.0.0) |
| **Изображения/Видео** | Coil 2.7.0 + Coil Video |
| **Разрешения** | Accompanist Permissions 0.32.0 |
| **Хранилище** | DataStore Preferences 1.1.1 |

## Структура проекта

<pre>
app/
├── src/main/
│   ├── java/com/matrix/messenger/
│   │   ├── data/
│   │   │   ├── model/          # Модели данных (Message, CallState, CallSession)
│   │   │   └── repository/     # Репозитории (MatrixRepository, CallRepository)
│   │   ├── di/                 # Dependency Injection (Hilt modules)
│   │   ├── receiver/           # BootReceiver, OemBatteryHelper, NotificationChannels
│   │   ├── service/            # Foreground Services (CallService)
│   │   └── ui/
│   │       ├── bridges/        # Управление мостами
│   │       ├── call/           # Экраны звонков (CallScreen, CallActivity, IncomingCallActivity, CallViewModel)
│   │       ├── chat/           # Экран чата
│   │       ├── home/           # Список чатов
│   │       ├── login/          # Экран входа
│   │       ├── navigation/     # Навигация
│   │       └── theme/          # Тема
│   └── res/                    # Ресурсы Android
</pre>

## Сборка и запуск

### Требования
- Android Studio Ladybug (2024.2.1) или новее
- JDK 17
- Android SDK 36

### Шаги
1. Откройте проект в Android Studio и дождитесь синхронизации Gradle.
2. Запустите на эмуляторе или устройстве.

**CI/CD:**
При создании тега версии (например, `v1.1.4`) GitHub Actions автоматически собирает подписанный Release APK и публикует релиз. На pull request запускается debug-сборка и валидация docker-compose.

**Команды сборки:**
```bash
# Windows
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease

# Linux/macOS
chmod +x gradlew
./gradlew assembleDebug
./gradlew assembleRelease
```

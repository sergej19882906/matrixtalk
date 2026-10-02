# Инструкция по установке и настройке сервера Matrix Talk

Полное руководство по развёртыванию собственного Matrix-сервера с поддержкой
мессенджера, аудио/видео звонков и опциональных мостов Telegram, WhatsApp и Signal.

Кастомные Docker-образы с авто-конфигурацией публикуются в
[ghcr.io](https://github.com/sergej19882906/matrix-talk-android/pkgs/container/)
— **не нужно вручную генерировать и редактировать конфиги.**

---

## Что входит в сервер

`docker-compose.bridges.yml` определяет 7 сервисов:

| Сервис | Образ | Назначение |
|--------|-------|------------|
| **Synapse** | `ghcr.io/sergej19882906/matrix-talk-synapse` | Matrix homeserver — ядро мессенджера |
| **PostgreSQL** | `postgres:16-alpine` | База данных Synapse + мосты |
| **init-bridges-db** | `postgres:16-alpine` | Создание БД для мостов (однократный) |
| **Coturn** | `coturn/coturn:latest` | TURN/STUN сервер для VoIP-звонков |
| **mautrix-telegram** | `ghcr.io/sergej19882906/matrix-talk-mautrix-telegram` | Мост Telegram |
| **mautrix-whatsapp** | `ghcr.io/sergej19882906/matrix-talk-mautrix-whatsapp` | Мост WhatsApp |
| **mautrix-signal** | `ghcr.io/sergej19882906/matrix-talk-mautrix-signal` | Мост Signal |

Кастомные образы автоматически:
- Генерируют `homeserver.yaml` / `config.yaml` при первом запуске
- Настраивают PostgreSQL, TURN и мосты из переменных окружения
- Создают `registration.yaml` для мостов
- Добавляют регистрации мостов в конфиг Synapse

Мосты используют профиль Compose `bridges`, чтобы Synapse запускался первым.

---

## Требования

- Docker Engine 20.10+ и Docker Compose v2+
- Доменное имя с DNS A-записью, указывающей на ваш сервер
- Открытые порты (см. раздел «Порты» ниже)

---

## Шаг 1. Настройка переменных окружения

```bash
cp .env.example .env
```

Отредактируйте `.env`:

```ini
# === Matrix Talk Server Configuration ===

# --- Homeserver ---
# Ваш домен (должен совпадать с DNS / обратным прокси)
MATRIX_SERVER_NAME=matrix.example.org

# --- PostgreSQL ---
POSTGRES_USER=synapse
POSTGRES_PASSWORD=<сгенерируйте длинный случайный пароль>
POSTGRES_DB=synapse

# --- TURN Server (VoIP звонки) ---
# Общий секрет для TURN-аутентификации
# Генерация: openssl rand -hex 32
TURN_SHARED_SECRET=<сгенерируйте случайный секрет>
# UDP-порт TURN/STUN
TURN_PORT=3478
# TLS-порт (требует сертификаты в server-data/coturn/)
TURN_TLS_PORT=5349
# Диапазон портов для медиа-релея
TURN_MIN_PORT=49152
TURN_MAX_PORT=65535

# --- Synapse Admin ---
# Секрет для скрипта register_new_matrix_user
# Генерация: openssl rand -hex 32
SYNAPSE_REGISTRATION_SHARED_SECRET=<сгенерируйте случайный секрет>

# --- Bridge Configuration ---
# Telegram API credentials (обязательно для моста Telegram)
# Получите на https://my.telegram.org/apps
TELEGRAM_API_ID=
TELEGRAM_API_HASH=

# Bridge admin MXID (опционально, по умолчанию @admin:MATRIX_SERVER_NAME)
BRIDGE_ADMIN=
```

> **⚠️ Никогда не коммитьте `.env` в git.** Файл добавлен в `.gitignore`.

---

## Шаг 2. Запуск основных сервисов

```bash
docker compose -f docker-compose.bridges.yml up -d synapse postgres coturn
```

При первом запуске кастомный образ Synapse:
1. Генерирует `homeserver.yaml` с вашим `MATRIX_SERVER_NAME`
2. Настраивает подключение к PostgreSQL
3. Добавляет TURN-конфигурацию (если `TURN_SHARED_SECRET` задан)

Дождитесь запуска (Synapse станет `healthy`):

```bash
docker compose -f docker-compose.bridges.yml ps
```

### Проверка

```bash
curl http://localhost:8008/_matrix/client/versions
```

Должен вернуть JSON с версиями API (v1.1–v1.12).

---

## Шаг 3. Создание администратора

```bash
docker exec -it matrix-talk-synapse register_new_matrix_user \
  -c /data/homeserver.yaml \
  --admin \
  --password-prompt \
  @admin:matrix.example.org
```

Введите пароль при запросе. Этот аккаунт будет использоваться для входа в
приложение Matrix Talk.

---

## Шаг 4. Порты фаервола

Откройте на сервере следующие порты:

| Порт | Протокол | Сервис | Назначение |
|------|----------|--------|------------|
| 8008 | TCP | Synapse | Client API + Federation API |
| 8448 | TCP | Synapse | Federation (если прямой TLS) |
| 3478 | UDP | Coturn | TURN/STUN |
| 5349 | TCP | Coturn | TURN over TLS (если есть сертификаты) |
| 49152–65535 | UDP | Coturn | Медиа-релей для VoIP |

---

## Шаг 5. Мосты Telegram / WhatsApp / Signal (опционально)

### 5.1. Запуск мостов

```bash
docker compose -f docker-compose.bridges.yml --profile bridges up -d
```

Кастомные образы мостов автоматически:
1. Генерируют `config.yaml` с настройками из `.env`
2. Настраивают адрес homeserver, домен, базу данных и права
3. Создают `registration.yaml`
4. Сервис `init-bridges-db` создаёт базы данных `mautrix_telegram`,
   `mautrix_whatsapp`, `mautrix_signal` в PostgreSQL

### 5.2. Перезапуск Synapse для подключения мостов

После первого запуска мостов перезапустите Synapse, чтобы он подхватил
файлы регистрации:

```bash
docker compose -f docker-compose.bridges.yml restart synapse
```

### 5.3. Проверка

```bash
docker compose -f docker-compose.bridges.yml --profile bridges ps
```

Все контейнеры должны быть `Up` (не `Restarting`).

> **⚠️ Telegram мост:** требуется указать `TELEGRAM_API_ID` и `TELEGRAM_API_HASH`
> в `.env`. Получите их на https://my.telegram.org/apps (войдите → API development
> tools). Без них мост не запустится.

> **⚠️ Права файлов:** если мосты падают с `Permission denied`, выполните:
> ```bash
> docker run --rm -v "$PWD/server-data:/server-data" alpine \
>   chmod -R 777 /server-data/mautrix-telegram /server-data/mautrix-whatsapp /server-data/mautrix-signal
> ```

### 5.4. Привязка аккаунтов

В Matrix Talk откройте экран «Мосты» (иконка ссылки на экране чатов) и
следуйте инструкции:

- **Telegram:** отправьте `!tg login` в комнату моста, введите номер телефона
- **WhatsApp:** отправьте `!wa login` и отсканируйте QR-код
- **Signal:** отправьте `!signal login` и привяжите устройство

> Подробные инструкции: [mautrix-telegram](https://docs.mau.fi/bridges/go/telegram/),
> [mautrix-whatsapp](https://docs.mau.fi/bridges/go/whatsapp/),
> [mautrix-signal](https://docs.mau.fi/bridges/go/signal/)

---

## ARM64 (Raspberry Pi и др.)

Кастомные образы собираются для `linux/amd64` и `linux/arm64` в CI
и публикуются в ghcr.io. Добавьте файл переопределения для PostgreSQL и Coturn:

```bash
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml up -d synapse postgres coturn
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml --profile bridges up -d
```

---

## Локальная сборка образов (без ghcr.io)

Если вы хотите собрать образы локально вместо пулла из ghcr.io:

```bash
docker compose -f docker-compose.bridges.yml build
```

Или отдельный образ:

```bash
docker compose -f docker-compose.bridges.yml build synapse
```

---

## Продакшен

### HTTPS / обратный прокси

Рекомендуется **Caddy** — автоматический TLS:

```
matrix.example.org {
    reverse_proxy localhost:8008
}
```

Или **nginx**:

```nginx
server {
    listen 443 ssl;
    server_name matrix.example.org;

    ssl_certificate /etc/ssl/certs/matrix.example.org.crt;
    ssl_certificate_key /etc/ssl/private/matrix.example.org.key;

    location / {
        proxy_pass http://localhost:8008;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
}
```

### Федерация

Для федерации с другими серверами Matrix создайте `.well-known`:

`https://matrix.example.org/.well-known/matrix/server`:
```json
{"m.server": "matrix.example.org:443"}
```

`https://matrix.example.org/.well-known/matrix/client`:
```json
{"m.homeserver": {"base_url": "https://matrix.example.org"}}
```

### Coturn с TLS

Поместите `cert.pem` и `key.pem` в `server-data/coturn/` и добавьте в
`docker-compose.bridges.yml` параметры Coturn:

```yaml
command:
  - "--cert=/etc/coturn/cert.pem"
  - "--pkey=/etc/coturn/key.pem"
```

### Бэкапы

Регулярно бэкапьте:
- `server-data/postgres/` — база данных
- `server-data/synapse/` — конфигурация, ключи подписи, медиа

### Рекомендации

- **Пиньте версии** — замените `latest` на конкретные теги образов
- **Не коммитьте** `.env`, токены доступа, базы мостов, файлы регистрации,
  ключи шифрования
- **Ограничьте права** мостов минимально необходимыми
- **Настройте мониторинг** логов Synapse и Coturn

---

## Подключение из Matrix Talk

1. Откройте приложение
2. На экране входа укажите адрес homeserver: `https://matrix.example.org`
3. Введите логин и пароль администратора (шаг 3)
4. Для настройки мостов — нажмите иконку ссылки на экране чатов

VoIP-звонки используют WebRTC через настроенный TURN-сервер. Приложение
автоматически запрашивает TURN-учётные данные у Synapse при звонке.

# Сервер MatrixTalk: настройка и границы поддержки

Стек запускает Synapse, PostgreSQL, Coturn и synapse-admin. **Мосты и ntfy
опциональны.** Их наличие в Compose не означает готовность к работе.
Автогенерация конфигурации гарантируется только для Synapse. Конфигурации мостов
зависят от версии upstream и должны быть подготовлены оператором; wrapper
завершится с ошибкой, если необходимые файлы отсутствуют, а не имитирует успех.

## Базовый сервер

1. Скопируйте `.env.example` в `.env`, задайте домен, пароль PostgreSQL,
   `TURN_SHARED_SECRET` и `SYNAPSE_REGISTRATION_SHARED_SECRET`. Генерируйте секреты
   самостоятельно, например `openssl rand -hex 32`; никогда не коммитьте `.env`.
2. Закрепите проверенные теги или digest образов вместо `latest`. Изменения wrapper
   применяются только после сборки локальных образов либо публикации новых.
3. Запустите сервер:

```bash
docker compose -f docker-compose.bridges.yml build synapse
docker compose -f docker-compose.bridges.yml up -d postgres synapse coturn synapse-admin
curl http://localhost:8008/_matrix/client/versions
```

Synapse получает PostgreSQL, TURN и registration shared secret из окружения.
Администратор создаётся через штатный `register_new_matrix_user` в контейнере
`matrixtalk-synapse` с конфигурацией `/data/homeserver.yaml` и интерактивным
вводом пароля. synapse-admin слушает порт `SYNAPSE_ADMIN_PORT` (8080).
Поставьте HTTP-сервисы за HTTPS reverse proxy; ограничьте административный порт.
Coturn использует host networking: откройте 3478 UDP/TCP и выбранный диапазон
UDP relay (49152–65535). TLS TURN требует настоящих сертификатов и настройки
`--cert`/`--pkey`, один порт 5349 не включает работоспособный TLS.

## Восемь независимых мостов

| Сервис Compose | Профиль | Каталог на хосте | Файлы, обязательные перед запуском |
|---|---|---|---|
| `telegram-bridge` | `bridge-telegram` | `server-data/mautrix-telegram` | `config.yaml`, `registration.yaml` |
| `whatsapp-bridge` | `bridge-whatsapp` | `server-data/mautrix-whatsapp` | `config.yaml`, `registration.yaml` |
| `signal-bridge` | `bridge-signal` | `server-data/mautrix-signal` | `config.yaml`, `registration.yaml` |
| `mautrix-meta-bridge` | `bridge-meta` | `server-data/mautrix-meta` | `config.yaml`, `registration.yaml` |
| `mautrix-gmessages-bridge` | `bridge-gmessages` | `server-data/mautrix-gmessages` | `config.yaml`, `registration.yaml` |
| `mautrix-discord-bridge` | `bridge-discord` | `server-data/mautrix-discord` | `config.yaml`, `registration.yaml` |
| `heisenbridge` | `bridge-irc` | `server-data/heisenbridge` | `registration.yaml` |
| `hookshot` | `bridge-hookshot` | `server-data/hookshot` | `config.yml`, `registration.yml`, `passkey.pem` |

Не используйте прежний профиль `bridges`: он запускал всё одновременно даже без
настроек. Выбирайте только нужные профили. Настройки `HOMESERVER_*`, `BRIDGE_ADMIN`
и Telegram API в `.env` **не заменяют конфигурацию mautrix**: wrapper намеренно
не переписывает пользовательский YAML через sed и не угадывает формат версии.

### Подготовка mautrix

Для выбранного, закреплённого образа следуйте [upstream Docker setup]
(https://docs.mau.fi/bridges/general/docker-setup.html) и [регистрации appservice]
(https://docs.mau.fi/bridges/general/registering-appservices.html).
Используйте upstream image отдельно от нашего fail-fast wrapper для подготовки
шаблона и регистрации на выделенном каталоге. **Не запускайте preparation в
рабочем контейнере с production-данными.** Upstream `/docker-run.sh` создаёт
шаблон при отсутствии config и завершает запуск; после редактирования он
генерирует registration и снова завершается. Это этап подготовки, не healthy
bridge. Не пересоздавайте токены существующей регистрации.

Перед первым рабочим запуском проверьте:

- homeserver URL внутри Docker — `http://synapse:8008`, домен — ваш Matrix domain;
- appservice URL в регистрации доступен Synapse по **имени сервиса Compose**, а не
  localhost; listener мостa привязан к `0.0.0.0` и правильному порту;
- PostgreSQL URI использует `postgres` и одну из БД `mautrix_telegram`,
  `mautrix_whatsapp`, `mautrix_signal`, `mautrix_meta`, `mautrix_gmessages`,
  `mautrix_discord`; корректно URL-encode специальные символы пароля;
- разрешения содержат ваш административный MXID и минимально необходимые права;
- Telegram credentials заполнены в config; для остальных мостов завершена
  версия-зависимая привязка аккаунта согласно upstream документации.

`init-bridges-db` создаёт все шесть БД идемпотентно, но не скрывает ошибки
авторизации/SQL. Для нового PostgreSQL эти же БД создаёт init script.

### IRC и Hookshot

Для heisenbridge используйте штатный CLI закреплённой версии для генерации
`registration.yaml` (`--generate -c ...`), затем настройте URL регистрации на
`http://heisenbridge:9898`. Укажите `BRIDGE_ADMIN` — полный MXID владельца.
Wrapper запускает официальный `heisenbridge` с `-o` (owner), `-l 0.0.0.0`,
`-c /data/registration.yaml` и полным positional homeserver URL.

Hookshot требует подготовленные config, registration и RSA passkey по
[upstream setup](https://matrix-org.github.io/matrix-hookshot/latest/setup.html).
Настройте `passFile: /data/passkey.pem`, внутренние listener/registration URL,
права и только нужные интеграции. Wrapper использует upstream container launch
`/bin/matrix-hookshot/App/BridgeApp.js`, не выдуманный путь source checkout.
Публикация webhook listener наружу и HTTPS — отдельная настройка оператора.

### Запуск и регистрация в Synapse

```bash
docker compose -f docker-compose.bridges.yml build whatsapp-bridge
docker compose -f docker-compose.bridges.yml --profile bridge-whatsapp up -d whatsapp-bridge
docker compose -f docker-compose.bridges.yml restart synapse
```

Все восемь каталогов смонтированы в Synapse read-only. На старте scanner ищет
`registration.yaml` или `registration.yml`, проверяет обязательные поля и
добавляет найденные файлы к `app_service_config_files`. **Пользовательские
appservice entries сохраняются**, повторный запуск не дублирует записи.
Две регистрации разных расширений в одном каталоге — ошибка, а не выбор наугад.
Перезапускайте Synapse после появления или изменения регистрации. Чтобы удалить
мост, остановите его и явно удалите его entry из homeserver config; scanner не
удаляет пользовательские entries автоматически.

Не используйте `chmod -R 777`. Определите UID/GID закреплённого upstream image,
дайте минимальные права на его каталог и возможность Synapse читать registration.
Токены регистрации, passkeys, bridge databases и пароли — секреты; защищайте бэкапы.

## Мгновенный push: только настроенный ntfy

**Произвольные UnifiedPush/NextPush endpoints не поддерживаются.** Пустая
настройка означает unavailable и периодический fallback, а не зелёный push status.

1. Задайте `NTFY_BASE_URL=https://ntfy.your-domain.example` (HTTPS origin без
   subpath/query) и `NTFY_PORT=2586` в `.env`.
2. Запустите `docker compose -f docker-compose.bridges.yml --profile push up -d ntfy`.
   Настройте HTTPS reverse proxy на 2586; проверьте `/v1/health` и GET
   `https://ntfy.your-domain.example/_matrix/push/v1/notify` (discovery должен
   вернуть `{"unifiedpush":{"gateway":"matrix"}}`).
3. При сборке Android APK задайте `-PntfyBaseUrl=https://ntfy.your-domain.example`.
   Manifest placeholder `ntfyBaseUrl` заполняет metadata
   `com.matrix.messenger.NTFY_BASE_URL` в **тот же origin**. По умолчанию
   property пустое: unsupported push выключен до конфигурации.
   `.env` сервера не конфигурирует APK автоматически.
4. Установите ntfy (`io.heckel.ntfy`), выберите тот же default server, включите
   уведомления MatrixTalk и дайте системное разрешение.

ntfy endpoint вида `https://ntfy.your-domain.example/up<secret>?up=1` становится
**pushkey**, а gateway URL — `https://ntfy.your-domain.example/_matrix/push/v1/notify`.
Это разные значения. Matrix HTTP pusher использует `kind=http`,
`data.format=event_id_only`, `append=false`; содержимое сообщения не отправляется
в gateway. Gateway ntfy требует совпадение pushkey с `NTFY_BASE_URL` и пересылает
Matrix JSON как UnifiedPush bytes. См. [официальный gateway protocol]
(https://unifiedpush.org/developers/gateway/) и [реализацию ntfy]
(https://github.com/binwiederhier/ntfy/blob/main/server/server_matrix.go).

Exported receiver оставляет SDK token/instance validation и проверяет выбранный
установленный ntfy distributor; payload служит только подсказкой room/event.
Worker получает и расшифровывает событие через один repository-owned client,
проверяет собственные/прочитанные сообщения, toggle и room notification level.
Дедуп фиксируется после успешного показа, не до decryption.

Статусы: **unavailable**, **disabled**, **pending**, **failed**, **active**.
`active` только после успешного ответа homeserver на регистрацию pusher, не при
получении endpoint; это не гарантия end-to-end доставки. При disable/logout или
unregistration серверный pusher удаляется. Неудачная очистка сохраняется как
pending removal, а не объявляется успешной; при недоступном сервере и окончательном
logout оператор может удалить оставшийся pusher через homeserver/device management.
Fallback WorkManager — минимум 15 минут, Android/Doze может отложить его; это
не мгновенная доставка. Force-stop приложения блокирует фон до следующего открытия.

## ARM64 и проверка

`docker-compose.arm64.yml` задаёт платформу, но **не гарантирует**, что любой
upstream tag имеет ARM64 manifest. Проверяйте закреплённые образы перед deployment.

```bash
docker compose -f docker-compose.bridges.yml -f docker-compose.arm64.yml config
```

Изолированные regression tests лежат в `docker/tests/test_configuration.py` и
`shared/src/commonTest/kotlin/com/matrix/messenger/data/push/PushProtocolTest.kt`.
Они используют mock config/токены и не запускают production containers.

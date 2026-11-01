# Вторая волна редизайна + аудит аватаров (v1.16)

## Контекст

Первая волна (v1.15.0) перестроила главные экраны. Вторая — второстепенные экраны. Параллельный аудит «отображения аватаров» нашёл **6 реальных багов рендеринга**, самый серьёзный: незашифрованные картинки в чате и галерее, а также аватар профиля в настройках **никогда не отображаются** — фабрика Coil возвращает `null` для `MatrixMedia(url, null)`, но coil3 не «проваливает» запрос к сетевой фабрике для чужого типа данных (та регистрируется на `Uri`, а не `MatrixMedia`) → «Unable to create a fetcher». Работают только незашифрованные `String`-модели (аватары списков) и зашифрованные вложения.

## Часть A — фиксы аватаров/медиа

1. **`media/MediaFetcher.kt`** (одна точка, чинит сразу 3 места): в `MatrixMediaFetcher.Factory.create` для `encryptedFile == null` явно строить сетевой fetcher: `NetworkFetcher.Factory().create(url.toUri(), options, imageLoader)` (импорт `coil3.network.NetworkFetcher`, `coil3.toUri`; coil-network-core уже в classpath через coil-network-okhttp).
2. **Аватар комнаты в шапке чата**: `ChatUiState` + `roomAvatarUrl`; `ChatViewModel.observeMessages` (сейчас отбрасывает `getRoomInfo().avatarUrl`); `ChatScreen` TopAppBar — `MatrixAvatar(avatarUrl = uiState.roomAvatarUrl)`.
3. **Аватары отправителей в ленте**: `Message.senderAvatarUrl: String?`; в `MatrixRepositoryImpl` рядом с `userNameCache` добавить кэш аватаров (`resolveSenderAvatar` через `c.user.getById(roomId, sender).avatarUrl` + `mxcToHttp`, с таймаутом как у имени); прокинуть в `mapToMessage`; `ChatScreen.MessageItem` — передавать в MatrixAvatar (36dp, CircleShape).
4. **DM-фоллбэк в списке чатов**: `toChatRoom` — если у комнаты нет `m.room.avatar`, а это личный чат, взять аватар hero (собеседника) через `c.user.getById`.
5. **Аватары контактов**: `searchUsers` отдаёт сырой `mxc://` → обернуть `mxcToHttp` (сейчас Coil не может загрузить).
6. **Дедупликация**: инлайновые аватары `SettingsScreen` (профиль, 56dp) и `RoomsCatalogScreen.PublicRoomRow` (44dp) → `MatrixAvatar`; в диалоге «Профиль» на `HomeScreen` — MatrixAvatar вместо голого AsyncImage (буква-фоллбэк).

## Часть B — второстепенные экраны (в стиль Element X)

Референс-паттерн: TopAppBar + строки на `surfaceContainerLow`, поля с `shapes.extraLarge`, иконки/виджеты из общего набора.

- **SettingsScreen**: MatrixAvatar в профиле, заголовки секций `labelMedium`/`onSurfaceVariant`, выравнивание TopAppBar с Home/Chat.
- **DevicesScreen**: строки устройств на `surfaceContainerLow`; `formatSeen` частично на `TimeFormat`; диалоги получают новый вид бесплатно (shapes).
- **SearchScreen**: поле поиска — скруглённый pill (как в Home), строки с Surface + `TimeFormat` вместо локального `formatSearchTime`.
- **RoomsCatalogScreen**: MatrixAvatar, «Вступить» → TextButton, строки на `surfaceContainerLow`.
- **SpaceChildrenScreen**: извлечь композабл строки, добавить MatrixAvatar (буквенный), «Открыть» → TextButton.
- **BridgesScreen**: лёгкая полировка Card (цвет `surfaceContainerLow`).
- **LoginScreen**: лёгкая полировка — скруглённые поля (`shapes.extraLarge`), брендовый блок; Scaffold не добавляем.

**Не трогаем**: CallScreen (намеренно самостоятельный оверлей с своими цветами), аватары в результатах поиска (данных нет в `SearchResultItem` — отдельно, если попросите), аутентифицированные медиа MSC3916 (matrix.org пока принимает legacy-эндпоинт — риск задокументирован).

## Файлы

- Правка: `media/MediaFetcher.kt`, `data/model/Models.kt` (+1 поле), `data/repository/MatrixRepositoryImpl.kt`, `ui/chat/{ChatViewModel,ChatScreen}.kt`, `ui/home/HomeScreen.kt` (диалог профиля), `ui/settings/{SettingsScreen,DevicesScreen}.kt`, `ui/search/SearchScreen.kt`, `ui/rooms/{RoomsCatalogScreen,SpaceChildrenScreen}.kt`, `ui/bridges/BridgesScreen.kt`, `ui/login/LoginScreen.kt`
- Переиспользуем: `MatrixAvatar`, `TimeFormat`, палитру/типографику темы из v1.15.0

## Проверка

1. `gradlew :shared:compileKotlinDesktop :desktop-app:compileKotlinDesktop :app:assembleDebug` + `:shared:desktopTest :shared:testDebugUnitTest`
2. `gradlew :desktop-app:run` — визуально: шапка чата с аватаром комнаты, аватары собеседников в ленте, незашифрованная картинка в чате/галерее, профиль в настройках с фото, список чатов с DM-аватарами
3. Вручную у вас: тот же чат, где проверяли отправку — аватары с обеих сторон
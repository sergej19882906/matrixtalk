# v1.19.0 — пакет фич Element (п.1–7 меню)

## Контекст

Волна доверия и стабильность завершены (v1.18.1). По меню фич Element реализуем пакет «быстрых побед»: настройки комнаты со списком участников, per-room уведомления, просмотрщик изображений с зумом, произвольные реакции, markdown-композер, jump to unread. Проверенные API Trixnity 4.9.2: `api.room.sendStateEvent` (Name/Topic/Avatar/PowerLevels/Encryption contents), `UserService.loadMembers/getAll`, `RoomApiClient.kickUser/inviteUser`, `api.push.setPushRuleEnabled(ROOM)`, `Room.unreadMessageCount`, `api.room.setReadMarkers`.

## Реализация

### A. Настройки комнаты + участники (новый экран)

**Репозиторий** (`MatrixRepository.kt` + Impl):
- `getRoomSettingsFlow(roomId): Flow<RoomSettingsInfo>` — name/topic/avatarUrl/encrypted/memberCount из state-флоу (`roomService.getState<NameEventContent/TopicEventContent/AvatarEventContent>`, `Room.encrypted`)
- `getRoomMembersFlow(roomId): Flow<List<RoomMemberInfo>>` — `loadMembers` + `getAll` + power levels (`getPowerLevel(roomId, userId)`), только JOIN
- `setRoomName/Topic/Avatar(roomId, ...)` — `api.room.sendStateEvent` (avatar: uploadBytes → AvatarEventContent(mxc))
- `kickUser(roomId, userId)` — `api.room.kickUser`
- `setRoomMuted(roomId, muted)` / `isRoomMutedFlow(roomId)` — SessionStore + `api.push.setPushRuleEnabled(ROOM, roomId.full, enabled=false/true)` (rule: PushRuleKind.ROOM, ruleId = roomId.full)
- Чистка: мёртвый `respondToVerificationRequest` уже удалён

**Модели** (`Models.kt`): `RoomSettingsInfo(name, topic, avatarUrl, encrypted, memberCount, canEdit)`, `RoomMemberInfo(userId, name, avatarUrl, powerLevel, membership, isSelf)`, `RoomTrust` уже есть

**Навигация**: `Screen.RoomSettings(route = "room/{roomId}/settings")` + AppNavigation; вход: ChatScreen MoreVert → «Настройки комнаты»

**`ui/room/RoomSettingsScreen.kt`** (+ VM): поля имени/темы с «Сохранить», аватар с пикером (шаблон профиля из SettingsScreen), тумблер «Уведомления» (mute), зашифровано да/нет, список участников с PL-бейджами (админ ≥100, модератор ≥50), kick у участников (если `canKickUser`), кнопка «Выйти из комнаты»

### B. Уведомления per-room

- `SessionStore`: `mutedRooms: List<String>?` + read/save; Room-список: бейдж unread скрывается для muted
- ChatViewModel/CallRepository: фильтр — muted-комнаты не поднимают CallState.Incoming popup (звонки в muted комнате — только сообщения о вызове)

### C. Просмотрщик изображений (`ui/components/ImageViewer.kt`)

- Fullscreen-диалог поверх чата: тёмный фон, `HorizontalPager` по списку изображений комнаты, зум/пан (`detectTransformGestures` + `graphicsLayer`), кнопка скачивания (existing `openAttachment`), кнопка закрытия
- Вход: клик по изображению в MessageItem → `ChatViewModel.openImageViewer(startIndex)`; список = медиа-сообщения комнаты
- Список медиа в `ChatUiState.imageMessages: List<Message>` (из существующей фильтрации GalleryPanel)

### D. Произвольные реакции

- `ui/components/ReactionPicker.kt`: Popup-грид ~48 эмодзи (категории: смайлы, жесты, символы) на пузыре сообщения — клик → существующий `sendReaction(eventId, emoji)` (API принимает любую строку ✓)
- Быстрые реакции остаются в меню, пикер — по клику «+» рядом с ними

### E. Markdown-композер

- `ui/chat/MarkdownToHtml.kt`: конвертер `**bold**` → `<strong>`, `*italic*` → `<em>`, `` `code` `` → `<code>`, `~~strike~~` → `<del>`, URL → `<a>` (regex, без внешних зависимостей) + экранирование HTML
- Композер: тумблер-иконка (TextFormat) — включён → текст уходит с `format = "org.matrix.custom.html"` + `formattedBody`
- `sendTextMessage(text, formattedBody: String? = null)` — TextBased.Text(format, formattedBody)
- Тест: `MarkdownToHtmlTest` (bold/italic/code/strike/комбинации/экранирование)

### F. Jump to unread

- `ChatUiState.unreadCount` — из `getRoomInfo` (`Room.unreadMessageCount`)
- ChatScreen: при открытии чата с unread > 0 — вычислить индекс первого непрочитанного в TimelineItem-списке (messages.size - min(unreadCount, size)), прокрутить (`listState.scrollToItem`), вставить разделитель «Новые сообщения» перед первым непрочитанным (TimelineItem-обёртка в buildTimeline)
- Прочитанные отметки уже отправляются (`markRoomAsRead` при открытии) — разделитель живёт до закрытия экрана

## Файлы

- Новые: `ui/room/RoomSettingsScreen.kt`, `ui/room/RoomSettingsViewModel.kt`, `ui/components/ImageViewer.kt`, `ui/components/ReactionPicker.kt`, `ui/chat/MarkdownToHtml.kt`, `commonTest/.../MarkdownToHtmlTest.kt`
- Правка: `Models.kt` (+3 модели), `MatrixRepository.kt`+Impl (room settings/members/kick/mute/self-ver уже частично есть), `ui/navigation/{Screen,AppNavigation}.kt`, `ui/chat/{ChatScreen,ChatViewModel}.kt`, `ui/home/HomeScreen.kt` (mute в списке), `data/repository/SessionStore.kt` (mutedRooms), `ui/settings/SettingsViewModel.kt`
- Переиспользование: `uploadBytes`, `resolveSenderName/Avatar`, `rememberFilePicker`, `TimelineItem`, `MatrixAvatar`, `sendReaction`

## Проверка

1. `gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid :desktop-app:compileKotlinDesktop :app:assembleDebug :shared:desktopTest :shared:testDebugUnitTest`
2. Desktop-run: Настройки комнаты (имя/тема/аватар применяются в Element), участники + kick, mute гасит бейдж, клик по изображению → fullscreen viewer с зумом, реакции из пикера доходят до Element, markdown → отформатированный текст в Element, jump to unread
3. `MarkdownToHtmlTest` — зелёный
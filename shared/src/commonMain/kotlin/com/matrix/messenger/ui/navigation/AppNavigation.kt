package com.matrix.messenger.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.matrix.messenger.ui.settings.AppLockManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.matrix.messenger.data.model.CallState
import com.matrix.messenger.ui.settings.PinLockScreen
import com.matrix.messenger.data.repository.CallRepository
import com.matrix.messenger.ui.bridges.BridgesScreen
import com.matrix.messenger.ui.call.CallScreen
import com.matrix.messenger.ui.chat.ChatScreen
import com.matrix.messenger.ui.home.HomeScreen
import com.matrix.messenger.ui.login.LoginScreen
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    val callRepository: CallRepository = koinInject()
    val callState by callRepository.callState.collectAsState()

    val appLock = AppLockManager
    val lockState by appLock.state.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(appLock) { appLock.start(this) }
    DisposableEffect(lifecycleOwner, appLock) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) appLock.onBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!lockState.initialized) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (lockState.error == null) CircularProgressIndicator()
            else Column {
                Text(lockState.error!!)
                TextButton(onClick = { scope.launch { appLock.initialize() } }, enabled = !lockState.busy) {
                    Text("Повторить")
                }
            }
        }
        return
    }
    if (lockState.enabled && lockState.locked) {
        PinLockScreen(onUnlock = {}, appLock = appLock)
        return
    }

    // Keep signaling alive while locked, but never navigate until the PIN gate opens.
    LaunchedEffect(callState, lockState.initialized, lockState.locked) {
        if (lockState.initialized && !lockState.locked && callState is CallState.Incoming &&
            navController.currentDestination?.route != Screen.Call.route
        ) navController.navigate(Screen.Call.route)
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            TwoPaneHome(navController)
        }

        composable(Screen.Bridges.route) {
            BridgesScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            ChatScreen(
                roomId = roomId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onStartCall = {
                    if (navController.currentDestination?.route != Screen.Call.route) {
                        navController.navigate(Screen.Call.route)
                    }
                },
                onOpenRoomSettings = { navController.navigate(Screen.RoomSettings.createRoute(roomId)) },
                onOpenThread = { rootId -> navController.navigate(Screen.Thread.createRoute(roomId, rootId)) },
                onStartGroupCall = { navController.navigate(Screen.CallWidget.createRoute(roomId)) }
            )
        }

        composable(
            route = Screen.Thread.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("rootId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val rootId = backStackEntry.arguments?.getString("rootId") ?: return@composable
            com.matrix.messenger.ui.chat.ThreadScreen(
                roomId = roomId,
                rootId = rootId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Spaces.route) {
            com.matrix.messenger.ui.spaces.SpacesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSpace = { spaceId, _ ->
                    navController.navigate(Screen.SpaceDetail.createRoute(spaceId))
                }
            )
        }

        composable(
            route = Screen.SpaceDetail.route,
            arguments = listOf(
                navArgument("spaceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val spaceId = backStackEntry.arguments?.getString("spaceId") ?: return@composable
            com.matrix.messenger.ui.spaces.SpaceDetailScreen(
                spaceId = spaceId,
                onNavigateBack = { navController.popBackStack() },
                onOpenRoom = { roomId -> navController.navigate(Screen.Chat.createRoute(roomId)) },
                onOpenNestedSpace = { nestedId, _ ->
                    navController.navigate(Screen.SpaceDetail.createRoute(nestedId))
                }
            )
        }

        composable(
            route = Screen.RoomSettings.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            com.matrix.messenger.ui.room.RoomSettingsScreen(
                roomId = roomId,
                onNavigateBack = { navController.popBackStack() },
                onLeftRoom = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onOpenMedia = { navController.navigate(Screen.RoomMedia.createRoute(roomId)) }
            )
        }

        composable(
            route = Screen.RoomMedia.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            com.matrix.messenger.ui.room.RoomMediaScreen(
                roomId = roomId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CallWidget.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            var session by remember(roomId) {
                mutableStateOf<com.matrix.messenger.data.repository.SessionStore.Session?>(null)
            }
            var sessionLoaded by remember(roomId) { mutableStateOf(false) }
            var sessionError by remember(roomId) { mutableStateOf<String?>(null) }
            LaunchedEffect(roomId) {
                try { session = com.matrix.messenger.data.repository.SessionStore.read() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { sessionError = "Не удалось загрузить данные сессии" }
                finally { sessionLoaded = true }
            }
            if (!sessionLoaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@composable
            }
            if (session == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column {
                        Text(sessionError ?: "Для звонка необходимо войти в аккаунт")
                        TextButton(onClick = { navController.popBackStack() }) { Text("Назад") }
                    }
                }
                return@composable
            }
            val url = com.matrix.messenger.ui.call.elementCallUrl(
                roomId,
                session?.homeServer ?: ""
            )
            com.matrix.messenger.ui.call.ElementCallScreen(
                url = url,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Call.route) {
            CallScreen(
                onCallEnded = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            com.matrix.messenger.ui.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenDevices = { navController.navigate(Screen.Devices.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Devices.route) {
            com.matrix.messenger.ui.settings.DevicesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            com.matrix.messenger.ui.search.SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenRoom = { roomId ->
                    navController.navigate(Screen.Chat.createRoute(roomId))
                }
            )
        }

        composable(Screen.RoomsCatalog.route) {
            com.matrix.messenger.ui.rooms.RoomsCatalogScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenRoom = { roomId ->
                    navController.navigate(Screen.Chat.createRoute(roomId))
                }
            )
        }

        composable(
            route = Screen.SpaceChildren.route,
            arguments = listOf(
                navArgument("spaceId") { type = NavType.StringType },
                navArgument("spaceName") { type = NavType.StringType }
            )
        ) { entry ->
            val spaceId = entry.arguments?.getString("spaceId") ?: return@composable
            val spaceName = entry.arguments?.getString("spaceName") ?: ""
            com.matrix.messenger.ui.rooms.SpaceChildrenScreen(
                spaceId = spaceId,
                spaceName = spaceName,
                onNavigateBack = { navController.popBackStack() },
                onOpenRoom = { roomId ->
                    navController.navigate(Screen.Chat.createRoute(roomId))
                }
            )
        }
    }
}

/**
 * Element-style adaptive layout: on wide windows the chat list stays on the left
 * and the selected room opens on the right; narrow windows keep single-pane
 * push navigation. The same ChatScreen instance is reused in the right pane —
 * ChatScreen switches rooms itself via setRoomId.
 */
@Composable
private fun TwoPaneHome(navController: NavHostController) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoPaneMinWidth
        var selectedRoomId by androidx.compose.runtime.saveable.rememberSaveable {
            mutableStateOf<String?>(null)
        }

        if (!wide) {
            HomeScreen(
                onRoomClick = { roomId ->
                    navController.navigate(Screen.Chat.createRoute(roomId))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onOpenSpaces = { navController.navigate(Screen.Spaces.route) },
                onBridges = { navController.navigate(Screen.Bridges.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onSearch = { navController.navigate(Screen.Search.route) },
                onRoomsCatalog = { navController.navigate(Screen.RoomsCatalog.route) },
                onOpenSpace = { spaceId ->
                    navController.navigate(Screen.SpaceChildren.createRoute(spaceId, ""))
                }
            )
            return@BoxWithConstraints
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Draggable split: the saved width is clamped on every relayout so
            // resizing the window can never squeeze either pane away.
            var savedListWidth by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(TwoPaneListWidth.value)
            }
            val minListWidth = 280.dp
            val minChatWidth = 360.dp
            val maxListWidth = (this@BoxWithConstraints.maxWidth - minChatWidth).coerceAtLeast(minListWidth)
            val listWidth = savedListWidth.dp.coerceIn(minListWidth, maxListWidth)
            Box(modifier = Modifier.width(listWidth)) {
                HomeScreen(
                    onRoomClick = { roomId -> selectedRoomId = roomId },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onOpenSpaces = { navController.navigate(Screen.Spaces.route) },
                    onBridges = { navController.navigate(Screen.Bridges.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                    onSearch = { navController.navigate(Screen.Search.route) },
                    onRoomsCatalog = { navController.navigate(Screen.RoomsCatalog.route) },
                    onOpenSpace = { spaceId ->
                        navController.navigate(Screen.SpaceChildren.createRoute(spaceId, ""))
                    }
                )
            }
            TwoPaneDragHandle(
                onDrag = { delta ->
                    savedListWidth = (savedListWidth + delta).coerceIn(minListWidth.value, maxListWidth.value)
                }
            )
            Box(modifier = Modifier.weight(1f)) {
                val roomId = selectedRoomId
                if (roomId == null) {
                    TwoPanePlaceholder()
                } else {
                    key(roomId) {
                        ChatScreen(
                            roomId = roomId,
                            onNavigateBack = { selectedRoomId = null },
                            onStartCall = {
                                if (navController.currentDestination?.route != Screen.Call.route) {
                                    navController.navigate(Screen.Call.route)
                                }
                            },
                            onOpenRoomSettings = { navController.navigate(Screen.RoomSettings.createRoute(roomId)) },
                            onOpenThread = { rootId -> navController.navigate(Screen.Thread.createRoute(roomId, rootId)) },
                            onStartGroupCall = { navController.navigate(Screen.CallWidget.createRoute(roomId)) }
                        )
                    }
                }
            }
        }
    }
}

/** Draggable split divider: drag left/right to resize the chat-list pane. */
@Composable
private fun TwoPaneDragHandle(onDrag: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(8.dp)
            .fillMaxHeight()
            .background(if (dragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun TwoPanePlaceholder() {    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Выберите чат слева",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val TwoPaneMinWidth = 900.dp
private val TwoPaneListWidth = 360.dp

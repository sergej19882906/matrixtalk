package com.matrix.messenger.ui.navigation

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

    // Incoming call anywhere in the app -> open the call screen.
    LaunchedEffect(callState) {
        if (callState is CallState.Incoming &&
            navController.currentDestination?.route != Screen.Call.route
        ) {
            navController.navigate(Screen.Call.route)
        }
    }

    // App lock: PIN gate before anything else when configured.
    val pinHash = remember { kotlinx.coroutines.runBlocking { com.matrix.messenger.data.repository.SessionStore.readPinHash() } }
    var pinUnlocked by remember { mutableStateOf(pinHash == null) }
    if (!pinUnlocked) {
        PinLockScreen(
            pinHash = pinHash!!,
            onUnlock = { pinUnlocked = true }
        )
        return
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
            HomeScreen(
                onRoomClick = { roomId ->
                    navController.navigate(Screen.Chat.createRoute(roomId))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onOpenSpaces = {
                    navController.navigate(Screen.Spaces.route)
                },
                onBridges = {
                    navController.navigate(Screen.Bridges.route)
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onRoomsCatalog = {
                    navController.navigate(Screen.RoomsCatalog.route)
                },
                onOpenSpace = { spaceId ->
                    navController.navigate(Screen.SpaceChildren.createRoute(spaceId, ""))
                }
            )
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
                onOpenThread = { rootId -> navController.navigate(Screen.Thread.createRoute(roomId, rootId)) }
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
                }
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

package com.matrix.messenger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.matrix.messenger.data.model.CallState
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
                onCreateChat = {
                    // TODO: Показать диалог создания чата
                },
                onBridges = {
                    navController.navigate(Screen.Bridges.route)
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
                }
            )
        }

        composable(Screen.Call.route) {
            CallScreen(
                onCallEnded = { navController.popBackStack() }
            )
        }
    }
}

package com.matrix.messenger.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Bridges : Screen("bridges")
    object Chat : Screen("chat/{roomId}") {
        fun createRoute(roomId: String): String {
            return "chat/$roomId"
        }
    }
}

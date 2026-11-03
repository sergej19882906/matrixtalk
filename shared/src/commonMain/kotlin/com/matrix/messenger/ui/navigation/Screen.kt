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
    object Call : Screen("call")
    object Settings : Screen("settings")
    object Devices : Screen("devices")
    object Search : Screen("search")
    object RoomsCatalog : Screen("rooms_catalog")
    object Thread : Screen("chat/{roomId}/thread/{rootId}") {
        fun createRoute(roomId: String, rootId: String): String {
            return "chat/$roomId/thread/$rootId"
        }
    }
    object RoomSettings : Screen("room/{roomId}/settings") {
        fun createRoute(roomId: String): String {
            return "room/$roomId/settings"
        }
    }
    object SpaceChildren : Screen("space/{spaceId}/{spaceName}") {
        fun createRoute(spaceId: String, spaceName: String): String {
            return "space/$spaceId/$spaceName"
        }
    }
}

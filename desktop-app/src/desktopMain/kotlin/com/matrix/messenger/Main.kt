package com.matrix.messenger

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.rememberNavController
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.di.sharedModule
import com.matrix.messenger.ui.navigation.AppNavigation
import com.matrix.messenger.ui.navigation.Screen
import com.matrix.messenger.ui.theme.MatrixMessengerTheme
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(sharedModule)
    }

    val matrixRepository: MatrixRepository = koinInject()
    var isReady by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf(Screen.Login.route) }

    LaunchedEffect(Unit) {
        matrixRepository.initialize()
        if (matrixRepository.currentUser.first() != null) {
            startDestination = Screen.Home.route
        }
        isReady = true
    }

    if (isReady) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MatrixTalk",
            state = rememberWindowState()
        ) {
            MatrixMessengerTheme {
                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
